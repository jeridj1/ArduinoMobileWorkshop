package com.arduinomobileworkshop.app.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class UsbDeviceReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AMW_USB_Receiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                device?.let { onDeviceAttached(context, it) }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                device?.let { onDeviceDetached(context, it) }
            }
        }
    }
    
    private fun onDeviceAttached(context: Context, device: UsbDevice) {
        Log.d(TAG, "Device attached: ${device.deviceName}")
    }
    
    private fun onDeviceDetached(context: Context, device: UsbDevice) {
        Log.d(TAG, "Device detached: ${device.deviceName}")
    }
}
