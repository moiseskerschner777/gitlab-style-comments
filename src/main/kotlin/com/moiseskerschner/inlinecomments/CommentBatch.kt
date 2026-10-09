package com.moiseskerschner.inlinecomments

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tracks every file touched by "Add Inline Comment" since the last clipboard copy, so the
 * clipboard payload accumulates comments across files instead of each new file's comments
 * replacing the previous file's in the clipboard (used by format()).
 *
 * clearAll() is intentionally NOT limited to just the touched/batched files: once a paste+submit
 * is detected anywhere, it wipes every inline comment in every currently open editor across the
 * WHOLE IDE (all project windows) — by design. A narrower "only clear what was in this specific
 * clipboard snapshot" version was tried and explicitly rejected: comments left over from another
 * window, or from files touched in an earlier batch cycle, should not survive a submit either.
 *
 * Comments themselves still live per-document in CommentManager (unchanged) — this just
 * remembers which documents to re-read and re-format on every copy, and does a full sweep to
 * clear.
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
        touched.clear()
        EditorFactory.getInstance().allEditors.forEach { editor ->
            CommentManager.getInstance(editor.document).clearAll()
            GutterIconsRefresher.refresh(editor)
        }
    }
}
