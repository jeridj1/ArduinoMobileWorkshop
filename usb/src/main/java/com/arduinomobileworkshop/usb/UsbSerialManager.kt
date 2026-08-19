package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialManager(private val context: Context) {
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var connectedDevice: UsbDevice? = null
    private var isConnected = false

    fun openConnection(device: UsbDevice): Boolean {
        return try {
            if (isConnected && connectedDevice == device) return true
            closeConnection()
            val driver: UsbSerialDriver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return false
            if (driver.ports.isEmpty()) return false
            val port = driver.ports[0]
            port.open(usbManager)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbSerialPort = port
            connectedDevice = device
            isConnected = true
            true
        } catch (e: Exception) {
            closeConnection()
            false
        }
    }

    fun closeConnection() {
        try { usbSerialPort?.close() } catch (_: IOException) {}
        usbSerialPort = null
        connectedDevice = null
        isConnected = false
    }

    fun writeData(data: ByteArray): Boolean = try {
        if (!isConnected) false else { usbSerialPort?.write(data, 1000); true }
    } catch (_: Exception) { false }

    fun readData(buffer: ByteArray, timeout: Int): Int = try {
        if (!isConnected) -1 else usbSerialPort?.read(buffer, timeout) ?: -1
    } catch (_: Exception) { -1 }

    fun getAvailableDevices(): List<UsbDevice> = usbManager.deviceList.values.filter {
        UsbSerialProber.getDefaultProber().probeDevice(it) != null
    }

    fun isConnected(): Boolean = isConnected
    fun getConnectedDevice(): UsbDevice? = connectedDevice
    fun getUsbSerialPort(): UsbSerialPort? = usbSerialPort

    fun setBaudRate(baudRate: Int): Boolean = try {
        if (!isConnected) false else {
            usbSerialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        }
    } catch (_: Exception) { false }
}
