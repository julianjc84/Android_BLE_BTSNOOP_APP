package com.jc84.blesnooplogger.data.capture

import com.jc84.blesnooplogger.data.btsnoop.BtSnoopConstants
import com.jc84.blesnooplogger.data.btsnoop.BtSnoopRecord
import com.jc84.blesnooplogger.data.hci.AdvertisingParser
import com.jc84.blesnooplogger.data.hci.AttParser
import com.jc84.blesnooplogger.data.hci.GattParser
import com.jc84.blesnooplogger.data.hci.HciParser
import com.jc84.blesnooplogger.data.hci.L2capParser
import com.jc84.blesnooplogger.data.hci.RfcommParser
import com.jc84.blesnooplogger.data.model.BlePacket
import com.jc84.blesnooplogger.data.protocol.ProtocolRegistry
import com.jc84.blesnooplogger.data.util.RootShell
import com.jc84.blesnooplogger.data.util.toHexString
import com.jc84.blesnooplogger.data.util.toPrintableAscii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Captures BLE/BT packets by reading the btsnoop HCI log via root shell.
 * Pre-scans existing file for connection events to resolve handle→MAC mapping.
 */
class RootSnoopCapture {

    private val handleToAddress = ConcurrentHashMap<Int, String>()
    private val addressLabels = ConcurrentHashMap<String, String>()
    private val addressNames = ConcurrentHashMap<String, String>()

    /** Per-connection GATT map: connectionHandle → (attHandle → normalized UUID). */
    private val gattHandles = ConcurrentHashMap<Int, ConcurrentHashMap<Int, String>>()
    private var packetCounter = 0L
    @Volatile
    private var capturing = false

    /** The long-running `tail -f` subprocess. Destroyed by stop(). */
    @Volatile
    private var tailProcess: Process? = null

    var onDeviceDiscovered: ((String) -> Unit)? = null

    /**
     * When true, LE advertising reports are emitted as ADV packets. Off by default so the
     * stream isn't flooded by nearby devices — set before [startCapture].
     */
    @Volatile
    var includeAdvertising = false

    private val snoopPath = "/data/misc/bluetooth/logs/btsnoop_hci.log"

