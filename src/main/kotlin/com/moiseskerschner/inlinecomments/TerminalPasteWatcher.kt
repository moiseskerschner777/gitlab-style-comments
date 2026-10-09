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

object TerminalPasteWatcher {
    private val logger = Logger.getInstance(TerminalPasteWatcher::class.java)

    private const val POLL_INTERVAL_MS = 500L
    private const val MAX_ATTEMPTS = 3600
    private const val PASTE_PLACEHOLDER_MARKER = "Pasted text"

    private enum class Phase { AWAITING_PASTE, AWAITING_SUBMIT }

    private var lastPayload: String? = null
    private var onSubmitted: (() -> Unit)? = null
    private var pollTask: ScheduledFuture<*>? = null
    private var attemptsLeft = 0

    private var phase = Phase.AWAITING_PASTE
    private var matchedModel: WeakReference<TerminalOutputModel>? = null
    private var matchedText: String? = null
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
        matchedText = null
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
        var foundText: String? = null
        val model = EditorFactory.getInstance().allEditors.firstNotNullOfOrNull { editor ->
            try {
                val candidate = editor.getUserData(TerminalOutputModel.KEY) ?: return@firstNotNullOfOrNull null
                val rawBaseline = baselines[editor] ?: candidate.startOffset
                val safeBaseline = when {
                    rawBaseline.compareTo(candidate.startOffset) < 0 -> candidate.startOffset
                    rawBaseline.compareTo(candidate.endOffset) > 0 -> candidate.startOffset
                    else -> rawBaseline
                }
                val delta = candidate.getText(safeBaseline, candidate.endOffset)
                when {
                    delta.contains(payload) -> {
                        foundText = payload
                        candidate
                    }
                    delta.contains(PASTE_PLACEHOLDER_MARKER, ignoreCase = true) -> {
                        foundText = PASTE_PLACEHOLDER_MARKER
                        candidate
                    }
                    else -> null
                }
            } catch (t: Throwable) {
                logger.warn("TerminalPasteWatcher: failed to read TerminalOutputModel for one editor, skipping it", t)
                null
            }
        }

        if (model != null) {
            logger.info("TerminalPasteWatcher: payload pasted, waiting for it to be submitted (Enter)")
            matchedModel = WeakReference(model)
            matchedText = foundText
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
            return
        }

        // Full-screen TUIs (e.g. Claude Code's CLI) redraw their input box in place instead of
        // linearly scrolling to a new line on submit, so the cursor-line check above never fires
        // for them (confirmed via idea.log: "payload pasted" logged, but no "Enter detected"
        // ever followed, despite the user confirming they did submit). As a second, independent
        // signal: once submitted, the pasted text/placeholder that was sitting in the input
        // disappears (cleared, replaced by a spinner, etc.) — treat that as submission too.
        val text = matchedText
        if (text != null) {
            val stillPresent = try {
                model.getText(model.startOffset, model.endOffset).contains(text, ignoreCase = true)
            } catch (t: Throwable) {
                logger.warn("TerminalPasteWatcher: failed to re-check pasted text presence", t)
                true
            }
            if (!stillPresent) {
                logger.info("TerminalPasteWatcher: pasted text disappeared from input (submitted), firing onSubmitted")
                fireAndStop()
            }
        }
    }

    private fun fireAndStop() {
        val callback = onSubmitted
        stopPolling()
        callback?.invoke()
    }
}
