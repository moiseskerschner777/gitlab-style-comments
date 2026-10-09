package com.moiseskerschner.inlinecomments

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

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

        val root = JPanel(BorderLayout())
        root.add(scrollPane, BorderLayout.CENTER)

        val footer = JPanel(FlowLayout(FlowLayout.RIGHT))
        val clearButton = JButton("I already pasted this — clear now")
        clearButton.isEnabled = content.isNotBlank()
        footer.add(clearButton)
        root.add(footer, BorderLayout.SOUTH)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(root, textArea)
            .setTitle("Pending Inline Comments")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            .createPopup()

        clearButton.addActionListener {
            CommentBatch.clearAll()
            popup.cancel()
        }

        popup.showCenteredInCurrentWindow(project)
    }
}
