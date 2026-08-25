package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbManager as AndroidUsbManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [UsbSerialManager] covering the safe, disconnected-state
 * behaviour: the manager must reject I/O and reconfiguration cleanly when no
 * port is open, expose null/empty device state, and tolerate repeated
 * closeConnection()/shutdown() calls. Construction is exercised against a
 * mocked [Context] / [AndroidUsbManager] so no hardware or Robolectric runtime
 * is required.
 */
class UsbSerialManagerTest {

    private lateinit var context: Context
    private lateinit var androidUsbManager: AndroidUsbManager
    private lateinit var manager: UsbSerialManager

    @Before
    fun setUp() {
        context = mock()
        androidUsbManager = mock()
        whenever(context.getSystemService(Context.USB_SERVICE)).thenReturn(androidUsbManager)
        whenever(androidUsbManager.deviceList).thenReturn(emptyMap())
        manager = UsbSerialManager(context)
    }

    @Test
    fun isConnectedIsFalseByDefault() {
        assertFalse(manager.isConnected())
    }

    @Test
    fun writeDataFailsWhenDisconnected() {
        assertFalse(manager.writeData(byteArrayOf(0x41, 0x42, 0x43)))
    }

    @Test
    fun readDataReturnsMinusOneWhenDisconnected() {
        assertEquals(-1, manager.readData(ByteArray(8), 100))
    }

    @Test
    fun setBaudRateFailsWhenDisconnected() {
        assertFalse(manager.setBaudRate(9600))
    }

    @Test
    fun setParametersFailsWhenDisconnected() {
        assertFalse(manager.setParameters(9600, 8, 1, 0))
    }

    @Test
    fun connectedDeviceIsNullByDefault() {
        assertNull(manager.getConnectedDevice())
    }

    @Test
    fun serialPortIsNullByDefault() {
        assertNull(manager.getUsbSerialPort())
    }

    @Test
    fun availableDevicesIsEmptyWhenHostSeesNoUsbDevices() {
        assertTrue(manager.getAvailableDevices().isEmpty())
    }

    @Test
    fun closeConnectionIsIdempotent() {
        manager.closeConnection()
        manager.closeConnection()
        assertFalse(manager.isConnected())
    }

    @Test
    fun shutdownIsSafeWithoutAnOpenConnection() {
        manager.closeConnection()
        manager.shutdown()
    }

    @Test
    fun setListenerAcceptsNullAndConcreteListenerWithoutError() {
        manager.setListener(null)
        manager.setListener(object : UsbSerialManager.Listener {
            override fun onNewData(data: ByteArray) {}
            override fun onRunError(e: Exception) {}
        })
        manager.setListener(null)
    }
}
