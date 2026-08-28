package com.arduinomobileworkshop.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SketchParser]. Covers sketch parsing heuristics: include
 * extraction, global-declaration collection, setup()/loop() body extraction,
 * additional-function detection, comment skipping and round-trip
 * reconstruction. Pure JVM — no Android or Robolectric runtime required.
 */
class SketchParserTest {

    private val blink = listOf(
        "#include <Arduino.h>",
        "int ledPin = 13;",
        "void setup() {",
        "  pinMode(ledPin, OUTPUT);",
        "}",
        "void loop() {",
        "  digitalWrite(ledPin, HIGH);",
        "  delay(500);",
        "  digitalWrite(ledPin, LOW);",
        "  delay(500);",
        "}"
    ).joinToString("\n")

    @Test
    fun parsesIncludes() {
        val src = SketchParser.parse(blink)
        assertEquals(listOf("#include <Arduino.h>"), src.includes)
    }

    @Test
    fun parsesGlobalDeclarations() {
        val src = SketchParser.parse(blink)
        assertEquals(listOf("int ledPin = 13;"), src.globalDeclarations)
    }

    @Test
    fun extractsSetupBody() {
        val src = SketchParser.parse(blink)
        assertTrue(src.setupBody.contains("void setup()"))
        assertTrue(src.setupBody.contains("pinMode(ledPin, OUTPUT)"))
    }

    @Test
    fun extractsLoopBody() {
        val src = SketchParser.parse(blink)
        assertTrue(src.loopBody.contains("void loop()"))
        assertTrue(src.loopBody.contains("digitalWrite(ledPin, HIGH)"))
        assertTrue(src.loopBody.contains("delay(500)"))
    }

    @Test
    fun noExtraFunctionsForBareBlink() {
        val src = SketchParser.parse(blink)
        assertTrue(src.functions.isEmpty())
    }

    @Test
    fun detectsAdditionalFunctions() {
        val sketch = listOf(
            "void helper(int x) {",
            "  return x + 1;",
            "}"
        ).joinToString("\n")
        val src = SketchParser.parse(sketch)
        assertEquals(1, src.functions.size)
        assertTrue(src.functions[0].contains("return x + 1"))
    }

    @Test
    fun skipsComments() {
        val sketch = listOf(
            "// this is a comment",
            "int y;"
        ).joinToString("\n")
        val src = SketchParser.parse(sketch)
        assertTrue(src.includes.isEmpty())
        assertEquals(listOf("int y;"), src.globalDeclarations)
    }

    @Test
    fun parsesMultipleIncludes() {
        val sketch = listOf(
            "#include <Wire.h>",
            "#include <SPI.h>"
        ).joinToString("\n")
        val src = SketchParser.parse(sketch)
        assertEquals(listOf("#include <Wire.h>", "#include <SPI.h>"), src.includes)
    }

    @Test
    fun emptySourceYieldsEmptyParts() {
        val src = SketchParser.parse("")
        assertTrue(src.includes.isEmpty())
        assertTrue(src.globalDeclarations.isEmpty())
        assertTrue(src.functions.isEmpty())
        assertEquals("", src.setupBody)
        assertEquals("", src.loopBody)
    }

    @Test
    fun reconstructRoundTripsRaw() {
        assertEquals(blink, SketchParser.reconstruct(SketchParser.parse(blink)))
    }
}
