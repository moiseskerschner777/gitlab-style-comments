package com.moiseskerschner.inlinecomments

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class AddInlineCommentAction : AnAction() {

    private val logger = Logger.getInstance(AddInlineCommentAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null &&
            editor.selectionModel.hasSelection() &&
            ProgrammingLanguages.isProgrammingLanguageFile(vFile)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val document = editor.document
        val selectionEndLine = document.getLineNumber(editor.selectionModel.selectionEnd)
        val anchorOffset = document.getLineStartOffset(selectionEndLine)

        InlineCommentWidget.show(
            editor = editor,
            anchorOffset = anchorOffset,
            initialText = "",
            submitLabel = "Add Comment",
            onSubmit = { request ->
                val manager = CommentManager.getInstance(document)
                val comment = manager.addComment(editor, request)
                logger.info("line ${comment.lineNumber}: \"${comment.snippet}\" — ${comment.request}")
                GutterIconsRefresher.refresh(editor)

                val project = editor.project
                if (vFile != null && project != null) {
                    CommentBatch.touch(vFile, document)
                    val formatted = CommentBatch.format(project)
                    CopyPasteManager.getInstance().setContents(StringSelection(formatted))
                    TerminalPasteWatcher.recordCopy(formatted, project) { CommentBatch.clearAll() }
                }
            }
        )
    }
}
