package com.moiseskerschner.inlinecomments

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID

class CommentManager {

    private val comments = mutableListOf<InlineComment>()
    private val markers = mutableListOf<RangeMarker>()
    private var document: Document? = null

    fun addComment(editor: Editor, request: String): InlineComment {
        val document = editor.document
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val text = document.getText(TextRange(selectionStart, selectionEnd))
        val lineNumber = document.getLineNumber(selectionStart) + 1
        val comment = InlineComment(
            id = UUID.randomUUID().toString(),
            startOffset = selectionStart,
            endOffset = selectionEnd,
            snippet = text,
            request = request,
            lineNumber = lineNumber
        )
        comments.add(comment)
        markers.add(document.createRangeMarker(selectionStart, selectionEnd))
        saveIfDocumentAvailable()
        return comment
    }

    fun getComments(): List<InlineComment> = runReadAction {
        comments.mapIndexed { index, comment ->
            val marker = markers[index]
            if (!marker.isValid) {
                comment
            } else {
                val document = marker.document
                val currentText = document.getText(TextRange(marker.startOffset, marker.endOffset))
                val currentLine = document.getLineNumber(marker.startOffset) + 1
                comment.copy(
                    startOffset = marker.startOffset,
                    endOffset = marker.endOffset,
                    snippet = currentText,
                    lineNumber = currentLine
                )
            }
        }
    }

    fun updateComment(id: String, newRequest: String) {
        val index = comments.indexOfFirst { it.id == id }
        if (index >= 0) {
            comments[index] = comments[index].copy(request = newRequest)
            saveIfDocumentAvailable()
        }
    }

    fun removeComment(id: String) {
        val index = comments.indexOfFirst { it.id == id }
        if (index >= 0) {
            markers[index].dispose()
            comments.removeAt(index)
            markers.removeAt(index)
            saveIfDocumentAvailable()
        }
    }

    fun clearAll() {
        markers.forEach { it.dispose() }
        markers.clear()
        comments.clear()
        saveIfDocumentAvailable()
    }

    private fun saveIfDocumentAvailable() {
        val doc = document ?: return
        val vFile = FileDocumentManager.getInstance().getFile(doc) ?: return
        CommentStorage.save(vFile, comments.toList())
    }

    fun loadFromStorage(file: VirtualFile, document: Document) {
        val loaded = CommentStorage.load(file)
        if (loaded.isEmpty()) return
        comments.clear()
        markers.forEach { it.dispose() }
        markers.clear()
        for (comment in loaded) {
            comments.add(comment)
            markers.add(document.createRangeMarker(comment.startOffset, comment.endOffset))
        }
    }

    companion object {
        val KEY = Key.create<CommentManager>("inlineCommentManager")

        fun getInstance(document: Document): CommentManager {
            var manager = document.getUserData(KEY)
            if (manager == null) {
                manager = CommentManager()
                document.putUserData(KEY, manager)
                val vFile = FileDocumentManager.getInstance().getFile(document)
                if (vFile != null) {
                    manager.loadFromStorage(vFile, document)
                }
            }
            manager.document = document
            return manager
        }
    }
}
