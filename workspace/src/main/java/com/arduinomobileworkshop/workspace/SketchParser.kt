package com.arduinomobileworkshop.workspace

/**
 * Lightweight Arduino-sketch text parser. Splits a raw .ino source string into
 * structural parts (includes, globals, setup(), loop(), other functions) so the
 * IDE can populate its editor / outline buffers without a full C++ parser.
 *
 * The original [raw] text is always preserved for round-tripping back into the
 * editor. This is intentionally heuristic: it is meant to feed buffers, not to
 * validate C++ syntax.
 */
data class SketchSource(
    val raw: String,
    val includes: List<String>,
    val globalDeclarations: List<String>,
    val setupBody: String,
    val loopBody: String,
    val functions: List<String>
)

object SketchParser {

    fun parse(source: String): SketchSource {
        val includes = mutableListOf<String>()
        val globalDecls = mutableListOf<String>()
        val functions = mutableListOf<String>()
        var setupBody = ""
        var loopBody = ""

        val lines = source.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.startsWith("#include")) {
                includes += trimmed
                i++; continue
            }
            if (trimmed.startsWith("//")) {
                i++; continue
            }

            val isSetup = startsFunction(trimmed, "setup")
            val isLoop = startsFunction(trimmed, "loop")

            if (isSetup || isLoop) {
                val block = readBlock(lines, i)
                if (isSetup) setupBody = block.text else loopBody = block.text
                i = block.nextIndex
                continue
            }

            // Heuristic: a top-level function definition opens both '(' and '{'.
            if (trimmed.contains('(') && line.contains('{')) {
                val block = readBlock(lines, i)
                functions += block.text
                i = block.nextIndex
                continue
            }

            if (trimmed.isNotEmpty()) globalDecls += line
            i++
        }
        return SketchSource(source, includes, globalDecls, setupBody, loopBody, functions)
    }

    private fun startsFunction(trimmed: String, name: String): Boolean {
        if (!trimmed.contains(name)) return false
        return trimmed.startsWith("void $name(") ||
            trimmed.startsWith("int $name(") ||
            trimmed.startsWith("inline void $name(") ||
            trimmed.startsWith("static void $name(") ||
            trimmed.startsWith("inline int $name(") ||
            trimmed.startsWith("static int $name(")
    }

    private data class Block(val text: String, val nextIndex: Int)

    /** Reads from the opening line through the matching closing brace. */
    private fun readBlock(lines: List<String>, start: Int): Block {
        val sb = StringBuilder()
        var depth = 0
        var i = start
        var started = false
        while (i < lines.size) {
            val line = lines[i]
            sb.appendLine(line)
            for (ch in line) {
                if (ch == '{') { depth++; started = true }
                else if (ch == '}') depth--
            }
            i++
            if (started && depth <= 0) break
        }
        return Block(sb.toString().trimEnd(), i)
    }

    fun reconstruct(source: SketchSource): String = source.raw
}
