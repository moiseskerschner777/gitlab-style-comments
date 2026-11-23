package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import java.awt.datatransfer.StringSelection

class AddInlineCommentAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private fun resolveEditor(e: AnActionEvent): Editor? =
        e.getData(CommonDataKeys.EDITOR)
            ?: e.project?.let { FileEditorManager.getInstance(it).selectedTextEditor }

    private fun resolveFile(e: AnActionEvent, editor: Editor?): VirtualFile? =
        e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: editor?.let { FileDocumentManager.getInstance().getFile(it.document) }

    override fun update(e: AnActionEvent) {
        val editor = resolveEditor(e)
        val vFile = resolveFile(e, editor)
        val hasEditorSelection = editor != null && editor.selectionModel.hasSelection()
        val previewCandidate = !hasEditorSelection &&
            e.project?.let { PreviewSelection.findSplitEditor(it) } != null
        e.presentation.isEnabledAndVisible = (hasEditorSelection || previewCandidate) &&
            ProgrammingLanguages.isProgrammingLanguageFile(vFile)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val current = resolveEditor(e)
        if (current != null && current.selectionModel.hasSelection()) {
            addComment(current, resolveFile(e, current))
            return
        }
        if (project != null) runPreviewFlow(project)
    }

    companion object {
        private const val TAG = "AddInlineCommentAction"

        private var lastPreviewFlowAt = 0L

        fun runPreviewFlow(project: Project) {
            val now = System.currentTimeMillis()
            if (now - lastPreviewFlowAt < 800 || PreviewCommentPopup.isShowing()) {
                PluginLog.info(TAG, "preview flow skipped (already running or popup open)")
                return
            }
            lastPreviewFlowAt = now
            val split = PreviewSelection.findSplitEditor(project)
            PluginLog.info(TAG, "preview flow: splitEditor=${split != null}, focusedBrowser=${PreviewSelection.hasFocusedPreview()}")
            if (split == null) return
            PreviewSelection.readSelectedText { text, point ->
                ApplicationManager.getApplication().invokeLater {
                    if (text.isNullOrBlank()) return@invokeLater
                    val textEditor = split.textEditor.editor
                    val range = PreviewSelection.mapToSource(textEditor, text)
                    if (range == null) {
                        PluginLog.warn(TAG, "could not map preview selection to source: \"${text.take(80)}\"")
                        return@invokeLater
                    }
                    val vFile = FileDocumentManager.getInstance().getFile(textEditor.document)
                    PreviewCommentPopup.show(project, split.previewEditor.component, point) { request ->
                        textEditor.selectionModel.setSelection(range.first, range.second)
                        saveComment(textEditor, vFile, request)
                    }
                }
            }
        }

        private fun addComment(editor: Editor, vFile: VirtualFile?) {
            val document = editor.document
            val selectionEndLine = document.getLineNumber(editor.selectionModel.selectionEnd)
            val anchorOffset = document.getLineStartOffset(selectionEndLine)

            InlineCommentWidget.show(
                editor = editor,
                anchorOffset = anchorOffset,
                initialText = "",
                submitLabel = "Add Comment",
                onSubmit = { request -> saveComment(editor, vFile, request) }
            )
        }

        private fun saveComment(editor: Editor, vFile: VirtualFile?, request: String) {
            val document = editor.document
            val manager = CommentManager.getInstance(document)
            val comment = manager.addComment(editor, request)
            PluginLog.info(TAG, "line ${comment.lineNumber}: \"${comment.snippet}\" — ${comment.request}")
            GutterIconsRefresher.refresh(editor)

            val project = editor.project
            if (vFile != null && project != null) {
                CommentBatch.touch(vFile, document)
                val formatted = CommentBatch.format(project)
                CopyPasteManager.getInstance().setContents(StringSelection(formatted))
                if (ClaudeTerminalSender.isTerminalPluginInstalled() && ClaudeTerminalSender.send(project, formatted, submit = true)) {
                    TerminalPasteWatcher.cancel()
                    CommentBatch.clearAll()
                    EditorNotifications.getInstance(project).updateAllNotifications()
                } else if (ClaudeTerminalSender.isTerminalPluginInstalled()) {
                    TerminalPasteWatcher.recordCopy(formatted, project) { CommentBatch.clearAll() }
                }
            }
        }
    }
}
