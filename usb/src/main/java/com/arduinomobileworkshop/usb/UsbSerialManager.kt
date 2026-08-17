package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialManager(private val context: Context) {
    
    private var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var connectedDevice: UsbDevice? = null
    private var isConnected = false
    
    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    fun openConnection(device: UsbDevice): Boolean {
        return try {
            if (isConnected && connectedDevice == device) {
                return true
            }
            
            closeConnection()
            
            val prober = UsbSerialProber.getDefaultProber()
            val driver: UsbSerialDriver? = prober.probeDevice(device)
            
            if (driver == null) {
                return false
            }
            
            usbSerialPort = driver.ports[0]
            connectedDevice = device
            
            usbSerialPort?.open(usbManager)
            usbSerialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            isConnected = true
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun closeConnection() {
        try {
            usbSerialPort?.close()
            usbSerialPort = null
            connectedDevice = null
            isConnected = false
        } catch (e: IOException) {
        }
    }
    
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
    
    fun isConnected(): Boolean = isConnected
    
    fun getConnectedDevice(): UsbDevice? = connectedDevice
    
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