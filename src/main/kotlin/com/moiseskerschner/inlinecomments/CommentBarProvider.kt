package com.moiseskerschner.inlinecomments

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

class CommentBarProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val manager = CommentManager.getInstance(document)
        val count = manager.getComments().size
        if (count == 0) return null
        return Function<FileEditor, JComponent> { fileEditor ->
            val displayPath = CommentFormatter.getRelativePath(project, file)
            CommentBarPanel(count, displayPath, file.name, manager.getComments(), project, { id ->
                manager.removeComment(id)
                (fileEditor as? TextEditor)?.editor?.let { GutterIconsRefresher.refresh(it) }
                EditorNotifications.getInstance(project).updateAllNotifications()
            }) {
                manager.clearAll()
                (fileEditor as? TextEditor)?.editor?.let { GutterIconsRefresher.refresh(it) }
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }
}
