package com.moiseskerschner.inlinecomments

import com.intellij.openapi.vfs.VirtualFile

object ProgrammingLanguages {
    private val EXTENSIONS = setOf(
        "java", "kt", "kts", "scala", "groovy",
        "ts", "tsx", "js", "jsx", "mjs", "cjs",
        "py", "rb", "go", "rs", "swift",
        "c", "h", "cpp", "cc", "cxx", "hpp", "hh",
        "cs", "php", "dart", "lua",
        "sh", "bash", "zsh", "ps1",
        "sql", "pl", "pm", "r",
        "html", "htm", "css", "scss", "sass", "less",
        "json"
    )

    fun isProgrammingLanguageFile(vFile: VirtualFile?): Boolean {
        val extension = vFile?.extension?.lowercase() ?: return true
        return extension in EXTENSIONS
    }
}
