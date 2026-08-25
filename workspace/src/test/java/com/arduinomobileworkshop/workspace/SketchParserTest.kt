package com.arduinomobileworkshop.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SketchParser]. Covers include extraction, setup()/loop()
 * detection, generic-function block reading (incl. nested braces), global
 * declaration capture, comment skipping and raw round-tripping.
 *
 * These run on a plain JVM with no Android dependencies.
 */
class SketchParserTest {

    private val basicSketch = """
#include <Arduino.h>
int led = 13;
void setup() {
  pinMode(led, OUTPUT);
}
void loop() {
  digitalWrite(led, HIGH);
  delay(500);
  digitalWrite(led, LOW);
  delay(500);
}
void blink(int pin, int ms) {
  digitalWrite(pin, HIGH);
  delay(ms);
  digitalWrite(pin, LOW);
  delay(ms);
}
""".trimIndent()

    @Test
    fun parseBasicSketchExtractsAllSections() {
        val r = SketchParser.parse(basicSketch)

        assertEquals(listOf("#include <Arduino.h>"), r.includes)
        assertEquals(listOf("int led = 13;"), r.globalDeclarations)

        assertTrue(r.setupBody.startsWith("void setup()"))
        assertTrue(r.setupBody.contains("pinMode(led, OUTPUT)"))

        assertTrue(r.loopBody.startsWith("void loop()"))
        assertTrue(r.loopBody.contains("digitalWrite(led, HIGH)"))
        assertTrue(r.loopBody.contains("delay(500)"))

        assertEquals(1, r.functions.size)
        val fn = r.functions[0]
        assertTrue(fn.contains("void blink(int pin, int ms)"))
        assertTrue(fn.contains("digitalWrite(pin, LOW)"))
        assertTrue(fn.endsWith("}"))

        assertEquals(basicSketch, r.raw)
    }

    @Test
    fun reconstructReturnsOriginalSource() {
        val r = SketchParser.parse(basicSketch)
        assertEquals(basicSketch, SketchParser.reconstruct(r))
    }

    @Test
    fun parseEmptySourceProducesEmptySections() {
        val r = SketchParser.parse("")
        assertTrue(r.includes.isEmpty())
        assertTrue(r.globalDeclarations.isEmpty())
        assertEquals("", r.setupBody)
        assertEquals("", r.loopBody)
        assertTrue(r.functions.isEmpty())
        assertEquals("", r.raw)
    }

    @Test
    fun parsesAngleAndQuoteIncludesAndSkipsComments() {
        val src = """
// header comment
#include <Arduino.h>
#include "Custom.h"
""".trimIndent()
        val r = SketchParser.parse(src)
        assertEquals(listOf("#include <Arduino.h>", "#include "Custom.h""), r.includes)
        assertTrue(r.globalDeclarations.isEmpty())
    }

    @Test
    fun readsFunctionWithNestedBracesAsSingleBlock() {
        val src = """
void f() {
  for (int i = 0; i < 3; i++) {
    if (i == 1) {
      continue;
    }
  }
}
""".trimIndent()
        val r = SketchParser.parse(src)
        assertEquals(1, r.functions.size)
        val fn = r.functions[0]
        assertTrue(fn.contains("void f()"))
        assertTrue(fn.contains("for (int i = 0; i < 3; i++)"))
        assertTrue(fn.contains("if (i == 1)"))
        assertTrue(fn.contains("continue;"))
        assertTrue(fn.endsWith("}"))
        assertTrue(r.globalDeclarations.isEmpty())
        assertEquals("", r.setupBody)
        assertEquals("", r.loopBody)
    }

    @Test
    fun prototypeWithoutBraceGoesToGlobalsNotFunctions() {
        val src = "int add(int a, int b);"
        val r = SketchParser.parse(src)
        assertEquals(listOf("int add(int a, int b);"), r.globalDeclarations)
        assertTrue(r.functions.isEmpty())
    }

    @Test
    fun globalDeclarationsKeepOriginalIndentation() {
        val src = "  int x = 5;"
        val r = SketchParser.parse(src)
        assertEquals(listOf("  int x = 5;"), r.globalDeclarations)
    }

    @Test
    fun detectsSetupAndLoopWithAlternateReturnTypes() {
        val src = """
void setup() {
  init();
}
int loop() {
  return 0;
}
""".trimIndent()
        val r = SketchParser.parse(src)
        assertTrue(r.setupBody.startsWith("void setup()"))
        assertTrue(r.loopBody.startsWith("int loop()"))
        assertTrue(r.functions.isEmpty())
    }
}
