package com.arduinomobileworkshop.rp2040

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.arduinomobileworkshop.usb.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RP2040Manager(
    private val usbManager: UsbManager,
    private val usbSerialManager: UsbSerialManager
) {
    
    companion object {
        const val RP2040_VID = 0x2E8A
        const val RP2040_PID_BOOTLOADER = 0x000A
        const val RP2040_PID_SERIAL = 0x000B
        
        const val UF2_MAGIC_START = 0x0A324655
        
        const val FLASH_PAGE_SIZE = 4096
        const val FLASH_SECTOR_SIZE = 65536
        const val FLASH_TOTAL_SIZE = 2097152
    }
    
    private var connectedDevice: UsbDevice? = null
    private var isInBootloaderMode = false
    private var isLogicAnalyzerMode = false
    
    /**
     * Enumerates physical RP2040 devices straight from the Android USB
     * host descriptor table. A device is recognised when its vendor id is
     * the Raspberry Pi VID (0x2E8A) and its product id is either the UF2
     * bootloader (0x000A) or the application serial interface (0x000B).
     */
    fun scanForDevices(): List<UsbDevice> {
        val deviceList = usbManager.deviceList
        return deviceList.values.filter { device ->
            device.vendorId == RP2040_VID && 
            (device.productId == RP2040_PID_BOOTLOADER || device.productId == RP2040_PID_SERIAL)
        }
    }
    
    fun connectToDevice(device: UsbDevice): Boolean {
        return try {
            connectedDevice = device
            usbSerialManager.openConnection(device)
            isInBootloaderMode = device.productId == RP2040_PID_BOOTLOADER
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun disconnect() {
        usbSerialManager.closeConnection()
        connectedDevice = null
        isInBootloaderMode = false
        isLogicAnalyzerMode = false
    }
    
    fun isInBootloader(): Boolean = isInBootloaderMode
    
    suspend fun enterBootloaderMode(): Boolean = withContext(Dispatchers.IO) {
        try {
            usbSerialManager.writeData(byteArrayOf(0x00, 0x01))
            Thread.sleep(1000)
            
            // The device re-enumerates as the UF2 bootloader; drop the now-stale
            // serial handle and re-open against the freshly discovered device.
            usbSerialManager.closeConnection()
            Thread.sleep(500)
            
            val devices = scanForDevices()
            val bootDevice = devices.firstOrNull { it.productId == RP2040_PID_BOOTLOADER }
            if (bootDevice != null) {
                connectedDevice = bootDevice
                isInBootloaderMode = true
                usbSerialManager.openConnection(bootDevice)
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    suspend fun programFirmware(uf2Data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isInBootloaderMode) {
            return@withContext false
        }
        
        try {
            val blockSize = 256
            for (i in 0 until uf2Data.size step blockSize) {
                val end = minOf(i + blockSize, uf2Data.size)
                val block = uf2Data.copyOfRange(i, end)
                usbSerialManager.writeData(block)
                Thread.sleep(10)
            }
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    suspend fun enterLogicAnalyzerMode(
        sampleRate: Int = 1000000,
        channels: Int = 8
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = byteArrayOf(
                0x02.toByte(),
                (sampleRate and 0xFF).toByte(),
                ((sampleRate shr 8) and 0xFF).toByte(),
                ((sampleRate shr 16) and 0xFF).toByte(),
                channels.toByte()
            )
            
            usbSerialManager.writeData(command)
            Thread.sleep(500)
            
            isLogicAnalyzerMode = true
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    suspend fun exitLogicAnalyzerMode(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = byteArrayOf(0x03.toByte())
            usbSerialManager.writeData(command)
            Thread.sleep(500)
            isLogicAnalyzerMode = false
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    suspend fun startCapture(): Boolean = withContext(Dispatchers.IO) {
        if (!isLogicAnalyzerMode) {
            return@withContext false
        }
        
        try {
            val command = byteArrayOf(0x04.toByte())
            usbSerialManager.writeData(command)
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    suspend fun stopCapture(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isLogicAnalyzerMode) {
            return@withContext null
        }
        
        try {
            val command = byteArrayOf(0x05.toByte())
            usbSerialManager.writeData(command)
            Thread.sleep(100)
            return@withContext ByteArray(0)
        } catch (e: Exception) {
            return@withContext null
        }
    }
    
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "connected" to (connectedDevice != null).toString(),
            "in_bootloader" to isInBootloaderMode.toString(),
            "in_logic_analyzer" to isLogicAnalyzerMode.toString(),
            "vendor_id" to (connectedDevice?.vendorId?.toString() ?: "N/A"),
            "product_id" to (connectedDevice?.productId?.toString() ?: "N/A")
        )
    }
}