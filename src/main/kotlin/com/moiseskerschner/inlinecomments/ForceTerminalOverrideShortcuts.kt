package com.moiseskerschner.inlinecomments

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Forces Settings > Tools > Terminal > "Override IDE shortcuts" to true on every project open.
 *
 * Without this, Ctrl+V (and other keystrokes) can get intercepted by IDE-level actions before
 * reaching the terminal's PTY, so paste into a terminal-hosted app (e.g. Claude Code's CLI)
 * silently does nothing. Confirmed via idea.log: Settings Sync pushed a fresh options/terminal.xml
 * from another machine, which reset this to its platform default (false) and broke paste that had
 * previously been working — nothing in this plugin's own code changed. Re-asserting it here on
 * every project open means that kind of sync reset can't silently regress paste again.
 *
 * Uses reflection against org.jetbrains.plugins.terminal.TerminalOptionsProvider instead of a
 * direct reference: javap confirms getInstance()/getOverrideIdeShortcuts()/
 * setOverrideIdeShortcuts(boolean) all exist as public members on that class, but the Kotlin
 * compiler reports "Unresolved reference" on getInstance() specifically (both
 * `TerminalOptionsProvider.getInstance()` and `TerminalOptionsProvider.Companion.getInstance()`)
 * despite TerminalView/TerminalOutputModel from the same jar resolving fine elsewhere in this
 * plugin — likely a Kotlin metadata quirk specific to that declaration. Reflection sidesteps it.
 */
class ForceTerminalOverrideShortcuts : ProjectActivity {
    private val logger = Logger.getInstance(ForceTerminalOverrideShortcuts::class.java)

    override suspend fun execute(project: Project) {
        try {
            val cls = Class.forName("org.jetbrains.plugins.terminal.TerminalOptionsProvider")
            val instance = cls.getMethod("getInstance").invoke(null)
            val isEnabled = cls.getMethod("getOverrideIdeShortcuts").invoke(instance) as Boolean
            if (!isEnabled) {
                cls.getMethod("setOverrideIdeShortcuts", Boolean::class.javaPrimitiveType)
                    .invoke(instance, true)
                logger.info("ForceTerminalOverrideShortcuts: enabled 'Override IDE shortcuts' (was off)")
            }
        } catch (t: Throwable) {
            logger.warn("ForceTerminalOverrideShortcuts: failed to enforce terminal override-shortcuts setting", t)
        }
    }
}
