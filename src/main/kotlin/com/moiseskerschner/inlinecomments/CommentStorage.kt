package com.moiseskerschner.inlinecomments

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.security.MessageDigest

object CommentStorage {
    private val gson = Gson()

    private fun getStorageFile(virtualFile: VirtualFile): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(virtualFile.path.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val dir = File(PathManager.getSystemPath(), "inline-comments")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$hash.json")
    }

    fun save(file: VirtualFile, comments: List<InlineComment>) {
        getStorageFile(file).writeText(gson.toJson(comments))
    }

    fun load(file: VirtualFile): List<InlineComment> {
        val jsonFile = getStorageFile(file)
        if (!jsonFile.exists()) return emptyList()
        val type = object : TypeToken<List<InlineComment>>() {}.type
        return gson.fromJson(jsonFile.readText(), type)
    }
}
