package com.arduinomobileworkshop.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager as AndroidUsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class UsbManager(private val context: Context) {
    companion object {
        private const val TAG = "AMW_USB_Manager"
        const val ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_USB_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
        const val ACTION_USB_PERMISSION = "com.arduinomobileworkshop.app.USB_PERMISSION"
    }

    val androidUsbManager: AndroidUsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as AndroidUsbManager
    }
    val usbSerialManager: UsbSerialManager by lazy { UsbSerialManager(context) }

    private var connectedDevices: MutableMap<String, UsbDeviceInfo> = mutableMapOf()
    private var deviceListeners: MutableList<UsbDeviceListener> = mutableListOf()

    data class UsbDeviceInfo(
        val device: UsbDevice,
        val driver: UsbSerialDriver?,
        val port: UsbSerialPort?,
        var isConnected: Boolean = false
    )

    interface UsbDeviceListener {
        fun onDeviceAttached(device: UsbDevice, deviceInfo: UsbDeviceInfo)
        fun onDeviceDetached(device: UsbDevice)
        fun onConnectionStateChanged(device: UsbDevice, isConnected: Boolean)
    }

    fun initialize() { scanForDevices() }

    fun scanForDevices() {
        try {
            val prober = UsbSerialProber.getDefaultProber()
            connectedDevices.clear()
            for (device in androidUsbManager.deviceList.values) {
                val driver = prober.probeDevice(device)
                connectedDevices[device.deviceName] = UsbDeviceInfo(device, driver, null, false)
                Log.d(TAG, "Found USB device: ${device.deviceName}")
            }
            notifyDeviceListeners()
        } catch (e: Exception) { Log.e(TAG, "USB scan failed", e) }
    }

    fun getSerialDevices(): List<UsbDevice> = UsbSerialProber.getDefaultProber().findAllDrivers(androidUsbManager)
        .map { it.device }

    fun hasPermission(device: UsbDevice): Boolean = androidUsbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(
            context, device.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
        )
        androidUsbManager.requestPermission(device, permissionIntent)
    }

    fun openSerialPort(device: UsbDevice, baudRate: Int): SerialPortManager? {
        return try {
            if (!hasPermission(device)) return null
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return null
            val port = driver.ports.firstOrNull() ?: return null
            port.open(androidUsbManager)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            SerialPortManager(port, device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open serial port", e)
            null
        }
    }

    fun connectToDevice(device: UsbDevice): Boolean {
        if (!hasPermission(device)) return false
        return try {
            val success = usbSerialManager.openConnection(device)
            connectedDevices[device.deviceName]?.isConnected = success
            notifyConnectionStateChanged(device, success)
            success
        } catch (e: Exception) { false }
    }

    fun disconnectFromDevice(device: UsbDevice): Boolean {
        return try {
            usbSerialManager.closeConnection()
            connectedDevices[device.deviceName]?.isConnected = false
            notifyConnectionStateChanged(device, false)
            true
        } catch (e: Exception) { false }
    }

    fun getConnectedDevices(): List<UsbDeviceInfo> = connectedDevices.values.filter { it.isConnected }
    fun getAvailableDevices(): List<UsbDeviceInfo> = connectedDevices.values.toList()
    fun getDevice(deviceName: String): UsbDeviceInfo? = connectedDevices[deviceName]
    fun isDeviceConnected(deviceName: String): Boolean = connectedDevices[deviceName]?.isConnected ?: false
    fun writeData(data: ByteArray): Boolean = usbSerialManager.writeData(data)
    fun writeString(data: String): Boolean = writeData(data.toByteArray())
    fun readData(buffer: ByteArray, timeout: Int): Int = usbSerialManager.readData(buffer, timeout)
    fun readLine(timeout: Int = 1000): String? {
        val buffer = ByteArray(1024)
        val bytesRead = readData(buffer, timeout)
        return if (bytesRead > 0) String(buffer, 0, bytesRead) else null
    }
    fun setBaudRate(baudRate: Int): Boolean = usbSerialManager.setBaudRate(baudRate)
    fun setDtr(dtr: Boolean): Boolean = try { usbSerialManager.getUsbSerialPort()?.dtr = dtr; true } catch (_: Exception) { false }
    fun setRts(rts: Boolean): Boolean = try { usbSerialManager.getUsbSerialPort()?.rts = rts; true } catch (_: Exception) { false }
    fun getUsbSerialManager(): UsbSerialManager = usbSerialManager
    fun getAndroidUsbManager(): AndroidUsbManager = androidUsbManager

    fun addDeviceListener(listener: UsbDeviceListener) { if (!deviceListeners.contains(listener)) deviceListeners.add(listener) }
    fun removeDeviceListener(listener: UsbDeviceListener) { deviceListeners.remove(listener) }
    fun onDeviceAttached(device: UsbDevice) {
        scanForDevices()
        connectedDevices[device.deviceName]?.let { info -> deviceListeners.forEach { it.onDeviceAttached(device, info) } }
    }
    fun onDeviceDetached(device: UsbDevice) {
        connectedDevices.remove(device.deviceName)
        deviceListeners.forEach { it.onDeviceDetached(device) }
        if (usbSerialManager.getConnectedDevice()?.deviceName == device.deviceName) usbSerialManager.closeConnection()
    }
    private fun notifyDeviceListeners() {
        connectedDevices.values.forEach { info -> deviceListeners.forEach { it.onDeviceAttached(info.device, info) } }
    }
    private fun notifyConnectionStateChanged(device: UsbDevice, connected: Boolean) {
        deviceListeners.forEach { it.onConnectionStateChanged(device, connected) }
    }
    fun cleanup() { usbSerialManager.closeConnection(); deviceListeners.clear(); connectedDevices.clear() }
}
