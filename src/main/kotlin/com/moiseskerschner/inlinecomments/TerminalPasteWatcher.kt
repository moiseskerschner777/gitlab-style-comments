package com.moiseskerschner.inlinecomments

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.terminal.TerminalView
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * After "Add Inline Comment" copies the formatted comment batch to the clipboard, this waits for
 * that payload to be PASTED AND THEN SUBMITTED (Enter pressed) in a terminal, then invokes the
 * caller's onSubmitted callback — a one-shot "copy into the Claude Code terminal, then
 * auto-clear" flow. Doesn't know or care about CommentManager/documents itself; the caller
 * (AddInlineCommentAction, via CommentBatch) decides what "submitted" means to clear.
 *
 * Two phases, both driven by polling the same TerminalOutputModel (org.jetbrains.plugins.
 * terminal.view, stored as Editor user-data — what the reworked/block terminal engine backs its
 * output panes with; JBTerminalWidget#getText() is a dead end here, see below):
 *
 *  1. AWAITING_PASTE — for every terminal, look only at the text appended since recordCopy was
 *     called (a per-editor baseline endOffset snapshotted at that moment — never scan the whole
 *     scrollback, or old history containing a stale payload/marker from a previous round would
 *     match instantly). A match is either the literal payload substring, OR (since Claude Code's
 *     own terminal UI collapses a large/multi-line paste into a "[Pasted text #1 +N lines]"
 *     placeholder instead of showing the raw text — confirmed via idea.log: a short single-
 *     comment payload matched literally and completed the full cycle, but every payload that
 *     grew past a few hundred chars/multiple files via CommentBatch never matched again) the
 *     placeholder marker appearing in that same delta.
 *  2. AWAITING_SUBMIT — keep polling that same model's cursor line. Pressing the real Enter key
 *     submits the command, and the terminal advances the cursor to a new line below (a fresh
 *     prompt). Once the cursor's line index is greater than pasteLine, Enter has actually been
 *     pressed — only then do we fire onSubmitted.
 *
 * recordCopy only ever tracks ONE in-flight payload (a new call replaces whatever was pending),
 * so every "Add Comment" re-copies the FULL accumulated batch (see CommentBatch) rather than
 * just the latest file's comments, otherwise adding a comment in file B while file A's payload
 * was still pending would silently drop file A from the clipboard.
 *
 * Earlier attempts:
 *  - A global AWTEventListener for Ctrl+V never fired on this terminal engine (confirmed via
 *    idea.log, zero events reached it) — not a viable way to detect paste OR enter here.
 *  - Clearing as soon as the payload text appeared (no AWAITING_SUBMIT phase) fired on paste
 *    alone, before Enter — the comments disappeared before the content was actually submitted.
 *  - Polling JBTerminalWidget#getText() (classic TerminalView API) never matched on the reworked
 *    engine — that bridge doesn't populate the same way. Kept only as a same-cycle fallback for
 *    IDEs still on the classic JediTerm engine, where it fires onSubmitted immediately (no
 *    submit-detection available there, but it's not the engine in use here).
 *  - Literal-substring-only matching against the WHOLE model text broke once CommentBatch made
 *    payloads large enough to trigger Claude Code's own paste-placeholder UI (see above).
 */
object TerminalPasteWatcher {
    private val logger = Logger.getInstance(TerminalPasteWatcher::class.java)

    private const val POLL_INTERVAL_MS = 500L
    // 2 minutes was too short for the real workflow — batching several comments across files
    // before finally pasting easily takes longer, and once this expires there was no way to
    // retry short of adding another comment (confirmed via idea.log: "gave up waiting" with no
    // error, just a timeout). 30 minutes per phase, plus ShowPendingCommentsAction now has a
    // manual "I already pasted this" fallback for anything longer.
    private const val MAX_ATTEMPTS = 3600 // give up after ~30 minutes per phase
    private const val PASTE_PLACEHOLDER_MARKER = "Pasted text"

    private enum class Phase { AWAITING_PASTE, AWAITING_SUBMIT }

    private var lastPayload: String? = null
    private var onSubmitted: (() -> Unit)? = null
    private var pollTask: ScheduledFuture<*>? = null
    private var attemptsLeft = 0

    private var phase = Phase.AWAITING_PASTE
    private var matchedModel: WeakReference<TerminalOutputModel>? = null
    private var pasteLine: TerminalLineIndex? = null
    private var baselines: Map<Editor, TerminalOffset> = emptyMap()

    fun recordCopy(payload: String, project: Project, onSubmitted: () -> Unit) {
        lastPayload = payload
        this.onSubmitted = onSubmitted
        attemptsLeft = MAX_ATTEMPTS
        phase = Phase.AWAITING_PASTE
        matchedModel = null
        pasteLine = null
        baselines = snapshotBaselines()
        logger.info("TerminalPasteWatcher: recorded copy (${payload.length} chars), polling terminals")

        pollTask?.cancel(false)
        pollTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { poll(project) },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun snapshotBaselines(): Map<Editor, TerminalOffset> {
        return try {
            EditorFactory.getInstance().allEditors.mapNotNull { editor ->
                val model = editor.getUserData(TerminalOutputModel.KEY) ?: return@mapNotNull null
                editor to model.endOffset
            }.toMap()
        } catch (t: Throwable) {
            logger.warn("TerminalPasteWatcher: failed to snapshot terminal baselines", t)
            emptyMap()
        }
    }

    private fun stopPolling() {
        pollTask?.cancel(false)
        pollTask = null
        lastPayload = null
        onSubmitted = null
        matchedModel = null
        pasteLine = null
        baselines = emptyMap()
    }

    private fun poll(project: Project) {
        val payload = lastPayload
        if (payload == null || onSubmitted == null) {
            stopPolling()
            return
        }
        if (attemptsLeft-- <= 0) {
            logger.info("TerminalPasteWatcher: gave up waiting (phase=$phase)")
            stopPolling()
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                stopPolling()
                return@invokeLater
            }

            when (phase) {
                Phase.AWAITING_PASTE -> pollAwaitingPaste(project, payload)
                Phase.AWAITING_SUBMIT -> pollAwaitingSubmit()
            }
        }
    }

    private fun pollAwaitingPaste(project: Project, payload: String) {
        val model = EditorFactory.getInstance().allEditors.firstNotNullOfOrNull { editor ->
            try {
                val candidate = editor.getUserData(TerminalOutputModel.KEY) ?: return@firstNotNullOfOrNull null
                // The buffer can shrink or reset underneath us (scrollback eviction, alt-screen
                // swap, clear) between polls, leaving a stale baseline outside the model's
                // current valid range — clamp instead of letting getText throw and abort the
                // whole scan (confirmed via idea.log: "chars sequence.length:X, start:Y" where
                // Y > X, repeating every cycle and starving other editors of a chance to match).
                val rawBaseline = baselines[editor] ?: candidate.startOffset
                val safeBaseline = when {
                    rawBaseline.compareTo(candidate.startOffset) < 0 -> candidate.startOffset
                    rawBaseline.compareTo(candidate.endOffset) > 0 -> candidate.startOffset
                    else -> rawBaseline
                }
                val delta = candidate.getText(safeBaseline, candidate.endOffset)
                val matched = delta.contains(payload) || delta.contains(PASTE_PLACEHOLDER_MARKER, ignoreCase = true)
                if (matched) candidate else null
            } catch (t: Throwable) {
                logger.warn("TerminalPasteWatcher: failed to read TerminalOutputModel for one editor, skipping it", t)
                null
            }
        }

        if (model != null) {
            logger.info("TerminalPasteWatcher: payload pasted, waiting for it to be submitted (Enter)")
            matchedModel = WeakReference(model)
            pasteLine = model.getLineByOffset(model.cursorOffset)
            phase = Phase.AWAITING_SUBMIT
            attemptsLeft = MAX_ATTEMPTS
            return
        }

        val foundViaClassicWidget = try {
            TerminalView.getInstance(project).getWidgets().any { widget ->
                widget.text?.contains(payload) == true
            }
        } catch (t: Throwable) {
            logger.warn("TerminalPasteWatcher: failed to read terminal widgets", t)
            false
        }

        if (foundViaClassicWidget) {
            logger.info("TerminalPasteWatcher: payload found via classic terminal widget, firing onSubmitted (no submit-detection on this engine)")
            fireAndStop()
        }
    }

    private fun pollAwaitingSubmit() {
        val model = matchedModel?.get()
        val linePasted = pasteLine
        if (model == null || linePasted == null) {
            logger.info("TerminalPasteWatcher: lost terminal model while awaiting submit, giving up")
            stopPolling()
            return
        }

        val currentLine = try {
            model.getLineByOffset(model.cursorOffset)
        } catch (t: Throwable) {
            logger.warn("TerminalPasteWatcher: failed to read cursor position", t)
            return
        }

        if (currentLine.compareTo(linePasted) > 0) {
            logger.info("TerminalPasteWatcher: Enter detected (cursor advanced past pasted line), firing onSubmitted")
            fireAndStop()
        }
    }

    private fun fireAndStop() {
        val callback = onSubmitted
        stopPolling()
        callback?.invoke()
    }
}
