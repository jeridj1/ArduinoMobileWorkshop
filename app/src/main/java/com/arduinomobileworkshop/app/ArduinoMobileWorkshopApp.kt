package com.arduinomobileworkshop.app

import android.app.Application
import android.util.Log

class ArduinoMobileWorkshopApp : Application() {
    companion object {
        private const val TAG = "AMW_App"
        lateinit var instance: ArduinoMobileWorkshopApp
            private set
    }
    
    val workspaceManager: com.arduinomobileworkshop.workspace.WorkspaceManager by lazy {
        com.arduinomobileworkshop.workspace.WorkspaceManager(this)
    }
    
    val toolchainManager: com.arduinomobileworkshop.toolchain.ToolchainManager by lazy {
        com.arduinomobileworkshop.toolchain.ToolchainManager(this)
    }
    
    val usbManager: com.arduinomobileworkshop.usb.UsbManager by lazy {
        com.arduinomobileworkshop.usb.UsbManager(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Arduino Mobile Workshop starting")
        workspaceManager.initialize()
        toolchainManager.initialize()
        usbManager.initialize()
    }
}
