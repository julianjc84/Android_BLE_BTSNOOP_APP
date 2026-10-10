package com.jc84.blesnooplogger.data.hci

import com.jc84.blesnooplogger.data.btsnoop.BtSnoopConstants
import com.jc84.blesnooplogger.data.btsnoop.BtSnoopRecord
import com.jc84.blesnooplogger.data.util.formatBtAddressReversed
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class HciAclData(
    val connectionHandle: Int,
    val pbFlag: Int,
    val bcFlag: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HciAclData) return false
        return connectionHandle == other.connectionHandle && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * connectionHandle + payload.contentHashCode()
}

data class ConnectionEvent(
    val connectionHandle: Int,
    val peerAddress: String,
    val isLe: Boolean,
)

data class DisconnectionEvent(
    val connectionHandle: Int,
    val reason: Int,
)

/**
 * HCI packet parser.
 *
 * IMPORTANT: btsnoop on this device includes the H4 transport type byte as the
 * first byte of every record's data. So for events, data[0]=0x04 (H4 event type),
 * data[1]=event_code, data[2]=param_length, data[3..]=params.
 * For ACL, data[0]=0x02, data[1..4]=ACL header, data[5..]=payload.
 */
object HciParser {

    /**
     * Parse ACL data. Record data starts with H4 type byte (0x02 for ACL).
     * ACL header: handle+flags(2 LE) + length(2 LE), then payload.
     */
    fun parseAcl(record: BtSnoopRecord): HciAclData? {
        if (record.h4Type != BtSnoopConstants.H4_ACL) return null
        val data = record.data

        // Find ACL header: skip H4 byte if present
        val offset = if (data.isNotEmpty() && data[0] == 0x02.toByte()) 1 else 0
        if (data.size < offset + 4) return null

        val buf = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
        val handleAndFlags = buf.getShort().toInt() and 0xFFFF
        val aclLength = buf.getShort().toInt() and 0xFFFF

        val connectionHandle = handleAndFlags and 0x0FFF
        val pbFlag = (handleAndFlags shr 12) and 0x03
        val bcFlag = (handleAndFlags shr 14) and 0x03

        val payloadStart = offset + 4
        val payloadEnd = minOf(payloadStart + aclLength, data.size)
        val payload = data.copyOfRange(payloadStart, payloadEnd)

        return HciAclData(
            connectionHandle = connectionHandle,
            pbFlag = pbFlag,
            bcFlag = bcFlag,
            payload = payload,
        )
    }

