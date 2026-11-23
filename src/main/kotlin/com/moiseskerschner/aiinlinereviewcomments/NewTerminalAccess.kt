package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import java.awt.Component
import java.awt.Container

object NewTerminalAccess {

    private const val TABS_MANAGER_CLASS = "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager"
    private const val SEND_TEXT_BUILDER_CLASS = "org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder"
    private const val FRONTEND_PACKAGE_PREFIX = "com.intellij.terminal.frontend"

    private val logger = Logger.getInstance(NewTerminalAccess::class.java)

    fun findSender(project: Project, tabTitleMatches: (String?) -> Boolean, contents: List<Content>, submit: Boolean): ((String) -> Unit)? {
        return try {
            findSenderOrThrow(project, tabTitleMatches, contents, submit)
        } catch (exception: ReflectiveOperationException) {
            logger.info("New terminal API is not available: ${exception.javaClass.simpleName}: ${exception.message}")
            null
        }
    }

    private fun findSenderOrThrow(
        project: Project,
        tabTitleMatches: (String?) -> Boolean,
        contents: List<Content>,
        submit: Boolean
    ): ((String) -> Unit)? {
        val loader = resolveFrontendClassLoader(contents)
        val managerClass = Class.forName(TABS_MANAGER_CLASS, true, loader)
        val manager = managerClass.getMethod("getInstance", Project::class.java).invoke(null, project)
        val tabs = managerClass.getMethod("getTabs").invoke(manager) as List<*>
        val tab = tabs.firstOrNull { it != null && tabTitleMatches(contentOf(it).displayName) } ?: return null
        val view = tab.javaClass.getMethod("getView").invoke(tab)
        return { message -> sendThroughViewLoggingFailure(view, loader, message, submit) }
    }

    private fun contentOf(tab: Any): Content = tab.javaClass.getMethod("getContent").invoke(tab) as Content

    private fun sendThroughViewLoggingFailure(view: Any, loader: ClassLoader, message: String, submit: Boolean) {
        try {
            sendThroughView(view, loader, message, submit)
        } catch (exception: ReflectiveOperationException) {
            logger.warn("Failed to send comments through the new terminal API", exception)
        }
    }

    private fun sendThroughView(view: Any, loader: ClassLoader, message: String, submit: Boolean) {
        val builderClass = Class.forName(SEND_TEXT_BUILDER_CLASS, true, loader)
        var builder = view.javaClass.getMethod("createSendTextBuilder").invoke(view)
        if (submit) builder = builderClass.getMethod("shouldExecute").invoke(builder)
        builder = builderClass.getMethod("useBracketedPasteMode").invoke(builder)
        builderClass.getMethod("send", String::class.java).invoke(builder, message)
    }

    private fun resolveFrontendClassLoader(contents: List<Content>): ClassLoader {
        val candidates = listOf(NewTerminalAccess::class.java.classLoader) + contents.flatMap { classLoadersOf(it.component) }
        return candidates.firstOrNull { canLoadTabsManager(it) }
            ?: throw ClassNotFoundException(TABS_MANAGER_CLASS)
    }

    private fun canLoadTabsManager(loader: ClassLoader): Boolean {
        return try {
            Class.forName(TABS_MANAGER_CLASS, false, loader)
            true
        } catch (exception: ClassNotFoundException) {
            false
        }
    }

    private fun classLoadersOf(component: Component): List<ClassLoader> {
        val own = if (component.javaClass.name.startsWith(FRONTEND_PACKAGE_PREFIX)) listOfNotNull(component.javaClass.classLoader) else emptyList()
        if (component !is Container) return own
        return own + component.components.flatMap { classLoadersOf(it) }
    }

}
