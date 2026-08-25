package com.arduinomobileworkshop.app.ui

import com.arduinomobileworkshop.app.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.rp2040.services.LogicAnalyzerService

class LogicAnalyzerActivity : AppCompatActivity() {
    
    private var logicAnalyzerService: LogicAnalyzerService? = null
    private var isServiceBound = false
    private var isCapturing = false
    
    private lateinit var startCaptureButton: Button
    private lateinit var stopCaptureButton: Button
    private lateinit var sampleRateSpinner: Spinner
    private lateinit var channelsSpinner: Spinner
    private lateinit var statusTextView: TextView
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LogicAnalyzerService.LocalBinder
            logicAnalyzerService = binder.getService()
            isServiceBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            logicAnalyzerService = null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logic_analyzer)
        
        startCaptureButton = findViewById(R.id.logic_analyzer_start)
        stopCaptureButton = findViewById(R.id.logic_analyzer_stop)
        sampleRateSpinner = findViewById(R.id.logic_analyzer_sample_rate)
        channelsSpinner = findViewById(R.id.logic_analyzer_channels)
        statusTextView = findViewById(R.id.logic_analyzer_status)
        
        startCaptureButton.setOnClickListener {
            startCapture()
        }
        
        stopCaptureButton.setOnClickListener {
            stopCapture()
        }
        
        val intent = Intent(this, LogicAnalyzerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        stopCapture()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logic_analyzer, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun startCapture() {
        if (!isServiceBound || isCapturing) return
        
        val sampleRate = when (sampleRateSpinner.selectedItemPosition) {
            0 -> 100000
            1 -> 500000
            2 -> 1000000
            3 -> 2000000
            else -> 1000000
        }
        
        val channels = when (channelsSpinner.selectedItemPosition) {
            0 -> 4
            1 -> 8
            2 -> 16
            else -> 8
        }
        
        logicAnalyzerService?.configure(sampleRate, channels)
        
        logicAnalyzerService?.startCapture { capturedData ->
            runOnUiThread {
                val processedChannels = logicAnalyzerService?.processCaptureData(capturedData)
                if (processedChannels != null) {
                    statusTextView.text = "Captured " + capturedData.size + " bytes"
                }
            }
        }
        
        isCapturing = true
        updateUI()
     
   Toast.makeText(this, "Capture started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopCapture() {
        if (!isServiceBound || !isCapturing) return
        
        logicAnalyzerService?.stopCapture()
        isCapturing = false
        updateUI()
        Toast.makeText(this, "Capture stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        startCaptureButton.isEnabled = isServiceBound && !isCapturing
        stopCaptureButton.isEnabled = isServiceBound && isCapturing
        
        val config = logicAnalyzerService?.getConfiguration()
        if (config != null) {
            statusTextView.text = "Ready - " + config["sample_rate"] + "Hz, " + config["channels"] + " channels"
        } else {
            statusTextView.text = "Service not connected"
        }
    }
}