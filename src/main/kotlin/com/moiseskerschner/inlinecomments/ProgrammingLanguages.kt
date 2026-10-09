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
        "sql", "pl", "pm", "r"
    )

    fun isProgrammingLanguageFile(vFile: VirtualFile?): Boolean {
        val extension = vFile?.extension?.lowercase() ?: return false
        return extension in EXTENSIONS
    }
}
