package com.arduinomobileworkshop.usb

import android.hardware.usb.UsbDevice
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class SerialPortManager internal constructor(
    private val port: UsbSerialPort,
    private val device: UsbDevice
) {
    interface SerialPortListener {
        fun onDataReceived(data: ByteArray)
        fun onError(error: String)
    }

    private val listeners = CopyOnWriteArrayList<SerialPortListener>()
    private val receiving = AtomicBoolean(false)
    private var receiveThread: Thread? = null

    fun addListener(listener: SerialPortListener) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: SerialPortListener) { listeners.remove(listener) }

    fun startReceiving() {
        if (!receiving.compareAndSet(false, true)) return
        receiveThread = Thread {
            val buffer = ByteArray(16384)
            while (receiving.get()) {
                try {
                    val count = port.read(buffer, 250)
                    if (count > 0) {
                        val data = buffer.copyOf(count)
                        listeners.forEach { it.onDataReceived(data) }
                    }
                } catch (t: Throwable) {
                    if (receiving.get()) listeners.forEach { it.onError(t.message ?: "Serial read failed") }
                    receiving.set(false)
                }
            }
        }.apply { name = "AMW-Serial-${device.deviceId}"; start() }
    }

    fun stopReceiving() {
        receiving.set(false)
        receiveThread?.interrupt()
        receiveThread = null
    }

    fun write(data: ByteArray): Int {
        return try { port.write(data, 2000) } catch (t: Throwable) {
            listeners.forEach { it.onError(t.message ?: "Serial write failed") }
            -1
        }
    }

    fun close() {
        stopReceiving()
        try { port.close() } catch (_: Throwable) { }
        listeners.clear()
    }
}
