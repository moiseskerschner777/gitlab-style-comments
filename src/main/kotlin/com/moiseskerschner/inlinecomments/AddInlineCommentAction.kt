package com.moiseskerschner.inlinecomments

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/**
 * Select text in any editor (including Git diff / Commit tool window panes) and press
 * Ctrl+Shift+X to attach a review-style inline comment to that selection, the way
 * GitLab's diff "add comment" works — but entirely local, on top of the built-in editor.
 */
class AddInlineCommentAction : AnAction() {

    private val logger = Logger.getInstance(AddInlineCommentAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && editor.selectionModel.hasSelection()
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
                    val displayPath = CommentFormatter.getRelativePath(project, vFile)
                    val formatted = CommentFormatter.format(displayPath, vFile.name, manager.getComments())
                    CopyPasteManager.getInstance().setContents(StringSelection(formatted))
                    TerminalPasteWatcher.recordCopy(document, formatted, project)
                }
            }
        )
    }
}
