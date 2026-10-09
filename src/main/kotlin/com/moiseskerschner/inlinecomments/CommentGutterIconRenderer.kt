package com.moiseskerschner.inlinecomments

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer

class CommentGutterIconRenderer(
    private val comment: InlineComment,
    private val editor: Editor,
    private val onChanged: () -> Unit
) : GutterIconRenderer() {

    override fun getIcon() = AllIcons.General.Balloon

    override fun getTooltipText(): String = comment.request

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val document = editor.document
            val anchorOffset = document.getLineStartOffset(document.getLineNumber(comment.startOffset))

            InlineCommentWidget.show(
                editor = editor,
                anchorOffset = anchorOffset,
                initialText = comment.request,
                submitLabel = "Update Comment",
                onSubmit = { newText ->
                    CommentManager.getInstance(document).updateComment(comment.id, newText)
                    onChanged()
                },
                onDelete = {
                    CommentManager.getInstance(document).removeComment(comment.id)
                    onChanged()
                }
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CommentGutterIconRenderer && other.comment.id == comment.id

    override fun hashCode(): Int = comment.id.hashCode()
}
