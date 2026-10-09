package com.moiseskerschner.inlinecomments

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class CommentBarPanel(
    count: Int,
    displayPath: String,
    fileName: String,
    comments: List<InlineComment>,
    project: Project,
    onRemoveComment: (String) -> Unit,
    onClearAll: () -> Unit
) : JPanel() {
    init {
        val text = if (count == 1) "1 comment stacked" else "$count comments stacked"
        val label = JLabel(text)
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showPreview(displayPath, fileName, comments, project, onRemoveComment)
            }
        })
        add(label)
        val copyBtn = JButton("Copy")
        copyBtn.addActionListener {
            val formatted = CommentFormatter.format(displayPath, fileName, comments)
            CopyPasteManager.getInstance().setContents(StringSelection(formatted))
        }
        add(copyBtn)
        val clearBtn = JButton(AllIcons.Actions.Close)
        clearBtn.preferredSize = Dimension(24, 24)
        clearBtn.foreground = Color(0xFF, 0x9E, 0x64)
        clearBtn.margin = java.awt.Insets(0, 0, 0, 0)
        clearBtn.isBorderPainted = false
        clearBtn.isContentAreaFilled = false
        clearBtn.addActionListener { onClearAll() }
        add(clearBtn)
    }

    private fun showPreview(
        displayPath: String,
        fileName: String,
        comments: List<InlineComment>,
        project: Project,
        onRemoveComment: (String) -> Unit
    ) {
        var popupRef: JBPopup? = null

        val content = buildContent(displayPath, fileName, comments) { id ->
            onRemoveComment(id)
            popupRef?.cancel()
            val remaining = comments.filter { it.id != id }
            if (remaining.isNotEmpty()) {
                showPreview(displayPath, fileName, remaining, project, onRemoveComment)
            }
        }

        val scrollPane = JScrollPane(content)
        scrollPane.preferredSize = Dimension(550, 200)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, content)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .createPopup()
        popupRef = popup
        popup.showCenteredInCurrentWindow(project)
    }

    private fun buildContent(
        displayPath: String,
        fileName: String,
        comments: List<InlineComment>,
        onRemove: (String) -> Unit
    ): JPanel {
        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.border = JBUI.Borders.empty(10, 12, 10, 12)

        val header = JLabel("$displayPath: $fileName")
        header.foreground = Color(0xF5, 0xF7, 0xFA)
        header.font = Font("Monospaced", Font.PLAIN, 11)
        header.alignmentX = Component.LEFT_ALIGNMENT
        content.add(header)

        for (comment in comments) {
            val lineRow = JPanel()
            lineRow.layout = BoxLayout(lineRow, BoxLayout.X_AXIS)
            lineRow.alignmentX = Component.LEFT_ALIGNMENT

            val lineLabel = JLabel("line ${comment.lineNumber}: ")
            lineLabel.foreground = Color(0xFF, 0x9E, 0x64)
            lineLabel.font = Font("Monospaced", Font.PLAIN, 11)
            lineRow.add(lineLabel)

            val snippetLabel = JLabel("\"${comment.snippet}\"")
            snippetLabel.foreground = Color(0x7D, 0xCF, 0xFF)
            snippetLabel.font = Font("Monospaced", Font.PLAIN, 11)
            lineRow.add(snippetLabel)

            lineRow.add(Box.createHorizontalGlue())

            val removeBtn = JButton(AllIcons.Actions.Close)
            removeBtn.preferredSize = Dimension(16, 16)
            removeBtn.minimumSize = Dimension(16, 16)
            removeBtn.maximumSize = Dimension(16, 16)
            removeBtn.foreground = Color(0xFF, 0x9E, 0x64)
            removeBtn.margin = java.awt.Insets(0, 0, 0, 0)
            removeBtn.isBorderPainted = false
            removeBtn.isContentAreaFilled = false
            removeBtn.addActionListener { onRemove(comment.id) }
            lineRow.add(removeBtn)
            content.add(lineRow)

            val requestLabel = JLabel("  > ${comment.request}")
            requestLabel.foreground = Color(0x9E, 0xCE, 0x6A)
            requestLabel.font = Font("Monospaced", Font.PLAIN, 11)
            requestLabel.alignmentX = Component.LEFT_ALIGNMENT
            content.add(requestLabel)
        }

        return content
    }
}
