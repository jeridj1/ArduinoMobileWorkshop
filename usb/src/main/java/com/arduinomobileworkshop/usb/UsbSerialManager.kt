package com.arduinomobileworkshop.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialManager(private val context: Context) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.arduinomobileworkshop.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var connectedDevice: UsbDevice? = null

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) return
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        usbManager.requestPermission(device, PendingIntent.getBroadcast(context, device.deviceId, intent, flags))
    }

    fun openConnection(device: UsbDevice): Boolean {
        if (!usbManager.hasPermission(device)) return false
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
        val port = usbSerialPort ?: return false
        return try {
            port.write(data, 1000)
            true
        } catch (_: Exception) { false }
    }

    fun readData(buffer: ByteArray, timeout: Int): Int {
        val port = usbSerialPort ?: return -1
        return try { port.read(buffer, timeout) } catch (_: Exception) { -1 }
    }

    fun getAvailableDevices(): List<UsbDevice> = usbManager.deviceList.values.filter {
        UsbSerialProber.getDefaultProber().probeDevice(it) != null
    }

    fun isConnected(): Boolean = usbSerialPort != null && usbConnection != null
    fun getConnectedDevice(): UsbDevice? = connectedDevice
    fun getUsbSerialPort(): UsbSerialPort? = usbSerialPort

    fun setBaudRate(baudRate: Int): Boolean {
        val port = usbSerialPort ?: return false
        return try {
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        } catch (_: Exception) { false }
    }
}
