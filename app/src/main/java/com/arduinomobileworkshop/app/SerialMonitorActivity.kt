package com.arduinomobileworkshop.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.usb.UsbSerialManager

class SerialMonitorActivity : AppCompatActivity() {
    private lateinit var usbSerialManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbSerialManager = UsbSerialManager(this)
    }

    override fun onDestroy() {
        usbSerialManager.closeConnection()
        super.onDestroy()
    }
}
