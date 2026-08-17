package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/**
 * Manages USB serial connections for Arduino devices
 */
class UsbSerialManager(private val context: Context) {
    
    private var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var connectedDevice: UsbDevice? = null
    private var isConnected = false
    
    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    /**
     * Open connection to a USB device
     */
    fun openConnection(device: UsbDevice): Boolean {
        return try {
            // Check if we already have a connection
            if (isConnected && connectedDevice == device) {
                return true
            }
            
            // Close existing connection
            closeConnection()
            
            // Find the serial driver for this device
            val prober = UsbSerialProber.getDefaultProber()
            val driver: UsbSerialDriver? = prober.probeDevice(device)
            
            if (driver == null) {
                return false
            }
            
            // Get the first available port
            usbSerialPort = driver.ports[0]
            connectedDevice = device
            
            // Open the connection
            usbSerialPort?.open(usbManager)
            usbSerialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            isConnected = true
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Close current USB connection
     */
    fun closeConnection() {
        try {
            usbSerialPort?.close()
            usbSerialPort = null
            connectedDevice = null
            isConnected = false
        } catch (e: IOException) {
            // Ignore
        }
    }
    
    /**
     * Write data to the USB serial port
     */
    fun writeData(data: ByteArray): Boolean {
        return try {
            if (!isConnected || usbSerialPort == null) {
                return false
            }
            usbSerialPort?.write(data, 1000)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Read data from the USB serial port
     */
    fun readData(buffer: ByteArray, timeout: Int): Int {
        return try {
            if (!isConnected || usbSerialPort == null) {
                return -1
            }
            usbSerialPort?.read(buffer, timeout) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Get list of available USB serial devices
     */
    fun getAvailableDevices(): List<UsbDevice> {
        val devices = mutableListOf<UsbDevice>()
        val deviceList = usbManager.deviceList
        val prober = UsbSerialProber.getDefaultProber()
        
        for (device in deviceList.values) {
            val driver = prober.probeDevice(device)
            if (driver != null) {
                devices.add(device)
            }
        }
        
        return devices
    }
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Get connected device
     */
    fun getConnectedDevice(): UsbDevice? = connectedDevice
    
    /**
     * Set baud rate
     */
    fun setBaudRate(baudRate: Int): Boolean {
        return try {
            if (!isConnected || usbSerialPort == null) {
                return false
            }
            usbSerialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        } catch (e: Exception) {
            false
        }
    }
}