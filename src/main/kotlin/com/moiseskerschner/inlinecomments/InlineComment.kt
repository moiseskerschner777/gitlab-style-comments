package com.moiseskerschner.inlinecomments

data class InlineComment(
    val id: String,
    val startOffset: Int,
    val endOffset: Int,
    val snippet: String,
    val request: String,
    val lineNumber: Int
)
