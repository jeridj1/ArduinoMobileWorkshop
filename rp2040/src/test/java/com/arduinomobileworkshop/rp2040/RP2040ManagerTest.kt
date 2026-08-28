package com.arduinomobileworkshop.rp2040

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.arduinomobileworkshop.usb.UsbSerialManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [RP2040Manager]. Covers the Raspberry Pi descriptor-table
 * constants and USB-device filtering (BOOTSEL bootloader vs application
 * serial interface vs unrelated devices) using mocked USB types so no
 * hardware or Robolectric runtime is required.
 */
class RP2040ManagerTest {

    private lateinit var usbManager: UsbManager
    private lateinit var usbSerialManager: UsbSerialManager
    private lateinit var manager: RP2040Manager
    private lateinit var picoBoot: UsbDevice
    private lateinit var picoSerial: UsbDevice
    private lateinit var other: UsbDevice

    @Before
    fun setUp() {
        usbManager = mock()
        usbSerialManager = mock()
        picoBoot = mock()
        picoSerial = mock()
        other = mock()
        whenever(picoBoot.vendorId).thenReturn(0x2E8A)
        whenever(picoBoot.productId).thenReturn(RP2040Manager.RP2040_PID_BOOTLOADER)
        whenever(picoBoot.deviceName).thenReturn("PicoBoot")
        whenever(picoSerial.vendorId).thenReturn(0x2E8A)
        whenever(picoSerial.productId).thenReturn(RP2040Manager.RP2040_PID_SERIAL)
        whenever(picoSerial.deviceName).thenReturn("PicoSerial")
        whenever(other.vendorId).thenReturn(0x1234)
        whenever(other.productId).thenReturn(0x5678)
        whenever(other.deviceName).thenReturn("Other")
        whenever(usbManager.deviceList).thenReturn(
            mapOf("a" to picoBoot, "b" to picoSerial, "c" to other)
        )
        manager = RP2040Manager(usbManager, usbSerialManager)
    }

    @Test
    fun rp2040DescriptorConstantsAreStable() {
        assertEquals(0x2E8A, RP2040Manager.RP2040_VID)
        assertEquals(0x0003, RP2040Manager.RP2040_PID_BOOTLOADER)
        assertEquals(0x000B, RP2040Manager.RP2040_PID_SERIAL)
        assertEquals(0x0A324655, RP2040Manager.UF2_MAGIC_START)
        assertEquals(4096, RP2040Manager.FLASH_PAGE_SIZE)
        assertEquals(65536, RP2040Manager.FLASH_SECTOR_SIZE)
        assertEquals(2097152, RP2040Manager.FLASH_TOTAL_SIZE)
    }

    @Test
    fun scanForDevicesKeepsOnlyRp2040Vid() {
        val devices = manager.scanForDevices()
        assertEquals(2, devices.size)
        assertTrue(devices.all { it.vendorId == 0x2E8A })
    }

    @Test
    fun scanForDevicesExcludesUnrelatedVid() {
        val devices = manager.scanForDevices()
        assertTrue(devices.none { it.deviceName == "Other" })
    }

    @Test
    fun scanForBootloaderDevicesReturnsOnlyBootsel() {
        val boot = manager.scanForBootloaderDevices()
        assertEquals(1, boot.size)
        assertEquals(RP2040Manager.RP2040_PID_BOOTLOADER, boot[0].productId)
    }

    @Test
    fun scanForDevicesEmptyWhenHostSeesNoUsbDevices() {
        whenever(usbManager.deviceList).thenReturn(emptyMap())
        assertTrue(manager.scanForDevices().isEmpty())
    }
}
