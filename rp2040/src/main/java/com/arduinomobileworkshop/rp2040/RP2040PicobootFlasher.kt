package com.arduinomobileworkshop.rp2040

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level RP2040 flasher that talks the PICOBOOT vendor USB protocol directly
 * over bulk endpoints.
 *
 * When a Raspberry Pi Pico is held in BOOTSEL mode it enumerates as the bootrom
 * composite device (Vendor ID 0x2E8A, Product ID 0x0003) exposing two interfaces:
 * a Mass Storage Class interface (class 0x08) and the PICOBOOT vendor interface
 * (class 0xFF) with a bulk OUT and a bulk IN endpoint. This flasher claims the
 * PICOBOOT interface with [UsbDeviceConnection.claimInterface], sends commands
 * through the bulk OUT endpoint and streams UF2 payload bytes the same way, then
 * reboots the device. No OS mass-storage mount is required.
 *
 * Command framing follows the RP2040 bootrom picoboot protocol: an 8-byte header
 * (magic 0x31, token, cmd, unused, little-endian transfer_len) followed by a
 * command-specific payload sent in the same OUT transfer; command completion is
 * queried through a vendor control-IN request.
 *
 * NOTE: command completion polling and exact reboot framing are best-effort
 * reconstructions of the documented protocol; full hardware validation requires
 * on-device testing.
 */
