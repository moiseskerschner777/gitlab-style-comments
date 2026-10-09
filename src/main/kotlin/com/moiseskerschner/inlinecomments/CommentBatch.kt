package com.moiseskerschner.inlinecomments

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

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
        touched.clear()
        EditorFactory.getInstance().allEditors.forEach { editor ->
            CommentManager.getInstance(editor.document).clearAll()
            GutterIconsRefresher.refresh(editor)
        }
    }
}
