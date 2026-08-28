package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Unit tests for [ToolchainManager]. Covers the built-in default board catalog
 * (used when no network/CLI index is available), board lookup by id and name,
 * and add/remove board configuration. The arduino-cli binary is absent in the
 * JVM sandbox, so the CLI-backed paths short-circuit cleanly; only the
 * pure-data and caching surfaces are exercised here.
 */
class ToolchainManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ToolchainManager
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = File(System.getProperty("java.io.tmpdir"), "amw-test-" + System.nanoTime())
        context = mock()
        whenever(context.filesDir).thenReturn(File(tmpDir, "files"))
        // Point nativeLibraryDir at a non-existent path so ArduinoCliManager's
        // ensureInstalled() returns false without throwing, letting the
        // background refresh thread started by initialize() exit cleanly.
        val appInfo = ApplicationInfo()
        appInfo.nativeLibraryDir = File(tmpDir, "nativelib").absolutePath
        whenever(context.applicationInfo).thenReturn(appInfo)
        manager = ToolchainManager(context)
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun initializePopulatesDefaultBoardCatalog() {
        manager.initialize()
        assertEquals(5, manager.getAvailableBoards().size)
    }

    @Test
    fun defaultCatalogIncludesArduinoUno() {
        manager.initialize()
        val uno = manager.getBoardConfig("arduino:avr:uno")
        assertNotNull(uno)
        assertEquals("Arduino Uno", uno!!.name)
        assertEquals("avr", uno.architecture)
        assertEquals("avrdude", uno.uploadTool)
    }

    @Test
    fun defaultCatalogIncludesRp2040PicoWithUf2Tool() {
        manager.initialize()
        val pico = manager.getBoardConfig("rp2040:rp2040:rpipico")
        assertNotNull(pico)
        assertEquals("Raspberry Pi Pico", pico!!.name)
        assertEquals("uf2", pico.uploadTool)
    }

    @Test
    fun getBoardConfigByNameResolvesNano() {
        manager.initialize()
        val nano = manager.getBoardConfigByName("Arduino Nano")
        assertNotNull(nano)
        assertEquals("arduino:avr:nano", nano!!.id)
    }

    @Test
    fun unknownBoardIdReturnsNull() {
        manager.initialize()
        assertNull(manager.getBoardConfig("no:such:board"))
    }

    @Test
    fun addBoardConfigAcceptsNewId() {
        manager.initialize()
        val custom = ToolchainManager.Board(
            "custom:avr:pro", "Custom Pro", "avr", "custom:avr",
            "1.0", "avr", "avrdude", "arduino"
        )
        assertTrue(manager.addBoardConfig(custom))
        assertNotNull(manager.getBoardConfig("custom:avr:pro"))
    }

    @Test
    fun addBoardConfigRejectsDuplicateId() {
        manager.initialize()
        val dup = ToolchainManager.Board(
            "arduino:avr:uno", "Uno Clone", "avr", "arduino:avr",
            "1.8.6", "avr", "avrdude", "arduino"
        )
        assertFalse(manager.addBoardConfig(dup))
    }

    @Test
    fun removeBoardConfigRemovesExistingEntry() {
        manager.initialize()
        assertTrue(manager.removeBoardConfig("arduino:avr:uno"))
        assertNull(manager.getBoardConfig("arduino:avr:uno"))
    }

    @Test
    fun installedLibrariesStartEmptyWithoutCli() {
        manager.initialize()
        assertTrue(manager.getInstalledLibraries().isEmpty())
    }
}
