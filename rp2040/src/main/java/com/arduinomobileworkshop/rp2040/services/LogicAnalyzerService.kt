package com.arduinomobileworkshop.rp2040.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.arduinomobileworkshop.rp2040.RP2040Manager
import com.arduinomobileworkshop.usb.UsbSerialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LogicAnalyzerService : Service() {
    
    private val binder = LocalBinder()
    private lateinit var rp2040Manager: RP2040Manager
    private lateinit var usbSerialManager: UsbSerialManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var isCapturing = false
    private var sampleRate = 1000000
    private var activeChannels = 8
    
    private var captureCallback: ((ByteArray) -> Unit)? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): LogicAnalyzerService = this@LogicAnalyzerService
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
        stopCapture()
    }
    
    fun configure(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.activeChannels = channels
    }
    
    fun startCapture(callback: (ByteArray) -> Unit) {
        if (isCapturing) return
        
        this.captureCallback = callback
        
        serviceScope.launch {
            val success = rp2040Manager.enterLogicAnalyzerMode(sampleRate, activeChannels)
            if (success) {
                isCapturing = true
                val started = rp2040Manager.startCapture()
                if (!started) {
                    isCapturing = false
                }
            }
        }
    }
    
    fun stopCapture() {
        if (!isCapturing) return
        
        serviceScope.launch {
            captureCallback = null
            isCapturing = false
            rp2040Manager.stopCapture()
            rp2040Manager.exitLogicAnalyzerMode()
        }
    }
    
    fun isCapturing(): Boolean = isCapturing
    
    fun getConfiguration(): Map<String, Any> {
        return mapOf(
            "sample_rate" to sampleRate,
            "channels" to activeChannels,
            "is_capturing" to isCapturing
        )
    }
    
    fun processCaptureData(data: ByteArray): List<LogicAnalyzerChannel> {
        val channels = mutableListOf<LogicAnalyzerChannel>()
        
        repeat(activeChannels) { channelIndex ->
            val channelData = mutableListOf<Boolean>()
            repeat(minOf(100, data.size)) {
                channelData.add(data[it % data.size].toInt() and (1 shl channelIndex) != 0)
            }
            channels.add(LogicAnalyzerChannel(channelIndex, channelData))
        }
        
        return channels
    }
}

data class LogicAnalyzerChannel(
    val channelIndex: Int,
    val samples: List<Boolean>
)