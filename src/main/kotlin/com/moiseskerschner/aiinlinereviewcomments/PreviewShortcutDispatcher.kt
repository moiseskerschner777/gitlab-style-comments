package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.jcef.JBCefBrowserBase
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

class PreviewShortcutDispatcher : ProjectActivity {

    override suspend fun execute(project: Project) {
        install()
    }

    companion object {
        private const val TAG = "PreviewShortcutDispatcher"
        private var installed = false

        @Synchronized
        fun install() {
            if (installed) return
            installed = true
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(KeyEventDispatcher { event ->
                if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_X) return@KeyEventDispatcher false
                val mask = InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
                val blocked = InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK
                if (event.modifiersEx and mask != mask || event.modifiersEx and blocked != 0) {
                    return@KeyEventDispatcher false
                }
                try {
                    val browser = JBCefBrowserBase.getFocusedBrowser()
                    PluginLog.info(TAG, "Ctrl+Shift+X seen, focusedBrowser=${browser != null}")
                    if (browser == null) return@KeyEventDispatcher false
                    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                    if (focusOwner == null || !SwingUtilities.isDescendingFrom(focusOwner, browser.component)) {
                        PluginLog.info(TAG, "focused browser is stale (focus is elsewhere), leaving to the action")
                        return@KeyEventDispatcher false
                    }
                    val project = ProjectManager.getInstance().openProjects.firstOrNull { PreviewSelection.findSplitEditor(it) != null }
                        ?: return@KeyEventDispatcher false
                    AddInlineCommentAction.runPreviewFlow(project)
                    true
                } catch (t: Throwable) {
                    PluginLog.warn(TAG, "preview shortcut handling failed", t)
                    false
                }
            })
        }
    }
}
