package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object ClassicTerminalAccess {

    private const val BRACKETED_PASTE_START = "\u001b[200~"
    private const val BRACKETED_PASTE_END = "\u001b[201~"
    private const val SUBMIT_KEY = "\r"

    private val logger = Logger.getInstance(ClassicTerminalAccess::class.java)

    fun findSender(contents: List<Content>, tabTitleMatches: (String?) -> Boolean, submit: Boolean): ((String) -> Unit)? {
        val widget = findWidget(contents, tabTitleMatches) ?: return null
        return { message -> writeToTerminal(widget, message, submit) }
    }

    private fun findWidget(contents: List<Content>, tabTitleMatches: (String?) -> Boolean): TerminalWidget? {
        val content = contents.firstOrNull { tabTitleMatches(it.displayName) } ?: return null
        return TerminalToolWindowManager.findWidgetByContent(content)
    }

    private fun writeToTerminal(widget: TerminalWidget, message: String, submit: Boolean) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val connector = widget.ttyConnector ?: return@executeOnPooledThread
                connector.write(BRACKETED_PASTE_START + message + BRACKETED_PASTE_END)
                if (submit) connector.write(SUBMIT_KEY)
            } catch (exception: Exception) {
                logger.warn("Failed to write comments to the terminal", exception)
            }
        }
    }

}
