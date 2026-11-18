package com.moiseskerschner.aiinlinereviewcomments

import com.intellij.openapi.diagnostic.Logger
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object PluginLog {
    data class Entry(val time: String, val level: String, val tag: String, val message: String)

    private const val MAX_ENTRIES = 2000
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val entries = ArrayDeque<Entry>()
    private val listeners = mutableListOf<(Entry) -> Unit>()

    fun info(tag: String, message: String) {
        Logger.getInstance("com.moiseskerschner.aiinlinereviewcomments.$tag").info(message)
        record("INFO", tag, message)
    }

    fun warn(tag: String, message: String, t: Throwable? = null) {
        Logger.getInstance("com.moiseskerschner.aiinlinereviewcomments.$tag").warn(message, t)
        record("WARN", tag, if (t != null) "$message (${t.javaClass.simpleName}: ${t.message})" else message)
    }

    @Synchronized
    fun addListener(listener: (Entry) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: (Entry) -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun record(level: String, tag: String, message: String) {
        val entry = Entry(LocalTime.now().format(timeFormatter), level, tag, message)
        val toNotify = synchronized(this) {
            entries.addLast(entry)
            if (entries.size > MAX_ENTRIES) entries.removeFirst()
            listeners.toList()
        }
        toNotify.forEach { it(entry) }
    }
}
