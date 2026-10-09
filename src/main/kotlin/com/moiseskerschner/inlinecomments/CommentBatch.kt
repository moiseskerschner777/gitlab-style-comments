package com.moiseskerschner.inlinecomments

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tracks every file touched by "Add Inline Comment" since the last successful paste+submit into
 * a terminal, so the clipboard payload accumulates comments across files instead of each new
 * file's comments replacing the previous file's in the clipboard.
 *
 * Comments themselves still live per-document in CommentManager (unchanged) — this just
 * remembers *which* documents to re-read and re-format on every copy, and which ones to clear
 * together once TerminalPasteWatcher confirms the batch was submitted.
 */
object CommentBatch {
    private val touched = LinkedHashMap<VirtualFile, Document>()

    fun touch(vFile: VirtualFile, document: Document) {
        touched[vFile] = document
    }

    fun format(project: Project): String {
        return touched.entries
            .mapNotNull { (vFile, document) ->
                val comments = CommentManager.getInstance(document).getComments()
                if (comments.isEmpty()) return@mapNotNull null
                val displayPath = CommentFormatter.getRelativePath(project, vFile)
                CommentFormatter.format(displayPath, vFile.name, comments)
            }
            .joinToString("\n\n")
    }

    fun clearAll() {
        val documents = touched.values.toList()
        touched.clear()
        documents.forEach { document ->
            CommentManager.getInstance(document).clearAll()
            EditorFactory.getInstance().getEditors(document).forEach { GutterIconsRefresher.refresh(it) }
        }
    }
}
