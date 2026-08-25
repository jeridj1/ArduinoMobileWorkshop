package com.arduinomobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager as AndroidUsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core low-level USB serial connection manager.
 *
 * Owns a single open [UsbSerialPort] and drives it with a background
 * [SerialInputOutputManager] thread (provided by the usb-serial-for-android
 * library, which ships standard drivers for CH340, CP2102, FTDI and CDC/ACM
 * devices such as official Arduino boards).
 *
 * Incoming bytes are delivered to a registered [Listener] on the background IO
 * thread. Hardware detachment / connection-lost errors surface through
 * [Listener.onRunError] (posted to the main thread) and the connection is torn
 * down gracefully so the UI can reconnect cleanly.
 *
 * This class is not safe for concurrent [openConnection] calls; callers are
 * expected to keep at most one connection open at a time.
 */
class UsbSerialManager(private val context: Context) {

    /** Stream callback for incoming data and asynchronous connection errors. */
    interface Listener {
        fun onNewData(data: ByteArray)
        fun onRunError(e: Exception)
    }

    companion object {
        private const val TAG = "AMW_UsbSerialManager"
        const val DEFAULT_BAUD_RATE = 115200
        const val WRITE_TIMEOUT_MILLIS = 1000
        const val READ_TIMEOUT_MILLIS = 1000
    }

    private val androidUsbManager: AndroidUsbManager =
        context.getSystemService(Context.USB_SERVICE) as AndroidUsbManager

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AMW-SerialIO").apply { isDaemon = true }
    }

    private val ioLock = Any()
    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var connectedDevice: UsbDevice? = null
    private val connected = AtomicBoolean(false)

    @Volatile private var ioManager: SerialInputOutputManager? = null
    @Volatile private var listener: Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val internalListener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            // Invoked on the background SerialInputOutputManager thread.
            listener?.onNewData(data)
        }

        override fun onRunError(e: Exception) {
            Log.w(TAG, "Serial IO error (hardware may have detached): ${e.message}")
            mainHandler.post {
                listener?.onRunError(e)
                handleDetachment(e)
            }
        }
    }

    /** Register a stream listener. Pass null to detach the current listener. */
    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /**
     * Opens a serial connection to [device] at [baudRate] (8 data bits, 1 stop
     * bit, no parity) and starts the background read/write thread.
     */
    fun openConnection(device: UsbDevice, baudRate: Int): Boolean {
        if (connected.get() && connectedDevice == device) {
            setBaudRate(baudRate)
            return true
        }
        closeConnection()
        try {
            val driver: UsbSerialDriver = UsbSerialProber.getDefaultProber().probeDevice(device)
                ?: run { Log.w(TAG, "No serial driver for ${device.deviceName}"); return false }
            val port: UsbSerialPort = driver.ports.firstOrNull()
                ?: run { Log.w(TAG, "Driver exposes no ports"); return false }
            val connection: UsbDeviceConnection = androidUsbManager.openDevice(device)
                ?: run { Log.w(TAG, "openDevice() returned null (USB permission denied?)"); return false }
            port.open(connection)
            port.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            serialPort = port
            usbConnection = connection
            connectedDevice = device
            connected.set(true)
            startIoManager()
            Log.d(TAG, "Connected to ${device.deviceName} @ ${baudRate} baud")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "openConnection failed for ${device.deviceName}", e)
            closeConnection()
            return false
        }
    }

    /** Backwards-compatible single-arg overload (used by the RP2040 module). */
    fun openConnection(device: UsbDevice): Boolean = openConnection(device, DEFAULT_BAUD_RATE)

    private fun startIoManager() {
        synchronized(ioLock) {
            stopIoManagerLocked()
            val port = serialPort ?: return
            val manager = SerialInputOutputManager(port, internalListener)
            ioManager = manager
            ioExecutor.submit(manager)
        }
    }

    private fun stopIoManager() {
        synchronized(ioLock) { stopIoManagerLocked() }
    }

    private fun stopIoManagerLocked() {
        ioManager?.stop()
        ioManager = null
    }

    private fun handleDetachment(e: Exception) {
        Log.w(TAG, "Handling detachment, closing connection: ${e.message}")
        closeConnection()
    }

    /** Stops the IO thread and releases the port + USB connection. Safe to call repeatedly. */
    fun closeConnection() {
        stopIoManager()
        try { serialPort?.close() } catch (_: IOException) {}
        try { usbConnection?.close() } catch (_: Exception) {}
        serialPort = null
        usbConnection = null
        connectedDevice = null
        connected.set(false)
    }

    /** Writes a byte payload. Returns true on success, false if not connected or on write error. */
    fun writeData(data: ByteArray): Boolean {
        if (!connected.get()) return false
        val port = serialPort ?: return false
        return try {
            port.write(data, WRITE_TIMEOUT_MILLIS)
            true
        } catch (e: Exception) {
            Log.w(TAG, "write failed: ${e.message}")
            false
        }
    }

    /** Synchronous direct read that bypasses the IO manager. Use only when no stream listener is set. */
    fun readData(buffer: ByteArray, timeout: Int): Int {
        if (!connected.get()) return -1
        val port = serialPort ?: return -1
        return try { port.read(buffer, timeout) } catch (_: Exception) { -1 }
    }

    fun getAvailableDevices(): List<UsbDevice> =
        UsbSerialProber.getDefaultProber().findAllDrivers(androidUsbManager).map { it.device }

    fun isConnected(): Boolean = connected.get()
    fun getConnectedDevice(): UsbDevice? = connectedDevice
    fun getUsbSerialPort(): UsbSerialPort? = serialPort

    fun setBaudRate(baudRate: Int): Boolean =
        setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

    /**
     * Changes line parameters on the open port. The IO manager is stopped while
     * reconfiguring and restarted afterwards, since setParameters is not safe
     * to call concurrently with the read loop.
     */
    fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        val port = serialPort ?: return false
        if (!connected.get()) return false
        return try {
            stopIoManager()
            port.setParameters(baudRate, dataBits, stopBits, parity)
            true
        } catch (e: Exception) {
            Log.w(TAG, "setParameters failed", e)
            false
        } finally {
            startIoManager()
        }
    }

    fun setDtr(dtr: Boolean): Boolean = try { serialPort?.dtr = dtr; true } catch (_: Exception) { false }
    fun setRts(rts: Boolean): Boolean = try { serialPort?.rts = rts; true } catch (_: Exception) { false }

    /** Releases the IO thread pool. Call from Application.onTerminate()/cleanup paths. */
    fun shutdown() {
        closeConnection()
        ioExecutor.shutdownNow()
    }
}