    /**
     * Extract advertised / remote device names as (MAC, name) pairs.
     *
     * Sources:
     *  - LE Advertising Report (event 0x3E, subevent 0x02 legacy / 0x0D extended):
     *    AD type 0x09 (Complete Local Name) or 0x08 (Shortened Local Name).
     *  - Classic Remote Name Request Complete (event 0x07).
     *
     * A single report event can carry several devices, hence a list.
     */
    fun parseDeviceNames(record: BtSnoopRecord): List<Pair<String, String>> {
        if (record.h4Type != BtSnoopConstants.H4_EVT) return emptyList()
        val data = record.data
        val o = if (data.isNotEmpty() && data[0] == 0x04.toByte()) 1 else 0
        if (data.size < o + 2) return emptyList()

        return when (data[o].toInt() and 0xFF) {
            0x3E -> parseAdvertisingNames(data, o)
            0x07 -> parseRemoteNameComplete(data, o)?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    /** LE Meta (0x3E) advertising reports — pull local-name AD fields per report. */
    private fun parseAdvertisingNames(data: ByteArray, o: Int): List<Pair<String, String>> {
        if (data.size < o + 4) return emptyList()
        val subevent = data[o + 2].toInt() and 0xFF
        val numReports = data[o + 3].toInt() and 0xFF
        val results = mutableListOf<Pair<String, String>>()
        var p = o + 4

        repeat(numReports) {
            when (subevent) {
                0x0D -> {
                    // event_type(2) addr_type(1) addr(6) primary_phy(1) secondary_phy(1)
                    // sid(1) tx_power(1) rssi(1) periodic(2) direct_addr_type(1) direct_addr(6) data_len(1)
                    if (p + 24 > data.size) return results
                    val addr = formatBtAddressReversed(data.copyOfRange(p + 3, p + 9))
                    val dataLen = data[p + 23].toInt() and 0xFF
                    val adStart = p + 24
                    if (adStart + dataLen > data.size) return results
                    extractName(data, adStart, dataLen)?.let { results.add(addr to it) }
                    p = adStart + dataLen
                }
                0x02 -> {
                    // event_type(1) addr_type(1) addr(6) data_len(1) data(n) rssi(1)
                    if (p + 9 > data.size) return results
                    val addr = formatBtAddressReversed(data.copyOfRange(p + 2, p + 8))
                    val dataLen = data[p + 8].toInt() and 0xFF
                    val adStart = p + 9
                    if (adStart + dataLen > data.size) return results
                    extractName(data, adStart, dataLen)?.let { results.add(addr to it) }
                    p = adStart + dataLen + 1 // + rssi
                }
                else -> return results
            }
        }
        return results
    }

    /** Walk AD structures, returning a Complete (0x09) or Shortened (0x08) Local Name. */
    private fun extractName(data: ByteArray, start: Int, length: Int): String? {
        var q = start
        val end = start + length
        while (q + 1 < end) {
            val len = data[q].toInt() and 0xFF
            if (len == 0) break
            val type = data[q + 1].toInt() and 0xFF
            if (type == 0x09 || type == 0x08) {
                val nameEnd = minOf(q + 1 + len, end)
                val name = String(data, q + 2, nameEnd - (q + 2)).trim { it <= ' ' }
                if (name.isNotEmpty()) return name
            }
            q += 1 + len
        }
        return null
    }

    /** Classic Remote Name Request Complete (0x07): status(1) + addr(6) + name(<=248, null-padded). */
    private fun parseRemoteNameComplete(data: ByteArray, o: Int): Pair<String, String>? {
        val paramLen = data[o + 1].toInt() and 0xFF
        if (data.size < o + 9 || paramLen < 7) return null
        if ((data[o + 2].toInt() and 0xFF) != 0x00) return null // status != success
        val addr = formatBtAddressReversed(data.copyOfRange(o + 3, o + 9))
        val nameStart = o + 9
        val nameEnd = minOf(nameStart + (paramLen - 7), data.size)
        var z = nameStart
        while (z < nameEnd && data[z].toInt() != 0) z++
        val name = String(data, nameStart, z - nameStart).trim { it <= ' ' }
        return if (name.isEmpty()) null else addr to name
    }

    /**
     * Parse connection event. Event data starts with H4 type byte (0x04).
     * Event format: H4(1) + event_code(1) + param_length(1) + params...
     */
    fun parseConnectionEvent(record: BtSnoopRecord): ConnectionEvent? {
        if (record.h4Type != BtSnoopConstants.H4_EVT) return null
        val data = record.data

        // Skip H4 type byte if present
        val offset = if (data.isNotEmpty() && data[0] == 0x04.toByte()) 1 else 0
        if (data.size < offset + 2) return null

        val eventCode = data[offset].toInt() and 0xFF

        return when (eventCode) {
            0x03 -> parseClassicConnectionComplete(data, offset)
            0x3E -> parseLeMetaConnectionComplete(data, offset)
            else -> null
        }
    }

    /**
     * Parse Disconnection Complete (event 0x05).
     * Format: [H4] + event_code(1) + param_length(1) + status(1) + handle(2) + reason(1)
     */
    fun parseDisconnectionEvent(record: BtSnoopRecord): DisconnectionEvent? {
        if (record.h4Type != BtSnoopConstants.H4_EVT) return null
        val data = record.data

        val offset = if (data.isNotEmpty() && data[0] == 0x04.toByte()) 1 else 0
        if (data.size < offset + 2) return null

        val eventCode = data[offset].toInt() and 0xFF
        if (eventCode != 0x05) return null

        // event_code(1) + param_length(1) + status(1) + handle(2) + reason(1)
        if (data.size < offset + 6) return null

        val status = data[offset + 2].toInt() and 0xFF
        if (status != 0x00) return null

        val handle = ByteBuffer.wrap(data, offset + 3, 2).order(ByteOrder.LITTLE_ENDIAN)
            .getShort().toInt() and 0x0FFF
        val reason = data[offset + 5].toInt() and 0xFF

        return DisconnectionEvent(connectionHandle = handle, reason = reason)
    }

    /**
     * Classic Connection Complete (event 0x03).
     * event_code(1) + param_length(1) + status(1) + handle(2) + addr(6) + link_type(1) + encryption(1)
     */
    private fun parseClassicConnectionComplete(data: ByteArray, h4Offset: Int): ConnectionEvent? {
        val o = h4Offset // base offset past H4 byte
        // Need: event_code(1) + param_len(1) + status(1) + handle(2) + addr(6) = 12 bytes
        if (data.size < o + 12) return null

        val status = data[o + 2].toInt() and 0xFF
        if (status != 0x00) return null

        val handle = ByteBuffer.wrap(data, o + 3, 2).order(ByteOrder.LITTLE_ENDIAN)
            .getShort().toInt() and 0x0FFF
        val addrBytes = data.copyOfRange(o + 5, o + 11)
        val address = formatBtAddressReversed(addrBytes)

        Timber.i("Bluetooth: Classic Connection Complete handle=0x%04X addr=%s", handle, address)
        return ConnectionEvent(connectionHandle = handle, peerAddress = address, isLe = false)
    }

    /**
     * LE Meta Event (0x3E) — Connection Complete (0x01) or Enhanced (0x0A).
     * event_code(1) + param_length(1) + subevent(1) + params...
     */
    private fun parseLeMetaConnectionComplete(data: ByteArray, h4Offset: Int): ConnectionEvent? {
        val o = h4Offset
        if (data.size < o + 3) return null

        val subeventCode = data[o + 2].toInt() and 0xFF
        if (subeventCode != 0x01 && subeventCode != 0x0A) return null

        // subevent(1) + status(1) + handle(2) + role(1) + addr_type(1) + addr(6) = 12 bytes from event_code
        if (data.size < o + 14) return null

        val status = data[o + 3].toInt() and 0xFF
        if (status != 0x00) return null

        val handle = ByteBuffer.wrap(data, o + 4, 2).order(ByteOrder.LITTLE_ENDIAN)
            .getShort().toInt() and 0x0FFF
        // o+6 = role, o+7 = addr_type
        val addrBytes = data.copyOfRange(o + 8, o + 14)
        val address = formatBtAddressReversed(addrBytes)

        Timber.i("Bluetooth: LE Connection Complete (sub=0x%02X) handle=0x%04X addr=%s", subeventCode, handle, address)
        return ConnectionEvent(connectionHandle = handle, peerAddress = address, isLe = true)
    }
}