    fun startCapture(): Flow<BlePacket> = flow {
        packetCounter = 0L
        handleToAddress.clear()
        addressLabels.clear()
        addressNames.clear()
        gattHandles.clear()
        capturing = true

        Timber.i("Bluetooth: starting root snoop capture (tail -f) from %s", snoopPath)

        val header = rootRead(0, BtSnoopConstants.FILE_HEADER_SIZE)
        if (header == null || header.size < BtSnoopConstants.FILE_HEADER_SIZE) {
            throw CaptureException("Cannot read btsnoop file header — is HCI snoop log enabled?")
        }
        validateFileHeader(header)

        // Pre-scan: read entire existing file to find connection events and build handle→address map
        val fileSize = rootFileSize() ?: BtSnoopConstants.FILE_HEADER_SIZE.toLong()
        Timber.i("Bluetooth: pre-scanning %d bytes for connection events", fileSize)
        prescanForConnections(fileSize)
        Timber.i("Bluetooth: pre-scan done, found %d handle→address mappings", handleToAddress.size)

        // Start a single long-running tail subprocess from the current EOF. It blocks waiting
        // for new bytes — packets stream into our stdin as the kernel writes them rather than
        // arriving in 100ms polling bursts. `-c +N` skips the first N-1 bytes (1-indexed).
        val proc = RootShell.execProcess("tail -c +${fileSize + 1} -f $snoopPath")
        tailProcess = proc
        Timber.i("Bluetooth: tail -f started at byte offset %d", fileSize)

        try {
            val input = proc.inputStream
            val readBuf = ByteArray(64 * 1024)
            // Holds bytes left over when a record spans a read boundary.
            val residue = ByteArrayOutputStream(64 * 1024)

            while (capturing && coroutineContext.isActive) {
                val n = try {
                    input.read(readBuf)
                } catch (e: java.io.IOException) {
                    // proc.destroy() closes the input stream from another thread.
                    // On Android that surfaces here as "read interrupted by close()" instead
                    // of a clean -1. If we initiated the close, treat it as normal EOF.
                    if (!capturing) break
                    throw e
                }
                if (n < 0) break  // process ended (stop() destroyed it, or tail died)
                if (n == 0) continue

                residue.write(readBuf, 0, n)
                val data = residue.toByteArray()

                // Drain as many complete records as possible from the buffer.
                val packetsThisRead = mutableListOf<BlePacket>()
                val consumed = processChunk(data) { record ->
                    processRecordLive(record)?.let { packetsThisRead.addAll(it) }
                }
                for (packet in packetsThisRead) emit(packet)

                // Keep only the partial-record tail for the next read.
                residue.reset()
                if (consumed < data.size) {
                    residue.write(data, consumed, data.size - consumed)
                }
            }
        } finally {
            Timber.i("Bluetooth: tail -f loop ended")
            proc.destroy()
            try { proc.waitFor() } catch (_: Exception) {}
            tailProcess = null
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        Timber.i("Bluetooth: stopping snoop capture")
        capturing = false
        // Closing the subprocess unblocks input.read() in the capture loop.
        tailProcess?.destroy()
    }

    /**
     * Scan existing file content for HCI connection events to build handle→address map.
     * Reads the entire file in one shot for speed.
     */
    private fun prescanForConnections(fileSize: Long) {
        val dataSize = (fileSize - BtSnoopConstants.FILE_HEADER_SIZE).toInt()
        if (dataSize <= 0) return

        val allData = rootRead(BtSnoopConstants.FILE_HEADER_SIZE.toLong(), dataSize)
        if (allData == null || allData.isEmpty()) return

        Timber.i("Bluetooth: pre-scan read %d bytes in one shot", allData.size)

        processChunk(allData) { record ->
            // Advertised / remote names — learned while scanning, before connection.
            captureNames(record)
            // GATT discovery usually happens right after connect, before we start tailing.
            learnGatt(record)
            // Only care about connection/disconnection events during pre-scan
            HciParser.parseConnectionEvent(record)?.let { conn ->
                handleToAddress[conn.connectionHandle] = conn.peerAddress
                onDeviceDiscovered?.invoke(conn.peerAddress)
                Timber.i("Bluetooth: pre-scan found %s connection handle=0x%04X addr=%s",
                    if (conn.isLe) "LE" else "Classic", conn.connectionHandle, conn.peerAddress)
            }
            HciParser.parseDisconnectionEvent(record)?.let { disc ->
                handleToAddress.remove(disc.connectionHandle)
            }
        }
    }

    /**
     * Parse btsnoop records from a byte chunk. Calls [onRecord] for each complete record.
     * Returns number of bytes consumed.
     */
    private inline fun processChunk(chunk: ByteArray, onRecord: (BtSnoopRecord) -> Unit): Int {
        val stream = ByteArrayInputStream(chunk)
        var consumed = 0

        while (consumed + BtSnoopConstants.RECORD_HEADER_SIZE <= chunk.size) {
            val remaining = chunk.size - consumed
            if (remaining < BtSnoopConstants.RECORD_HEADER_SIZE) break

            val recHeader = ByteArray(BtSnoopConstants.RECORD_HEADER_SIZE)
            if (stream.read(recHeader) != BtSnoopConstants.RECORD_HEADER_SIZE) break

            val buf = ByteBuffer.wrap(recHeader).order(ByteOrder.BIG_ENDIAN)
            val originalLength = buf.getInt().toLong() and 0xFFFFFFFFL
            val includedLength = (buf.getInt().toLong() and 0xFFFFFFFFL).toInt()
            val flags = (buf.getInt().toLong() and 0xFFFFFFFFL).toInt()
            buf.getInt() // drops
            val timestampUs = buf.getLong()

            if (includedLength < 0 || includedLength > 65535) break
            if (remaining - BtSnoopConstants.RECORD_HEADER_SIZE < includedLength) break

            val data = ByteArray(includedLength)
            if (stream.read(data) != includedLength) break

            consumed += BtSnoopConstants.RECORD_HEADER_SIZE + includedLength

            val record = BtSnoopRecord(
                originalLength = originalLength.toInt(),
                includedLength = includedLength,
                flags = flags,
                timestampUs = timestampUs,
                data = data,
            )

            onRecord(record)
        }

        return consumed
    }

    /**
     * Process a record during live capture: handle connection events and decode data.
     */
    private fun processRecordLive(record: BtSnoopRecord): List<BlePacket>? {
        val result = mutableListOf<BlePacket>()

        // Advertised / remote names arriving live (e.g. re-scans while connected).
        captureNames(record)

        // Advertising reports as their own packets (opt-in).
        if (includeAdvertising) {
            for (adv in AdvertisingParser.parse(record)) {
                result.add(BlePacket(
                    index = packetCounter++,
                    timestampMs = record.unixTimestampMs,
                    direction = BlePacket.Direction.EVENT,
                    deviceAddress = adv.address,
                    packetType = "ADV",
                    opcode = adv.name?.let { "Adv: $it" } ?: "Adv (${adv.eventLabel})",
                    attHandle = null,
                    dataHex = adv.adBytes.toHexString(),
                    dataAscii = adv.summary,
                    rawDataBytes = adv.adBytes,
                ))
            }
        }

        // Connection events
        HciParser.parseConnectionEvent(record)?.let { conn ->
            handleToAddress[conn.connectionHandle] = conn.peerAddress
            onDeviceDiscovered?.invoke(conn.peerAddress)

            val transportType = if (conn.isLe) "LE" else "Classic"
            result.add(BlePacket(
                index = packetCounter++,
                timestampMs = record.unixTimestampMs,
                direction = BlePacket.Direction.EVENT,
                deviceAddress = conn.peerAddress,
                packetType = "HCI",
                opcode = "Connected ($transportType)",
                attHandle = conn.connectionHandle,
                dataHex = "",
                dataAscii = "",
                rawDataBytes = ByteArray(0),
            ))
        }

        // Disconnection events
        HciParser.parseDisconnectionEvent(record)?.let { disc ->
            val address = handleToAddress[disc.connectionHandle] ?: "handle:0x%04X".format(disc.connectionHandle)
            result.add(BlePacket(
                index = packetCounter++,
                timestampMs = record.unixTimestampMs,
                direction = BlePacket.Direction.EVENT,
                deviceAddress = address,
                packetType = "HCI",
                opcode = "Disconnected (0x%02X)".format(disc.reason),
                attHandle = disc.connectionHandle,
                dataHex = "",
                dataAscii = "",
                rawDataBytes = ByteArray(0),
            ))
            handleToAddress.remove(disc.connectionHandle)
        }

        // Data packets
        result.addAll(decodeDataPackets(record))

        return result.ifEmpty { null }
    }

    private fun decodeDataPackets(record: BtSnoopRecord): List<BlePacket> {
        val acl = HciParser.parseAcl(record) ?: return emptyList()
        val l2cap = L2capParser.parse(acl.payload) ?: return emptyList()

        val address = handleToAddress[acl.connectionHandle] ?: "handle:0x%04X".format(acl.connectionHandle)
        val direction = if (record.isSent) BlePacket.Direction.TX else BlePacket.Direction.RX

        // ATT channel (CID 0x0004)
        if (l2cap.isAtt) {
            // Learn handle→UUID from any discovery responses carried by this PDU.
            learnGatt(record, acl, l2cap)
            val att = AttParser.parse(l2cap.payload) ?: return emptyList()
            val handleName = att.attHandle?.let { h ->
                gattHandles[acl.connectionHandle]?.get(h)?.let { GattParser.uuidLabel(it) }
            }
            return listOf(makePacket(direction, address, record.unixTimestampMs,
                "ATT", att.opcodeName, att.attHandle, att.value, handleName))
        }

        // Dynamic L2CAP channels — try RFCOMM decode
        if (l2cap.cid >= 0x0040 && l2cap.payload.isNotEmpty()) {
            val rfcomm = RfcommParser.parseDataFrame(l2cap.payload)
            if (rfcomm != null && rfcomm.payload.isNotEmpty()) {
                return listOf(makePacket(direction, address, record.unixTimestampMs,
                    "SPP", "Data (%d bytes)".format(rfcomm.payload.size), null, rfcomm.payload))
            }

            return listOf(makePacket(direction, address, record.unixTimestampMs,
                "L2CAP:0x%04X".format(l2cap.cid), "Data (%d bytes)".format(l2cap.payload.size),
                null, l2cap.payload))
        }

        return emptyList()
    }

    private fun makePacket(
        direction: BlePacket.Direction,
        address: String,
        timestampMs: Long,
        packetType: String,
        opcode: String,
        attHandle: Int?,
        data: ByteArray,
        handleName: String? = null,
    ): BlePacket {
        val packet = BlePacket(
            index = packetCounter++,
            timestampMs = timestampMs,
            direction = direction,
            deviceAddress = address,
            packetType = packetType,
            opcode = opcode,
            attHandle = attHandle,
            handleName = handleName,
            dataHex = data.toHexString(),
            dataAscii = data.toPrintableAscii(),
            rawDataBytes = data,
        )
        detectProtocol(packet)
        return packet
    }

    /** Learn handle→UUID mappings from a record's ATT PDU (pre-scan entry point). */
    private fun learnGatt(record: BtSnoopRecord) {
        val acl = HciParser.parseAcl(record) ?: return
        val l2cap = L2capParser.parse(acl.payload) ?: return
        if (l2cap.isAtt) learnGatt(record, acl, l2cap)
    }

    /** Learn handle→UUID mappings from an already-parsed ATT PDU (live entry point). */
    private fun learnGatt(
        @Suppress("UNUSED_PARAMETER") record: BtSnoopRecord,
        acl: com.jc84.blesnooplogger.data.hci.HciAclData,
        l2cap: com.jc84.blesnooplogger.data.hci.L2capFrame,
    ) {
        val discovered = GattParser.parseDiscovery(l2cap.payload)
        if (discovered.isEmpty()) return
        val map = gattHandles.getOrPut(acl.connectionHandle) { ConcurrentHashMap() }
        for ((handle, uuid) in discovered) {
            if (map.put(handle, uuid) == null) {
                Timber.i("Bluetooth: GATT handle 0x%04X = %s", handle, uuid)
            }
        }
    }

    private fun detectProtocol(packet: BlePacket) {
        if (packet.rawDataBytes.size < 2) return
        if (addressLabels.containsKey(packet.deviceAddress)) return
        if (!packet.deviceAddress.contains(":")) return

        val label = ProtocolRegistry.identify(packet.rawDataBytes) ?: return
        addressLabels[packet.deviceAddress] = label
        Timber.i("Bluetooth: detected protocol %s for %s", label, packet.deviceAddress)
    }

    fun getProtocolLabel(address: String): String? = addressLabels[address]

    fun getDeviceName(address: String): String? = addressNames[address]

    /** Record any advertised (BLE) or remote (Classic) device names carried by this record. */
    private fun captureNames(record: BtSnoopRecord) {
        val names = HciParser.parseDeviceNames(record)
        if (names.isEmpty()) return
        for ((mac, name) in names) {
            if (addressNames.put(mac, name) == null) {
                Timber.i("Bluetooth: resolved name %s = \"%s\"", mac, name)
            }
        }
    }

    private fun rootRead(offset: Long, count: Int): ByteArray? {
        return try {
            val cmd = if (count > 4096) {
                "dd if=$snoopPath bs=4096 iflag=skip_bytes,count_bytes skip=$offset count=$count"
            } else {
                "dd if=$snoopPath bs=1 skip=$offset count=$count"
            }
            RootShell.execBytes(cmd)
        } catch (e: Exception) {
            Timber.e("Bluetooth: root read failed at offset %d: %s", offset, e.message)
            null
        }
    }

    private fun rootFileSize(): Long? {
        return try {
            RootShell.exec("stat -c %s $snoopPath").trim().toLongOrNull()
        } catch (e: Exception) {
            Timber.e("Bluetooth: stat failed: %s", e.message)
            null
        }
    }

    private fun validateFileHeader(header: ByteArray) {
        for (i in BtSnoopConstants.FILE_MAGIC.indices) {
            if (header[i] != BtSnoopConstants.FILE_MAGIC[i]) {
                throw CaptureException("Invalid btsnoop magic at byte $i")
            }
        }
        val buf = ByteBuffer.wrap(header, 8, 8).order(ByteOrder.BIG_ENDIAN)
        val version = buf.getInt().toLong() and 0xFFFFFFFFL
        val datalink = buf.getInt().toLong() and 0xFFFFFFFFL
        if (datalink != BtSnoopConstants.DATALINK_H4) {
            throw CaptureException("Unsupported datalink type: $datalink")
        }
    }
}

class CaptureException(message: String) : Exception(message)
