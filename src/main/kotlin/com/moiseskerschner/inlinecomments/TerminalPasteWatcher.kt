package com.moiseskerschner.inlinecomments

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.terminal.TerminalView
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * After "Add Inline Comment" copies the formatted comments to the clipboard, this polls every
 * open terminal's visible text for that exact payload to appear, then clears the comments it
 * came from — a one-shot "copy into the Claude Code terminal, then auto-clear" flow.
 *
 * Reads two sources, since neither alone covers every terminal engine:
 *  - TerminalOutputModel (org.jetbrains.plugins.terminal.view), stored as Editor user-data —
 *    this is what IntelliJ's reworked/block terminal engine (INTELLIJ_TERMINAL_COMMAND_BLOCKS_
 *    REWORKED=1, confirmed active on this IDE build) actually backs its output panes with.
 *  - JBTerminalWidget#getText() via the classic TerminalView API, as a fallback for the older
 *    JediTerm-based engine, where TerminalOutputModel won't be present.
 *
 * The first attempt here only polled JBTerminalWidget#getText(): it silently never matched,
 * because that's a bridge over the classic text buffer, which the reworked engine doesn't
 * populate the same way (confirmed via idea.log: polling ran every cycle, logged no error, but
 * never found the payload).
 *
 * This does NOT hook keyboard/paste events (a global AWTEventListener for Ctrl+V never fired
 * on this terminal engine either — confirmed via idea.log, zero events reached it). Polling
 * actual rendered content works regardless of how the paste happened (keyboard, mouse, menu).
 */
object TerminalPasteWatcher {
    private val logger = Logger.getInstance(TerminalPasteWatcher::class.java)

    private const val POLL_INTERVAL_MS = 500L
    private const val MAX_ATTEMPTS = 240 // give up after ~2 minutes

    private var lastPayload: String? = null
    private var lastDocument: WeakReference<Document>? = null
    private var pollTask: ScheduledFuture<*>? = null
    private var attemptsLeft = 0

    fun recordCopy(document: Document, payload: String, project: Project) {
        lastPayload = payload
        lastDocument = WeakReference(document)
        attemptsLeft = MAX_ATTEMPTS
        logger.info("TerminalPasteWatcher: recorded copy (${payload.length} chars), polling terminals")

        pollTask?.cancel(false)
        pollTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { poll(project) },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopPolling() {
        pollTask?.cancel(false)
        pollTask = null
        lastPayload = null
        lastDocument = null
    }

    private fun poll(project: Project) {
        val payload = lastPayload
        val document = lastDocument?.get()
        if (payload == null || document == null) {
            stopPolling()
            return
        }
        if (attemptsLeft-- <= 0) {
            logger.info("TerminalPasteWatcher: gave up waiting for paste")
            stopPolling()
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                stopPolling()
                return@invokeLater
            }

            val foundViaOutputModel = try {
                EditorFactory.getInstance().allEditors.any { editor ->
                    val model = editor.getUserData(TerminalOutputModel.KEY) ?: return@any false
                    model.getText(model.startOffset, model.endOffset).contains(payload)
                }
            } catch (t: Throwable) {
                logger.warn("TerminalPasteWatcher: failed to read TerminalOutputModel", t)
                false
            }

            val foundViaClassicWidget = if (foundViaOutputModel) {
                false
            } else {
                try {
                    TerminalView.getInstance(project).getWidgets().any { widget ->
                        widget.text?.contains(payload) == true
                    }
                } catch (t: Throwable) {
                    logger.warn("TerminalPasteWatcher: failed to read terminal widgets", t)
                    false
                }
            }

            if (foundViaOutputModel || foundViaClassicWidget) {
                logger.info("TerminalPasteWatcher: payload found in a terminal, clearing comments")
                CommentManager.getInstance(document).clearAll()
                EditorFactory.getInstance().getEditors(document).forEach { GutterIconsRefresher.refresh(it) }
                stopPolling()
            }
        }
    }
}
