package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialManager(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var connectedDevice: UsbDevice? = null

    fun openConnection(device: UsbDevice): Boolean {
        if (connectedDevice == device && usbSerialPort != null && usbConnection != null) return true
        closeConnection()

        val driver: UsbSerialDriver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return false
        val port = driver.ports.firstOrNull() ?: return false
        val connection = usbManager.openDevice(device) ?: return false

        return try {
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbConnection = connection
            usbSerialPort = port
            connectedDevice = device
            true
        } catch (_: Exception) {
            try { port.close() } catch (_: Exception) {}
            connection.close()
            false
        }
    }

    fun closeConnection() {
        try { usbSerialPort?.close() } catch (_: IOException) {}
        try { usbConnection?.close() } catch (_: Exception) {}
        usbSerialPort = null
        usbConnection = null
        connectedDevice = null
    }

    fun writeData(data: ByteArray): Boolean {
        return try {
            val port = usbSerialPort ?: return false
            port.write(data, 1000)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readData(buffer: ByteArray, timeout: Int): Int = try {
        usbSerialPort?.read(buffer, timeout) ?: -1
    } catch (_: Exception) { -1 }

    fun getAvailableDevices(): List<UsbDevice> = usbManager.deviceList.values.filter {
        UsbSerialProber.getDefaultProber().probeDevice(it) != null
    }

    fun isConnected(): Boolean = usbSerialPort != null && usbConnection != null
    fun getConnectedDevice(): UsbDevice? = connectedDevice
    fun getUsbSerialPort(): UsbSerialPort? = usbSerialPort

    fun setBaudRate(baudRate: Int): Boolean {
        return try {
            val port = usbSerialPort ?: return false
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        } catch (_: Exception) {
            false
        }
    }
}
