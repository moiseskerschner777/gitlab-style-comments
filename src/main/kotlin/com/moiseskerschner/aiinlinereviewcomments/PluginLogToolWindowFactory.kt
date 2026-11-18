package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel
import java.awt.BorderLayout
import javax.swing.JButton
import java.awt.FlowLayout

class PluginLogToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val textArea = JBTextArea()
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.font = textArea.font.deriveFont(12f)

        fun render(entries: List<PluginLog.Entry>) {
            textArea.text = entries.joinToString("\n") { "${it.time} [${it.level}] ${it.tag} — ${it.message}" }
            textArea.caretPosition = textArea.document.length
        }

        render(PluginLog.snapshot())

        val listener: (PluginLog.Entry) -> Unit = { entry ->
            ApplicationManager.getApplication().invokeLater {
                textArea.append("${entry.time} [${entry.level}] ${entry.tag} — ${entry.message}\n")
                textArea.caretPosition = textArea.document.length
            }
        }
        PluginLog.addListener(listener)

        val clearButton = JButton("Clear")
        clearButton.addActionListener {
            PluginLog.clear()
            textArea.text = ""
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.add(clearButton)

        val root = JPanel(BorderLayout())
        root.add(toolbar, BorderLayout.NORTH)
        root.add(JBScrollPane(textArea), BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(root, "", false)
        Disposer.register(content) { PluginLog.removeListener(listener) }
        toolWindow.contentManager.addContent(content)
    }
}
