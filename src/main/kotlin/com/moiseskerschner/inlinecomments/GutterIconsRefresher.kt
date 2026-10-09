package com.moiseskerschner.inlinecomments

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key

object GutterIconsRefresher {
    private val HIGHLIGHTERS_KEY = Key.create<MutableList<RangeHighlighter>>("inlineCommentHighlighters")

    fun refresh(editor: Editor) {
        val document = editor.document
        val manager = CommentManager.getInstance(document)
        val markupModel = editor.markupModel

        editor.getUserData(HIGHLIGHTERS_KEY)?.forEach { if (it.isValid) markupModel.removeHighlighter(it) }

        val highlighters = mutableListOf<RangeHighlighter>()
        for (comment in manager.getComments()) {
            if (comment.startOffset < 0 || comment.endOffset > document.textLength || comment.startOffset > comment.endOffset) {
                continue
            }
            val highlighter = markupModel.addRangeHighlighter(
                comment.startOffset,
                comment.endOffset,
                HighlighterLayer.ADDITIONAL_SYNTAX,
                null,
                HighlighterTargetArea.EXACT_RANGE
            )
            highlighter.gutterIconRenderer = CommentGutterIconRenderer(comment, editor) {
                refresh(editor)
            }
            highlighters.add(highlighter)
        }
        editor.putUserData(HIGHLIGHTERS_KEY, highlighters)
    }
}
