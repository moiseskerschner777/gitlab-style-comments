package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

object ClaudeTerminalSender {

    private const val NOTIFICATION_GROUP_ID = "ai-inline-review-comments"
    private const val TERMINAL_PLUGIN_ID = "org.jetbrains.plugins.terminal"
    private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
    private const val TAG = "ClaudeTerminalSender"

    /** Terminal tabs whose title contains this text (case-insensitive) are treated as a Claude Code session. */
    const val DEFAULT_TAB_NAME = "claude"

    fun send(project: Project, message: String, submit: Boolean = false, terminalTabName: String = DEFAULT_TAB_NAME): Boolean {
        if (!isTerminalPluginInstalled()) return false
        val sendToTab = findSender(project, terminalTabName, submit)
        if (sendToTab == null) {
            PluginLog.warn(TAG, "no terminal tab matching \"$terminalTabName\" found")
            notifyTabNotFound(project, terminalTabName)
            return false
        }
        sendToTab(message)
        PluginLog.info(TAG, "pasted ${message.length} chars into terminal tab matching \"$terminalTabName\"")
        return true
    }

    fun isTerminalPluginInstalled(): Boolean {
        return PluginManagerCore.isPluginInstalled(PluginId.getId(TERMINAL_PLUGIN_ID))
    }

    private fun findSender(project: Project, terminalTabName: String, submit: Boolean): ((String) -> Unit)? {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TERMINAL_TOOL_WINDOW_ID) ?: return null
        val contents = toolWindow.contentManager.contents.toList()
        val matches = { tabTitle: String? -> tabTitleMatches(tabTitle, terminalTabName) }
        return ClassicTerminalAccess.findSender(contents, matches, submit)
            ?: NewTerminalAccess.findSender(project, matches, contents, submit)
    }

    private fun tabTitleMatches(tabTitle: String?, terminalTabName: String): Boolean {
        if (tabTitle == null) return false
        return tabTitle.contains(terminalTabName, ignoreCase = true)
    }

    private fun notifyTabNotFound(project: Project, terminalTabName: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Terminal tab \"$terminalTabName\" not found. Comments were only copied to the clipboard.",
                NotificationType.WARNING
            )
            .notify(project)
    }

}
