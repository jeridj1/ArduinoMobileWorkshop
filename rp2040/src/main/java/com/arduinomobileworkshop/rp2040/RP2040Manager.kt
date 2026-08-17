package com.arduinomobileworkshop.rp2040

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.arduinomobileworkshop.usb.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages RP2040 device operations including:
 * - Device detection and connection
 * - UF2 bootloader mode
 * - Direct serial programming
 * - Logic analyzer mode
 */
class RP2040Manager(
    private val usbManager: UsbManager,
    private val usbSerialManager: UsbSerialManager
) {
    
    companion object {
        // Raspberry Pi Pico (RP2040) USB VID and PID
        const val RP2040_VID = 0x2E8A
        const val RP2040_PID_BOOTLOADER = 0x000A
        const val RP2040_PID_SERIAL = 0x000B
        
        // UF2 file magic numbers
        const val UF2_MAGIC_START = 0x0A324655
        const val UF2_MAGIC_END = 0x0A324655
        
        // RP2040 flash parameters
        const val FLASH_PAGE_SIZE = 4096 // 4KB
        const val FLASH_SECTOR_SIZE = 4096 * 16 // 64KB
        const val FLASH_TOTAL_SIZE = 2 * 1024 * 1024 // 2MB
    }
    
    private var connectedDevice: UsbDevice? = null
    private var isInBootloaderMode = false
    private var isLogicAnalyzerMode = false
    
    /**
     * Scan for connected RP2040 devices
     */
    fun scanForDevices(): List<UsbDevice> {
        val deviceList = usbManager.deviceList
        return deviceList.values.filter { device ->
            device.vendorId == RP2040_VID && 
            (device.productId == RP2040_PID_BOOTLOADER || device.productId == RP2040_PID_SERIAL)
        }
    }
    
    /**
     * Connect to an RP2040 device
     */
    fun connectToDevice(device: UsbDevice): Boolean {
        return try {
            connectedDevice = device
            usbSerialManager.openConnection(device)
            // Check if in bootloader mode
            isInBootloaderMode = device.productId == RP2040_PID_BOOTLOADER
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Disconnect from current device
     */
    fun disconnect() {
        usbSerialManager.closeConnection()
        connectedDevice = null
        isInBootloaderMode = false
        isLogicAnalyzerMode = false
    }
    
    /**
     * Check if device is in bootloader mode
     */
    fun isInBootloader(): Boolean = isInBootloaderMode
    
    /**
     * Enter UF2 bootloader mode (for programming)
     */
    suspend fun enterBootloaderMode(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Send the command to enter bootloader mode
            usbSerialManager.writeData(byteArrayOf(0x00, 0x01))
            Thread.sleep(1000) // Wait for device to reconnect
            
            // Re-scan for devices
            val devices = scanForDevices()
            if (devices.isNotEmpty()) {
                connectedDevice = devices.first()
                isInBootloaderMode = true
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    /**
     * Program firmware using UF2 format
     */
    suspend fun programFirmware(uf2Data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isInBootloaderMode) {
            return@withContext false
        }
        
        try {
            // UF2 blocks are 256 bytes each
            val blockSize = 256
            for (i in 0 until uf2Data.size step blockSize) {
                val end = minOf(i + blockSize, uf2Data.size)
                val block = uf2Data.copyOfRange(i, end)
                
                // Send block to device
                usbSerialManager.writeData(block)
                Thread.sleep(10) // Small delay between blocks
            }
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    /**
     * Enter logic analyzer mode
     */
    suspend fun enterLogicAnalyzerMode(
        sampleRate: Int = 1000000, // 1 MHz default
        channels: Int = 8
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Send command to enter logic analyzer mode
            val command = byteArrayOf(
                0x02.toByte(), // Logic analyzer mode command
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
    
    /**
     * Exit logic analyzer mode
     */
    suspend fun exitLogicAnalyzerMode(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = byteArrayOf(0x03.toByte()) // Exit logic analyzer mode
            usbSerialManager.writeData(command)
            Thread.sleep(500)
            isLogicAnalyzerMode = false
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    /**
     * Start logic analyzer capture
     */
    suspend fun startCapture(): Boolean = withContext(Dispatchers.IO) {
        if (!isLogicAnalyzerMode) {
            return@withContext false
        }
        
        try {
            val command = byteArrayOf(0x04.toByte()) // Start capture
            usbSerialManager.writeData(command)
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    /**
     * Stop logic analyzer capture and get data
     */
    suspend fun stopCapture(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isLogicAnalyzerMode) {
            return@withContext null
        }
        
        try {
            val command = byteArrayOf(0x05.toByte()) // Stop capture
            usbSerialManager.writeData(command)
            Thread.sleep(100)
            
            // Read captured data
            // Note: In actual implementation, this would read the capture buffer
            // For now, return a placeholder
            return@withContext ByteArray(0)
        } catch (e: Exception) {
            return@withContext null
        }
    }
    
    /**
     * Get current device info
     */
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