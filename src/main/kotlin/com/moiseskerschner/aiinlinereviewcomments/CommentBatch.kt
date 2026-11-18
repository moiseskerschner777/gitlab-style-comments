package com.moiseskerschner.aiinlinereviewcomments

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

    data class Item(val vFile: VirtualFile, val document: Document, val comment: InlineComment)

    fun items(): List<Item> = touched.entries.flatMap { (vFile, document) ->
        CommentManager.getInstance(document).getComments().map { Item(vFile, document, it) }
    }

    fun remove(item: Item) {
        CommentManager.getInstance(item.document).removeComment(item.comment.id)
        EditorFactory.getInstance().getEditors(item.document).forEach { GutterIconsRefresher.refresh(it) }
    }

    fun count(): Int = touched.values.sumOf { CommentManager.getInstance(it).getComments().size }

    fun clearAll() {
        touched.clear()
        EditorFactory.getInstance().allEditors.forEach { editor ->
            CommentManager.getInstance(editor.document).clearAll()
            GutterIconsRefresher.refresh(editor)
        }
    }
}