class RP2040PicobootFlasher(
    private val usbManager: UsbManager
) {
    companion object {
        private const val TAG = "AMW_PicoBootFlasher"

        // UF2 block format.
        private const val UF2_MAGIC_START0 = 0x0A324655
        private const val UF2_MAGIC_START1 = 0x9E5D5157
        private const val UF2_MAGIC_END = 0x0AB16F30
        private const val UF2_BLOCK_SIZE = 512
        private const val UF2_DATA_OFFSET = 32
        private const val UF2_PAYLOAD_SIZE = 256

        // PICOBOOT command header.
        private const val PICOBOOT_CMD_MAGIC: Byte = 0x31

        // Command ids (from the RP2040 bootrom picoboot command set).
        private const val PC_EXCLUSIVE_ACCESS: Byte = 0x01
        private const val PC_REBOOT: Byte = 0x02
        private const val PC_FLASH_ERASE: Byte = 0x03
        private const val PC_READ: Byte = 0x04
        private const val PC_WRITE: Byte = 0x05
        private const val PC_EXIT_XIP: Byte = 0x06

        // USB control request to read the command status (vendor, interface, IN).
        private const val GET_CMD_STATUS_REQUEST = 0x40
        // Recipient encoding for the control bmRequestType (not exposed by UsbConstants).
        private const val USB_RECIPIENT_INTERFACE = 0x01

        private const val FLASH_SECTOR_SIZE = 4096
        private const val BULK_TIMEOUT_MS = 5000
    }

    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null
    private var token: Int = 0

    /** Opens the PICOBOOT vendor interface on [device] and claims it. */
    fun open(device: UsbDevice): Boolean {
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "No USB permission for " + device.deviceName)
            return false
        }
        val conn = usbManager.openDevice(device) ?: run {
            Log.w(TAG, "openDevice returned null")
            return false
        }
        // Prefer the vendor-specific (class 0xFF) interface; fall back to the
        // last interface (PICOBOOT is interface #1 on the bootrom composite).
        val picobootIface = findPicobootInterface(device) ?: device.getInterface(device.interfaceCount - 1)
        if (!conn.claimInterface(picobootIface, true)) {
            Log.w(TAG, "claimInterface failed")
            conn.close()
            return false
        }
        var out: UsbEndpoint? = null
        var inn: UsbEndpoint? = null
        for (i in 0 until picobootIface.endpointCount) {
            val ep = picobootIface.getEndpoint(i)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_OUT) out = ep
            else if (ep.direction == UsbConstants.USB_DIR_IN) inn = ep
        }
        if (out == null) {
            Log.w(TAG, "No bulk OUT endpoint on PICOBOOT interface")
            conn.releaseInterface(picobootIface)
            conn.close()
            return false
        }
        connection = conn
        iface = picobootIface
        outEndpoint = out
        inEndpoint = inn
        Log.d(TAG, "PICOBOOT interface claimed (" + picobootIface.id + "), OUT ep=" + out.address)
        return true
    }

    private fun findPicobootInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            if (itf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) return itf
        }
        return null
    }

    fun close() {
        val conn = connection ?: return
        try { iface?.let { conn.releaseInterface(it) } } catch (_: Exception) {}
        try { conn.close() } catch (_: Exception) {}
        connection = null
        iface = null
        outEndpoint = null
        inEndpoint = null
    }

    /**
     * Flashes a UF2 binary to the device. [progress] receives 0..100. Returns
     * true on success. The UF2 file is parsed into 256-byte payload blocks, each
     * carrying its own target flash address, so we erase the containing sector
     * once and stream every block via PC_WRITE.
     */
    fun programUf2(uf2File: File, progress: (Int) -> Unit): Boolean {
        val conn = connection ?: return false
        val out = outEndpoint ?: return false
        try {
            val bytes = uf2File.readBytes()
            val blocks = parseUf2(bytes)
            if (blocks.isEmpty()) {
                Log.w(TAG, "No valid UF2 blocks in " + uf2File.name)
                return false
            }

            // 1) Take exclusive access and leave XIP mode before touching flash.
            if (!sendCommand(PC_EXCLUSIVE_ACCESS, payload = byteArrayOf(1))) return false
            if (!sendCommand(PC_EXIT_XIP)) return false

            val erased = HashSet<Long>()
            val total = blocks.size
            for ((index, block) in blocks.withIndex()) {
                // Erase the 4 KiB sector that contains this block's target address.
                val sectorBase = block.targetAddr and (FLASH_SECTOR_SIZE - 1).inv().toLong()
                if (!erased.contains(sectorBase)) {
                    if (!flashErase(sectorBase, FLASH_SECTOR_SIZE)) return false
                    erased.add(sectorBase)
                }
                // Program the 256-byte payload at the block's target address.
                if (!writeFlash(block.targetAddr, block.payload)) return false
                progress((index + 1) * 100 / total)
            }

            // 2) Release exclusive access and reboot into the new firmware.
            sendCommand(PC_EXCLUSIVE_ACCESS, payload = byteArrayOf(0))
            reboot()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "programUf2 failed: " + (e.message ?: ""))
            return false
        }
    }

    private data class Uf2Block(val targetAddr: Long, val payload: ByteArray)

    private fun parseUf2(data: ByteArray): List<Uf2Block> {
        val out = mutableListOf<Uf2Block>()
        var i = 0
        while (i + UF2_BLOCK_SIZE <= data.size) {
            val bb = ByteBuffer.wrap(data, i, UF2_BLOCK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val m0 = bb.int
            val m1 = bb.int
            if (m0 != UF2_MAGIC_START0.toInt() || m1 != UF2_MAGIC_START1.toInt()) { i += UF2_BLOCK_SIZE; continue }
            bb.int // flags
            val targetAddr = bb.int.toLong() and 0xFFFFFFFFL
            bb.int // payloadSize (256)
            bb.int // blockNo
            bb.int // numBlocks
            bb.int // familyID / fileSize
            val payload = ByteArray(UF2_PAYLOAD_SIZE)
            System.arraycopy(data, i + UF2_DATA_OFFSET, payload, 0, UF2_PAYLOAD_SIZE)
            out += Uf2Block(targetAddr, payload)
            i += UF2_BLOCK_SIZE
        }
        return out
    }

    /** Sends a command: 8-byte header + payload, then waits for completion. */
    private fun sendCommand(cmd: Byte, transferLen: Int = 0, payload: ByteArray = ByteArray(0)): Boolean {
        val conn = connection ?: return false
        val out = outEndpoint ?: return false
        val pkt = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        pkt.put(PICOBOOT_CMD_MAGIC)
        pkt.put((token and 0xFF).toByte()); token++
        pkt.put(cmd)
        pkt.put(0) // unused
        pkt.putInt(transferLen)
        if (payload.isNotEmpty()) pkt.put(payload)
        val data = pkt.array()
        val written = conn.bulkTransfer(out, data, data.size, BULK_TIMEOUT_MS)
        if (written != data.size) {
            Log.w(TAG, "bulk OUT for cmd 0x" + cmd.toString(16) + " wrote " + written)
            return false
        }
        return waitForCompletion()
    }

    private fun flashErase(addr: Long, size: Int): Boolean {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(addr.toInt())
        payload.putInt(size)
        return sendCommand(PC_FLASH_ERASE, payload = payload.array())
    }

    /**
     * Writes [data] (<= 256 bytes) to flash at [addr]: a PC_WRITE command whose
     * transfer_len is the data length and whose payload is the 4-byte address,
     * immediately followed by the data streamed over the same bulk OUT endpoint.
     */
    private fun writeFlash(addr: Long, data: ByteArray): Boolean {
        val conn = connection ?: return false
        val out = outEndpoint ?: return false
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(addr.toInt())
        if (!sendCommand(PC_WRITE, transferLen = data.size, payload = payload.array())) return false
        val written = conn.bulkTransfer(out, data, data.size, BULK_TIMEOUT_MS)
        if (written != data.size) {
            Log.w(TAG, "writeFlash bulk data wrote " + written + " of " + data.size)
            return false
        }
        return waitForCompletion()
    }

    /** Reboots the device to launch the freshly programmed firmware. */
    private fun reboot(): Boolean {
        // dPC=0, dSP=0, dDelay=0, exclusive=0 -> normal reboot into flash.
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(0); payload.putInt(0); payload.putInt(0); payload.putInt(0)
        return sendCommand(PC_REBOOT, payload = payload.array())
    }

    /**
     * Polls the PICOBOOT command status (vendor control-IN) until the bootrom
     * reports the command is no longer in progress. Best-effort: if the status
     * control transfer is unavailable on a given device, we proceed optimistically.
     */
    private fun waitForCompletion(): Boolean {
        val conn = connection ?: return false
        val ifaceId = iface?.id ?: 0
        val status = ByteArray(12)
        for (attempt in 0 until 50) {
            val n = conn.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR or USB_RECIPIENT_INTERFACE,
                GET_CMD_STATUS_REQUEST, 0, ifaceId, status, status.size, 200
            )
            if (n < 0) {
                // Status endpoint not available on this device; assume completion.
                return true
            }
            // bInProgress is the second byte of PicobootCmdStatus.
            val inProgress = status[1].toInt() and 0xFF
            if (inProgress == 0) {
                // dStatusCode at offset 8 (little-endian).
                val code = (status[8].toInt() and 0xFF) or ((status[9].toInt() and 0xFF) shl 8)
                return code == 0
            }
            try { Thread.sleep(2) } catch (_: InterruptedException) {}
        }
        Log.w(TAG, "waitForCompletion timed out")
        return false
    }
}
