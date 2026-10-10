package com.jc84.blesnooplogger.data.hci

import com.jc84.blesnooplogger.data.btsnoop.BtSnoopConstants
import com.jc84.blesnooplogger.data.btsnoop.BtSnoopRecord
import com.jc84.blesnooplogger.data.util.formatBtAddressReversed

/**
 * A single decoded LE advertising report.
 *
 * [summary] is a compact human string of the decoded AD structures (flags, name,
 * service UUIDs, manufacturer data, TX power, appearance).
 */
data class AdvReport(
    val address: String,
    val eventLabel: String,
    val rssi: Int?,
    val name: String?,
    val adBytes: ByteArray,
    val summary: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdvReport) return false
        return address == other.address && adBytes.contentEquals(other.adBytes)
    }

    override fun hashCode(): Int = 31 * address.hashCode() + adBytes.contentHashCode()
}

/**
 * Decodes LE Advertising Report HCI events (legacy subevent 0x02 and extended 0x0D)
 * into per-device [AdvReport]s. Standard GAP — no device-specific knowledge.
 */
object AdvertisingParser {

    fun parse(record: BtSnoopRecord): List<AdvReport> {
        if (record.h4Type != BtSnoopConstants.H4_EVT) return emptyList()
        val data = record.data
        val o = if (data.isNotEmpty() && data[0] == 0x04.toByte()) 1 else 0
        if (data.size < o + 4) return emptyList()
        if ((data[o].toInt() and 0xFF) != 0x3E) return emptyList()

        return when (data[o + 2].toInt() and 0xFF) {
            0x02 -> parseLegacy(data, o)
            0x0D -> parseExtended(data, o)
            else -> emptyList()
        }
    }

    /** Legacy report: [event_type(1) addr_type(1) addr(6) data_len(1) data(n) rssi(1)] * num. */
    private fun parseLegacy(data: ByteArray, o: Int): List<AdvReport> {
        val num = data[o + 3].toInt() and 0xFF
        val out = mutableListOf<AdvReport>()
        var p = o + 4
        repeat(num) {
            if (p + 9 > data.size) return out
            val evt = data[p].toInt() and 0xFF
            val addr = formatBtAddressReversed(data.copyOfRange(p + 2, p + 8))
            val dataLen = data[p + 8].toInt() and 0xFF
            val adStart = p + 9
            if (adStart + dataLen + 1 > data.size) return out
            val adBytes = data.copyOfRange(adStart, adStart + dataLen)
            val rssi = data[adStart + dataLen].toInt().toByte().toInt()
            out.add(buildReport(addr, legacyEventLabel(evt), rssi, adBytes))
            p = adStart + dataLen + 1
        }
        return out
    }

    /** Extended report: 24-byte header per report then data_len(1) at offset+23 + data. */
    private fun parseExtended(data: ByteArray, o: Int): List<AdvReport> {
        val num = data[o + 3].toInt() and 0xFF
        val out = mutableListOf<AdvReport>()
        var p = o + 4
        repeat(num) {
            if (p + 24 > data.size) return out
            val addr = formatBtAddressReversed(data.copyOfRange(p + 3, p + 9))
            val rssi = data[p + 18].toInt().toByte().toInt()
            val dataLen = data[p + 23].toInt() and 0xFF
            val adStart = p + 24
            if (adStart + dataLen > data.size) return out
            val adBytes = data.copyOfRange(adStart, adStart + dataLen)
            out.add(buildReport(addr, "Ext Adv", rssi, adBytes))
            p = adStart + dataLen
        }
        return out
    }

    private fun legacyEventLabel(evt: Int): String = when (evt) {
        0x00 -> "ADV_IND"
        0x01 -> "ADV_DIRECT_IND"
        0x02 -> "ADV_SCAN_IND"
        0x03 -> "ADV_NONCONN_IND"
        0x04 -> "SCAN_RSP"
        else -> "ADV 0x%02X".format(evt)
    }

    private fun buildReport(addr: String, eventLabel: String, rssi: Int, adBytes: ByteArray): AdvReport {
        val (name, summary) = decodeAd(adBytes)
        val full = buildString {
            append(summary)
            if (isNotEmpty()) append(" | ")
            append("RSSI ").append(rssi).append("dBm")
        }
        return AdvReport(addr, eventLabel, rssi, name, adBytes, full)
    }

    /** Walk AD structures, returning (localName, humanSummary). */
    private fun decodeAd(ad: ByteArray): Pair<String?, String> {
        val parts = mutableListOf<String>()
        var name: String? = null
        var q = 0
        while (q + 1 < ad.size) {
            val len = ad[q].toInt() and 0xFF
            if (len == 0) break
            val type = ad[q + 1].toInt() and 0xFF
            val vEnd = minOf(q + 1 + len, ad.size)
            val v = ad.copyOfRange(q + 2, vEnd)
            when (type) {
                0x01 -> if (v.isNotEmpty()) parts.add("Flags 0x%02X".format(v[0].toInt() and 0xFF))
                0x02, 0x03 -> parts.add("Svc16 " + uuid16List(v))
                0x06, 0x07 -> parts.add("Svc128 " + (if (v.size >= 16) GattParser.uuidLabel(hex128(v)) else "?"))
                0x08 -> { name = str(v); parts.add("Name(short) $name") }
                0x09 -> { name = str(v); parts.add("Name $name") }
                0x0A -> if (v.isNotEmpty()) parts.add("TxPwr %ddBm".format(v[0].toInt().toByte().toInt()))
                0x16 -> if (v.size >= 2) parts.add("SvcData 0x%02X%02X".format(v[1], v[0]))
                0x19 -> if (v.size >= 2) parts.add("Appearance 0x%02X%02X".format(v[1], v[0]))
                0xFF -> if (v.size >= 2) parts.add("Mfr 0x%02X%02X".format(v[1], v[0]) +
                    (if (v.size > 2) ":" + v.copyOfRange(2, v.size).joinToString("") { "%02X".format(it) } else ""))
                else -> parts.add("Type0x%02X".format(type))
            }
            q += 1 + len
        }
        return name to parts.joinToString(" | ")
    }

    private fun uuid16List(v: ByteArray): String {
        val out = mutableListOf<String>()
        var i = 0
        while (i + 1 < v.size) {
            out.add("0x%02X%02X".format(v[i + 1], v[i]))
            i += 2
        }
        return out.joinToString(",")
    }

    private fun hex128(v: ByteArray): String {
        val be = v.copyOfRange(0, 16).reversedArray()
        val h = be.joinToString("") { "%02x".format(it) }
        return "%s-%s-%s-%s-%s".format(
            h.substring(0, 8), h.substring(8, 12), h.substring(12, 16), h.substring(16, 20), h.substring(20, 32),
        )
    }

    private fun str(v: ByteArray): String = String(v).trim { it <= ' ' }
}
