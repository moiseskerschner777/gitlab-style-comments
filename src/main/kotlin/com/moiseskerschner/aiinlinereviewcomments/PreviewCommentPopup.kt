package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.EditorNotifications
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

object PreviewCommentPopup {

    private const val MAX_SNIPPET = 90
    private const val STACK_HEIGHT = 170

    private var current: JBPopup? = null

    fun isShowing(): Boolean = current?.isVisible == true

    fun show(project: Project, anchor: JComponent, point: Point?, onSubmit: (String) -> Unit) {
        lateinit var popup: JBPopup
        val parts = InlineCommentWidget.buildCard("", "Add Comment", onSubmit, null) { popup.cancel() }

        val root = JPanel(BorderLayout())
        root.isOpaque = false
        root.add(parts.card, BorderLayout.CENTER)

        if (CommentBatch.count() > 0) {
            root.add(buildStackSection(project, { popup.cancel() }) { popup.pack(true, true) }, BorderLayout.SOUTH)
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(root, parts.textArea)
            .setRequestFocus(true)
            .setShowBorder(false)
            .setCancelOnClickOutside(true)
            .createPopup()
        current = popup

        if (point != null) {
            popup.show(RelativePoint(anchor, Point(point.x, point.y + JBUI.scale(8))))
        } else {
            popup.showInCenterOf(anchor)
        }
    }

    private fun buildStackSection(project: Project, close: () -> Unit, repack: () -> Unit): JPanel {
        val section = JPanel(BorderLayout())
        section.isOpaque = false
        section.border = JBUI.Borders.empty(6, 4, 0, 4)

        val header = JPanel(BorderLayout())
        header.isOpaque = false
        val label = JBLabel()
        label.font = JBFont.small()
        label.foreground = UIUtil.getContextHelpForeground()
        val clear = JButton("Clear all")
        clear.isFocusPainted = false
        header.add(label, BorderLayout.WEST)
        header.add(clear, BorderLayout.EAST)

        val list = JPanel()
        list.layout = BoxLayout(list, BoxLayout.Y_AXIS)
        list.isOpaque = false
        val scroll = JBScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
        scroll.border = JBUI.Borders.empty(4, 0, 0, 0)
        scroll.isOpaque = false
        scroll.viewport.isOpaque = false

        fun refreshBars() = EditorNotifications.getInstance(project).updateAllNotifications()

        lateinit var render: () -> Unit
        render = {
            list.removeAll()
            val items = CommentBatch.items()
            label.text = if (items.size == 1) "1 comment stacked" else "${items.size} comments stacked"
            for (item in items) {
                list.add(buildRow(item) {
                    CommentBatch.remove(item)
                    refreshBars()
                    if (CommentBatch.count() == 0) {
                        TerminalPasteWatcher.cancel()
                        close()
                    } else {
                        render()
                    }
                })
            }
            val rowHeight = JBUI.scale(44) * items.size
            scroll.preferredSize = Dimension(JBUI.scale(560), minOf(rowHeight, JBUI.scale(STACK_HEIGHT)))
            list.revalidate()
            list.repaint()
            repack()
        }

        clear.addActionListener {
            val total = CommentBatch.count()
            TerminalPasteWatcher.cancel()
            CommentBatch.clearAll()
            refreshBars()
            PluginLog.info("PreviewCommentPopup", "cleared $total stacked comment(s)")
            close()
        }

        section.add(header, BorderLayout.NORTH)
        section.add(scroll, BorderLayout.CENTER)
        render()
        return section
    }

    private fun buildRow(item: CommentBatch.Item, onRemove: () -> Unit): JPanel {
        val row = JPanel(BorderLayout())
        row.isOpaque = false
        row.border = JBUI.Borders.empty(2, 0)

        val snippet = item.comment.snippet.replace(Regex("\\s+"), " ").trim()
        val shown = if (snippet.length > MAX_SNIPPET) snippet.take(MAX_SNIPPET) + "…" else snippet

        val text = JPanel()
        text.layout = BoxLayout(text, BoxLayout.Y_AXIS)
        text.isOpaque = false
        val top = JBLabel("${item.vFile.name}  line ${item.comment.lineNumber}: \"$shown\"")
        top.font = JBFont.small()
        top.foreground = UIUtil.getContextHelpForeground()
        val request = JBLabel("> " + item.comment.request.replace("\n", " "))
        text.add(top)
        text.add(request)

        val remove = JButton(AllIcons.Actions.Close)
        remove.isBorderPainted = false
        remove.isContentAreaFilled = false
        remove.isFocusPainted = false
        remove.toolTipText = "Remove this comment"
        remove.preferredSize = Dimension(JBUI.scale(22), JBUI.scale(22))
        remove.addActionListener { onRemove() }

        row.add(text, BorderLayout.CENTER)
        row.add(remove, BorderLayout.EAST)
        return row
    }
}
