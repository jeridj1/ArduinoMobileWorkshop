package com.arduinomobileworkshop.usb

import android.content.Context
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
    }

    val androidUsbManager: AndroidUsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as AndroidUsbManager
    }

    val usbSerialManager: UsbSerialManager by lazy {
        UsbSerialManager(context)
    }

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

    fun initialize() {
        try {
            scanForDevices()
            Log.d(TAG, "USB Manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize USB manager", e)
        }
    }

    fun scanForDevices() {
        try {
            val prober = UsbSerialProber.getDefaultProber()
            connectedDevices.clear()
            for (device in androidUsbManager.deviceList.values) {
                val driver = prober.probeDevice(device)
                connectedDevices[device.deviceName] = UsbDeviceInfo(device, driver, null, false)
            }
            notifyDeviceListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan for USB devices", e)
        }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbSerialManager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        usbSerialManager.requestPermission(device)
    }

    fun connectToDevice(device: UsbDevice): Boolean {
        return try {
            val deviceInfo = connectedDevices[device.deviceName] ?: return false
            if (deviceInfo.isConnected) return true
            if (!usbSerialManager.hasPermission(device)) {
                usbSerialManager.requestPermission(device)
                return false
            }
            val success = usbSerialManager.openConnection(device)
            if (success) {
                deviceInfo.isConnected = true
                notifyConnectionStateChanged(device, true)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to device", e)
            false
        }
    }

    fun disconnectFromDevice(device: UsbDevice): Boolean {
        return try {
            val deviceInfo = connectedDevices[device.deviceName] ?: return false
            usbSerialManager.closeConnection()
            deviceInfo.isConnected = false
            notifyConnectionStateChanged(device, false)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception disconnecting", e)
            false
        }
    }

    fun onPermissionGranted(device: UsbDevice) {
        connectedDevices[device.deviceName]?.let { info ->
            if (usbSerialManager.openConnection(device)) {
                info.isConnected = true
                notifyConnectionStateChanged(device, true)
            }
        }
    }

    fun getConnectedDevices(): List<UsbDeviceInfo> = connectedDevices.values.filter { it.isConnected }
    fun getAvailableDevices(): List<UsbDeviceInfo> = connectedDevices.values.toList()
    fun getDevice(deviceName: String): UsbDeviceInfo? = connectedDevices[deviceName]
    fun isDeviceConnected(deviceName: String): Boolean = connectedDevices[deviceName]?.isConnected == true

    fun writeData(data: ByteArray): Boolean = usbSerialManager.writeData(data)
    fun writeString(data: String): Boolean = writeData(data.toByteArray())
    fun readData(buffer: ByteArray, timeout: Int): Int = usbSerialManager.readData(buffer, timeout)

    fun readLine(timeout: Int = 1000): String? {
        val buffer = ByteArray(1024)
        val bytesRead = readData(buffer, timeout)
        return if (bytesRead > 0) String(buffer, 0, bytesRead) else null
    }

    fun setBaudRate(baudRate: Int): Boolean = usbSerialManager.setBaudRate(baudRate)

    fun setDtr(dtr: Boolean): Boolean = try {
        usbSerialManager.getUsbSerialPort()?.dtr = dtr
        true
    } catch (_: Exception) { false }

    fun setRts(rts: Boolean): Boolean = try {
        usbSerialManager.getUsbSerialPort()?.rts = rts
        true
    } catch (_: Exception) { false }

    fun addDeviceListener(listener: UsbDeviceListener) {
        if (!deviceListeners.contains(listener)) deviceListeners.add(listener)
    }

    fun removeDeviceListener(listener: UsbDeviceListener) {
        deviceListeners.remove(listener)
    }

    fun onDeviceAttached(device: UsbDevice) {
        scanForDevices()
        connectedDevices[device.deviceName]?.let { info ->
            deviceListeners.forEach { it.onDeviceAttached(device, info) }
        }
    }

    fun onDeviceDetached(device: UsbDevice) {
        connectedDevices.remove(device.deviceName)
        deviceListeners.forEach { it.onDeviceDetached(device) }
        if (usbSerialManager.getConnectedDevice()?.deviceName == device.deviceName) {
            usbSerialManager.closeConnection()
        }
    }

    private fun notifyDeviceListeners() {
        deviceListeners.forEach { listener ->
            connectedDevices.values.filter { it.isConnected }.forEach { info ->
                listener.onDeviceAttached(info.device, info)
            }
        }
    }

    private fun notifyConnectionStateChanged(device: UsbDevice, isConnected: Boolean) {
        deviceListeners.forEach { it.onConnectionStateChanged(device, isConnected) }
    }

    fun cleanup() {
        usbSerialManager.closeConnection()
        deviceListeners.clear()
        connectedDevices.clear()
    }
}
