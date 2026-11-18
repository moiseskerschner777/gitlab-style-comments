package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.util.concurrent.atomic.AtomicBoolean

object PreviewSelection {
    private const val TAG = "PreviewSelection"

    fun findSplitEditor(project: Project): TextEditorWithPreview? =
        FileEditorManager.getInstance(project).selectedEditor as? TextEditorWithPreview

    fun hasFocusedPreview(): Boolean = JBCefBrowserBase.getFocusedBrowser() != null

    fun readSelectedText(onResult: (String?, java.awt.Point?) -> Unit) {
        val browser = JBCefBrowserBase.getFocusedBrowser()
        if (browser == null) {
            PluginLog.warn(TAG, "no focused JCEF browser, cannot read preview selection")
            onResult(null, null)
            return
        }
        val query = JBCefJSQuery.create(browser)
        val done = AtomicBoolean(false)
        query.addHandler { text ->
            if (done.compareAndSet(false, true)) {
                PluginLog.info(TAG, "preview selection received (${text?.length ?: 0} chars)")
                try {
                    val sep = text?.indexOf('\u0001') ?: -1
                    if (text == null || sep < 0) {
                        onResult(text, null)
                    } else {
                        val coords = text.substring(0, sep).split(",")
                        val point = if (coords.size == 2) {
                            val x = coords[0].toDoubleOrNull()
                            val y = coords[1].toDoubleOrNull()
                            if (x != null && y != null) java.awt.Point(x.toInt(), y.toInt()) else null
                        } else null
                        onResult(text.substring(sep + 1), point)
                    }
                } finally {
                    query.dispose()
                }
            }
            null
        }
        val js = "(function(){var s=window.getSelection();var t=s?s.toString():'';var p='';" +
            "if(s&&s.rangeCount>0){var b=s.getRangeAt(0).getBoundingClientRect();p=b.left+','+b.bottom;}" +
            "var payload=p+'\\u0001'+t;" +
            query.inject("payload") + "})();"
        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
        PluginLog.info(TAG, "requested selection from preview browser")
    }

    fun mapToSource(editor: Editor, selectedText: String): Pair<Int, Int>? {
        val parts = selectedText.lines().map { normalize(it) }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        val document = editor.document
        val sourceLines = (0 until document.lineCount).map { line ->
            normalize(document.getText(com.intellij.openapi.util.TextRange(
                document.getLineStartOffset(line), document.getLineEndOffset(line)
            )))
        }

        val startLine = sourceLines.indexOfFirst { it.isNotEmpty() && matches(it, parts.first()) }
        if (startLine < 0) return null
        val lastPart = parts.last()
        var endLine = startLine
        if (parts.size > 1 || !matches(sourceLines[startLine], lastPart)) {
            val found = (startLine until sourceLines.size).firstOrNull {
                sourceLines[it].isNotEmpty() && matches(sourceLines[it], lastPart)
            }
            if (found != null) endLine = found
        }
        return document.getLineStartOffset(startLine) to document.getLineEndOffset(endLine)
    }

    private fun matches(sourceLine: String, part: String): Boolean {
        if (sourceLine.contains(part)) return true
        val probe = part.take(40)
        return probe.length >= 8 && sourceLine.contains(probe)
    }

    private fun normalize(raw: String): String {
        var s = raw
        s = s.replace(Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("^\\s*(#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+|>\\s*)"), "")
        s = s.replace(Regex("[*_`|\\\\]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.lowercase()
    }
}
