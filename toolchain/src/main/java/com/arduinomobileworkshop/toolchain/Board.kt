package com.arduinomobileworkshop.toolchain

data class Board(
    val id: String,
    val name: String,
    val platform: String,
    val packageName: String,
    val version: String,
    val architecture: String,
    val uploadTool: String,
    val programmer: String,
    val fqbn: String = ""
)
