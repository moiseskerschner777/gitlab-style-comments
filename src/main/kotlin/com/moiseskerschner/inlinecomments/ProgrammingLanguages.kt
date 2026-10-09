package com.moiseskerschner.inlinecomments

import com.intellij.openapi.vfs.VirtualFile

/**
 * Gate for "Add Inline Comment" (Ctrl+Shift+X): only enabled on source files, not on arbitrary
 * text/config/markup files. Extend EXTENSIONS as needed — it's a flat set, no other wiring.
 */
object ProgrammingLanguages {
    private val EXTENSIONS = setOf(
        "java", "kt", "kts", "scala", "groovy",
        "ts", "tsx", "js", "jsx", "mjs", "cjs",
        "py", "rb", "go", "rs", "swift",
        "c", "h", "cpp", "cc", "cxx", "hpp", "hh",
        "cs", "php", "dart", "lua",
        "sh", "bash", "zsh", "ps1",
        "sql", "pl", "pm", "r",
        "html", "htm", "css", "scss", "sass", "less"
    )

    fun isProgrammingLanguageFile(vFile: VirtualFile?): Boolean {
        // Diff/Commit viewer panes don't always populate CommonDataKeys.VIRTUAL_FILE the way a
        // normal editor tab does — when we can't determine the file type, don't block (the core
        // "select text in a diff pane" use case must keep working); only block when we positively
        // know the extension and it's not in the allow-list.
        val extension = vFile?.extension?.lowercase() ?: return true
        return extension in EXTENSIONS
    }
}
