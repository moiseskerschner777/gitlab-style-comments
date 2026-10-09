package com.moiseskerschner.inlinecomments

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants

object InlineCommentWidget {

    private const val CARD_ARC = 10

    fun show(
        editor: Editor,
        anchorOffset: Int,
        initialText: String,
        submitLabel: String,
        onSubmit: (String) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val editorEx = editor as? EditorEx ?: return

        val scheme = EditorColorsManager.getInstance().globalScheme
        val borderColor = scheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()
        val cardBackground = scheme.defaultBackground

        var inlay: com.intellij.openapi.editor.Inlay<*>? = null
        fun dismiss() {
            val toDispose = inlay ?: return
            ApplicationManager.getApplication().invokeLater {
                if (!Disposer.isDisposed(toDispose)) {
                    Disposer.dispose(toDispose)
                }
            }
        }

        val textArea = JBTextArea(initialText)
        textArea.rows = 1
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.isOpaque = false
        textArea.border = null
        textArea.font = textArea.font.deriveFont(13f)

        fun doSubmit() {
            val text = textArea.text
            if (text.isNotBlank()) {
                onSubmit(text)
            }
            dismiss()
        }

        val submitAction = object : AbstractAction(submitLabel) {
            override fun actionPerformed(e: ActionEvent?) = doSubmit()
        }

        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = doSubmit()
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, textArea)

        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = dismiss()
        }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, textArea)

        textArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-newline")
        textArea.actionMap.put("insert-newline", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                textArea.replaceSelection("\n")
            }
        })

        val avatarLabel = JBLabel(AllIcons.General.User)
        avatarLabel.verticalAlignment = SwingConstants.TOP
        avatarLabel.border = JBUI.Borders.emptyRight(10)

        val inputRow = JPanel(BorderLayout())
        inputRow.isOpaque = false
        inputRow.border = JBUI.Borders.empty(6, 10)
        inputRow.add(avatarLabel, BorderLayout.WEST)
        inputRow.add(textArea, BorderLayout.CENTER)

        val closeButton = JButton(AllIcons.Actions.Close)
        closeButton.isBorderPainted = false
        closeButton.isContentAreaFilled = false
        closeButton.isFocusPainted = false
        closeButton.preferredSize = java.awt.Dimension(JBUI.scale(22), JBUI.scale(22))
        closeButton.toolTipText = "Cancel"
        closeButton.addActionListener { dismiss() }

        val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        topRow.isOpaque = false
        topRow.border = JBUI.Borders.empty(4, 4, 0, 0)
        topRow.add(closeButton)
        if (onDelete != null) {
            val deleteButton = JButton("Delete")
            deleteButton.foreground = JBColor(0xC75450, 0xFF6B68)
            deleteButton.isFocusPainted = false
            deleteButton.addActionListener {
                onDelete()
                dismiss()
            }
            topRow.add(deleteButton)
        }

        val hintLabel = JBLabel("Enter to comment    Shift+Enter to add new line")
        hintLabel.font = JBFont.small()
        hintLabel.foreground = UIUtil.getContextHelpForeground()

        val submitButton = JButton(submitAction)
        ClientProperty.put(submitButton, DarculaButtonUI.DEFAULT_STYLE_KEY, true)

        val footerRow = JPanel(BorderLayout())
        footerRow.isOpaque = false
        footerRow.border = JBUI.Borders.empty(0, 10, 8, 10)
        footerRow.add(hintLabel, BorderLayout.WEST)
        footerRow.add(submitButton, BorderLayout.EAST)

        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.isOpaque = false
        content.add(topRow)
        content.add(inputRow)
        content.add(footerRow)

        val card = RoundedCardPanel(cardBackground)
        card.layout = BorderLayout()
        card.border = RoundedLineBorder(borderColor, CARD_ARC)
        card.add(content, BorderLayout.CENTER)

        val noIcon = com.intellij.util.ui.EmptyIcon.ICON_0
        val gutterIcon = object : GutterIconRenderer() {
            override fun getIcon() = noIcon
            override fun equals(other: Any?) = other is GutterIconRenderer
            override fun hashCode() = 0
        }

        val properties = EditorEmbeddedComponentManager.Properties(
            EditorEmbeddedComponentManager.ResizePolicy.none(),
            EditorEmbeddedComponentManager.Properties.RendererFactory { gutterIcon },
            true,
            false,
            false,
            true,
            0,
            anchorOffset
        )

        inlay = EditorEmbeddedComponentManager.getInstance().addComponent(editorEx, card, properties)

        ApplicationManager.getApplication().invokeLater {
            textArea.requestFocusInWindow()
            textArea.caretPosition = textArea.text.length
        }
    }

    private class RoundedCardPanel(private val fill: Color) : JPanel() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = fill
                g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), CARD_ARC.toFloat(), CARD_ARC.toFloat()))
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
}
