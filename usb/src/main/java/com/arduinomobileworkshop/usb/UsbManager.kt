package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
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
    
    val androidUsbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    val usbSerialManager: UsbSerialManager by lazy {
        UsbSerialManager(context)
    }
    
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
            val deviceList = androidUsbManager.deviceList
            val prober = UsbSerialProber.getDefaultProber()
            connectedDevices.clear()
            for (device in deviceList.values) {
                val driver = prober.probeDevice(device)
                val deviceInfo = UsbDeviceInfo(
                    device = device,
                    driver = driver,
                    port = null,
                    isConnected = false
                )
                connectedDevices[device.deviceName] = deviceInfo
                Log.d(TAG, "Found USB device: " + device.deviceName)
            }
            notifyDeviceListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan for USB devices", e)
        }
    }
    
    fun connectToDevice(device: UsbDevice): Boolean {
        try {
            val deviceInfo = connectedDevices[device.deviceName]
            if (deviceInfo == null) {
                Log.e(TAG, "Device not found: " + device.deviceName)
                return false
            }
            if (deviceInfo.isConnected) {
                return true
            }
            val success = usbSerialManager.openConnection(device)
            if (success) {
                deviceInfo.isConnected = true
                connectedDevices[device.deviceName] = deviceInfo
                Log.d(TAG, "Connected to: " + device.deviceName)
                notifyConnectionStateChanged(device, true)
                return true
            } else {
                Log.e(TAG, "Failed to connect to: " + device.deviceName)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to device", e)
            return false
        }
    }
    
    fun disconnectFromDevice(device: UsbDevice): Boolean {
        try {
            val deviceInfo = connectedDevices[device.deviceName]
            if (deviceInfo == null) {
                return false
            }
            usbSerialManager.closeConnection()
            deviceInfo.isConnected = false
            connectedDevices[device.deviceName] = deviceInfo
            Log.d(TAG, "Disconnected from: " + device.deviceName)
            notifyConnectionStateChanged(device, false)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception disconnecting", e)
            return false
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
        if (bytesRead > 0) {
            return String(buffer, 0, bytesRead)
        }
        return null
    }
    
    fun setBaudRate(baudRate: Int): Boolean = usbSerialManager.setBaudRate(baudRate)
    
    fun setDtr(dtr: Boolean): Boolean {
        return try {
            usbSerialManager.getUsbSerialPort()?.dtr = dtr
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun setRts(rts: Boolean): Boolean {
        return try {
            usbSerialManager.getUsbSerialPort()?.rts = rts
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getUsbSerialManager(): UsbSerialManager = usbSerialManager
    fun getAndroidUsbManager(): UsbManager = androidUsbManager
    
    fun addDeviceListener(listener: UsbDeviceListener) {
        if (!deviceListeners.contains(listener)) {
            deviceListeners.add(listener)
        }
    }
    
    fun removeDeviceListener(listener: UsbDeviceListener) {
        deviceListeners.remove(listener)
    }
    
    fun onDeviceAttached(device: UsbDevice) {
        scanForDevices()
        deviceListeners.forEach { 
            connectedDevices[device.deviceName]?.let { deviceInfo ->
                it.onDeviceAttached(device, deviceInfo)
            }
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
            connectedDevices.values.forEach { deviceInfo ->
                if (deviceInfo.isConnected) {
                    listener.onDeviceAttached(deviceInfo.device, deviceInfo)
                }
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