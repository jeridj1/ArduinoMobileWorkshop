package com.arduinomobileworkshop.workspace

data class SketchProject(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val mainFile: String
)
