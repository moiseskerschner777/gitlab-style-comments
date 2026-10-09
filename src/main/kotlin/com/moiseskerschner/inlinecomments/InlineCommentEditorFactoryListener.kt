package com.moiseskerschner.inlinecomments

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class InlineCommentEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        GutterIconsRefresher.refresh(event.editor)
    }
}
