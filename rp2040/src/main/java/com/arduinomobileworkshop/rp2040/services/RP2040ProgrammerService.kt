package com.arduinomobileworkshop.rp2040.services

import android.app.Service
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Binder
import android.os.IBinder
import com.arduinomobileworkshop.rp2040.RP2040Manager
import com.arduinomobileworkshop.usb.UsbSerialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RP2040ProgrammerService : Service() {
    
    private val binder = LocalBinder()
    private lateinit var rp2040Manager: RP2040Manager
    private lateinit var usbSerialManager: UsbSerialManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var isProgramming = false
    private var programmingProgress = 0
    private var currentFile: File? = null
    private var programmingCallback: ((Int, String) -> Unit)? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): RP2040ProgrammerService = this@RP2040ProgrammerService
    }
    
    override fun onCreate() {
        super.onCreate()
        usbSerialManager = UsbSerialManager(this)
        rp2040Manager = RP2040Manager(
            getSystemService(USB_SERVICE) as android.hardware.usb.UsbManager,
            usbSerialManager
        )
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        cancelProgramming()
    }
    
    /**
     * Real RP2040 enumeration: delegates to [RP2040Manager.scanForDevices]
     * which reads the USB descriptor table and filters by the Raspberry Pi
     * vendor id (0x2E8A).
     */
    fun scanForDevices(): List<UsbDevice> = rp2040Manager.scanForDevices()
    
    fun getRp2040ManagerInfo(): Map<String, String> = rp2040Manager.getDeviceInfo()
    
    fun disconnect() {
        rp2040Manager.disconnect()
    }
    
    /**
     * High-level single-device programming pipeline: connect, enter the UF2
     * bootloader, then stream the file. The callback receives (progress,
     * message, terminal); terminal is true once the device is done (success
     * or failure) so callers can advance to the next device.
     */
    fun programDevice(
        device: UsbDevice,
        file: File,
        callback: (progress: Int, message: String, terminal: Boolean) -> Unit
    ) {
        if (isProgramming) {
            callback(0, "Already programming", true)
            return
        }
        serviceScope.launch {
            try {
                isProgramming = true
                callback(0, "Connecting to " + device.deviceName, false)
                val connected = rp2040Manager.connectToDevice(device)
                if (!connected) {
                    callback(0, "Connect failed", true)
                    isProgramming = false
                    return@launch
                }
                callback(0, "Entering bootloader mode...", false)
                val boot = rp2040Manager.enterBootloaderMode()
                if (!boot) {
                    rp2040Manager.disconnect()
                    callback(0, "Bootloader failed", true)
                    isProgramming = false
                    return@launch
                }
                val uf2Data = withContext(Dispatchers.IO) { file.readBytes() }
                callback(0, "Starting programming...", false)
                val success = rp2040Manager.programFirmware(uf2Data)
                rp2040Manager.disconnect()
                if (success) callback(100, "Programming complete!", true)
                else callback(0, "Programming failed", true)
                isProgramming = false
            } catch (ex: Exception) {
                try { rp2040Manager.disconnect() } catch (_: Exception) {}
                callback(0, "Error: " + ex.message, true)
                isProgramming = false
            }
        }
    }
    
    fun programFile(file: File, callback: (Int, String) -> Unit) {
        if (isProgramming) {
            callback(0, "Already programming")
            return
        }
        
        currentFile = file
        programmingCallback = callback
        
        serviceScope.launch {
            try {
                isProgramming = true
                programmingProgress = 0
                
                val uf2Data = withContext(Dispatchers.IO) {
                    file.readBytes()
                }
                
                if (!rp2040Manager.isInBootloader()) {
                    callback(0, "Device not in bootloader mode")
                    isProgramming = false
                    return@launch
                }
                
                callback(0, "Starting programming...")
                
                val success = rp2040Manager.programFirmware(uf2Data)
                
                if (success) {
                    programmingProgress = 100
                    callback(100, "Programming complete!")
                } else {
                    callback(0, "Programming failed")
                }
                
                isProgramming = false
                currentFile = null
                
            } catch (ex: Exception) {
                callback(0, "Error: " + ex.message)
                isProgramming = false
                currentFile = null
            }
        }
    }
    
    fun cancelProgramming() {
        isProgramming = false
        currentFile = null
        programmingCallback = null
        programmingProgress = 0
        
        serviceScope.launch {
            usbSerialManager.closeConnection()
        }
    }
    
    fun getProgrammingStatus(): Map<String, Any> {
        return mapOf(
            "is_programming" to isProgramming,
            "progress" to programmingProgress,
            "current_file" to (currentFile?.name ?: "N/A")
        )
    }
    
    fun enterBootloaderMode(callback: (Boolean, String) -> Unit) {
        serviceScope.launch {
            try {
                val success = rp2040Manager.enterBootloaderMode()
                callback(success, if (success) "Entered bootloader mode" else "Failed to enter bootloader mode")
            } catch (ex: Exception) {
                callback(false, "Error: " + ex.message)
            }
        }
    }
    
    fun verifyUf2File(file: File): Boolean {
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 32) return false
            
            val magicStart = (bytes[0].toInt() and 0xFF) or
                           ((bytes[1].toInt() and 0xFF) shl 8) or
                           ((bytes[2].toInt() and 0xFF) shl 16) or
                           ((bytes[3].toInt() and 0xFF) shl 24)
            
            magicStart == RP2040Manager.UF2_MAGIC_START
        } catch (ex: Exception) {
            false
        }
    }
}