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

/**
 * High-level facade over the Android USB Host stack and the serial driver
 * library. Discovery, permission, attach/detach bookkeeping and the serial
 * connection lifecycle are funneled through here so the rest of the app never
 * has to touch [android.hardware.usb.UsbManager] or the driver classes directly.
 */
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

    private val connectedDevices: MutableMap<String, UsbDeviceInfo> = mutableMapOf()
    private val deviceListeners: MutableList<UsbDeviceListener> = mutableListOf()

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
        } catch (e: Exception) {
            Log.e(TAG, "USB scan failed", e)
        }
    }

    fun getSerialDevices(): List<UsbDevice> =
        UsbSerialProber.getDefaultProber().findAllDrivers(androidUsbManager).map { it.device }

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

    /**
     * Opens a serial connection to [device] at [baudRate] and returns a
     * listener-facing [SerialPortManager] backed by [UsbSerialManager]'s
     * background SerialInputOutputManager thread. Returns null if permission
     * is missing or the connection could not be opened.
     */
    fun openSerialPort(device: UsbDevice, baudRate: Int): SerialPortManager? {
        if (!hasPermission(device)) {
            requestPermission(device)
            Log.w(TAG, "openSerialPort: permission not granted for ${device.deviceName}")
            return null
        }
        val ok = usbSerialManager.openConnection(device, baudRate)
        if (!ok) {
            Log.e(TAG, "openSerialPort: failed to open ${device.deviceName}")
            return null
        }
        return SerialPortManager(usbSerialManager, device)
    }

    fun connectToDevice(device: UsbDevice): Boolean {
        if (!hasPermission(device)) return false
        return try {
            val success = usbSerialManager.openConnection(device)
            connectedDevices[device.deviceName]?.isConnected = success
            notifyConnectionStateChanged(device, success)
            success
        } catch (e: Exception) {
            Log.e(TAG, "connectToDevice failed", e)
            false
        }
    }

    fun disconnectFromDevice(device: UsbDevice): Boolean {
        return try {
            usbSerialManager.closeConnection()
            connectedDevices[device.deviceName]?.isConnected = false
            notifyConnectionStateChanged(device, false)
            true
        } catch (e: Exception) {
            false
        }
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
    fun setDtr(dtr: Boolean): Boolean = usbSerialManager.setDtr(dtr)
    fun setRts(rts: Boolean): Boolean = usbSerialManager.setRts(rts)
    fun getUsbSerialManager(): UsbSerialManager = usbSerialManager
    fun getAndroidUsbManager(): AndroidUsbManager = androidUsbManager

    fun addDeviceListener(listener: UsbDeviceListener) {
        if (!deviceListeners.contains(listener)) deviceListeners.add(listener)
    }
    fun removeDeviceListener(listener: UsbDeviceListener) { deviceListeners.remove(listener) }

    /** Called by [com.arduinomobileworkshop.app.usb.UsbDeviceReceiver] on attach. */
    fun onDeviceAttached(device: UsbDevice) {
        scanForDevices()
        connectedDevices[device.deviceName]?.let { info ->
            deviceListeners.forEach { it.onDeviceAttached(device, info) }
        }
    }

    /** Called by [com.arduinomobileworkshop.app.usb.UsbDeviceReceiver] on detach. */
    fun onDeviceDetached(device: UsbDevice) {
        connectedDevices.remove(device.deviceName)
        deviceListeners.forEach { it.onDeviceDetached(device) }
        if (usbSerialManager.getConnectedDevice()?.deviceName == device.deviceName) {
            usbSerialManager.closeConnection()
        }
    }

    private fun notifyDeviceListeners() {
        connectedDevices.values.forEach { info ->
            deviceListeners.forEach { it.onDeviceAttached(info.device, info) }
        }
    }

    private fun notifyConnectionStateChanged(device: UsbDevice, connected: Boolean) {
        deviceListeners.forEach { it.onConnectionStateChanged(device, connected) }
    }

    fun cleanup() {
        usbSerialManager.closeConnection()
        deviceListeners.clear()
        connectedDevices.clear()
    }
}
