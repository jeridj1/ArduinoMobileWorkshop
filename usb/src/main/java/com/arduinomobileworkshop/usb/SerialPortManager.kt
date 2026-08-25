package com.arduinomobileworkshop.usb

import android.hardware.usb.UsbDevice
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Listener-facing adapter that exposes a stable callback stream backed by
 * [UsbSerialManager]'s background SerialInputOutputManager thread.
 *
 * The terminal UI registers [SerialPortListener]s here; the actual read loop
 * lives in [UsbSerialManager]. This keeps Android USB internals out of the UI
 * layer while preserving the simple addListener / startReceiving / write / close
 * API that the serial monitor depends on.
 */
class SerialPortManager internal constructor(
    private val serialManager: UsbSerialManager,
    private val device: UsbDevice
) {

    interface SerialPortListener {
        fun onDataReceived(data: ByteArray)
        fun onError(error: String)
    }

    private val listeners = CopyOnWriteArrayList<SerialPortListener>()
    private var receiving = false

    private val bridge = object : UsbSerialManager.Listener {
        override fun onNewData(data: ByteArray) {
            listeners.forEach { it.onDataReceived(data) }
        }

        override fun onRunError(e: Exception) {
            listeners.forEach { it.onError(e.message ?: "Serial connection lost") }
        }
    }

    val connectedDevice: UsbDevice get() = device

    fun addListener(listener: SerialPortListener) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: SerialPortListener) { listeners.remove(listener) }

    /** Begins forwarding incoming bytes to all registered listeners. */
    fun startReceiving() {
        if (receiving) return
        receiving = true
        serialManager.setListener(bridge)
    }

    /** Stops forwarding incoming bytes (the underlying port stays open). */
    fun stopReceiving() {
        if (!receiving) return
        receiving = false
        serialManager.setListener(null)
    }

    /** Returns the number of bytes written, or -1 on failure. */
    fun write(data: ByteArray): Int =
        if (serialManager.writeData(data)) data.size else -1

    /** Stops receiving, clears listeners and closes the underlying serial connection. */
    fun close() {
        stopReceiving()
        listeners.clear()
        serialManager.closeConnection()
    }
}
