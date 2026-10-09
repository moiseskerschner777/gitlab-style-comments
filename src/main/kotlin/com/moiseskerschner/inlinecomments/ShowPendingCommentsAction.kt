package com.moiseskerschner.inlinecomments

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.Dimension

/**
 * Ctrl+Alt+X then S: shows every comment currently batched (added since the last paste+submit
 * into a terminal, across all files — see CommentBatch) in a scrollable, read-only popup, so it
 * can be read over carefully before pasting instead of only trusting the clipboard blindly.
 */
class ShowPendingCommentsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val content = CommentBatch.format(project)
        val displayText = content.ifBlank { "No pending comments." }

        val textArea = JBTextArea(displayText)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.caretPosition = 0
        textArea.border = JBUI.Borders.empty(8)

        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(640, 480)

        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, textArea)
            .setTitle("Pending Inline Comments")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }
}
