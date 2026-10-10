package com.jc84.blesnooplogger.data.hci

import java.util.Locale

/**
 * Extracts handle→UUID mappings from GATT discovery responses so that later
 * reads/writes/notifications can be labelled with a characteristic name instead of a
 * bare handle. Purely standard GATT — no device-specific knowledge.
 */
object GattParser {

    /**
     * Parse an ATT PDU for handle→UUID mappings it reveals. Returns (attHandle, uuid)
     * pairs, where uuid is a normalized string ("0x2A19" or a full 128-bit UUID).
     *
     * Handled responses:
     *  - Read By Group Type Rsp (0x11): primary-service discovery — service decl handle.
     *  - Read By Type Rsp (0x09): characteristic discovery — maps the *value* handle.
     *  - Find Information Rsp (0x05): descriptor discovery — handle → UUID.
     */
    fun parseDiscovery(attPayload: ByteArray): List<Pair<Int, String>> {
        if (attPayload.size < 2) return emptyList()
        return when (attPayload[0].toInt() and 0xFF) {
            0x11 -> parseReadByGroupTypeRsp(attPayload)
            0x09 -> parseReadByTypeRsp(attPayload)
            0x05 -> parseFindInfoRsp(attPayload)
            else -> emptyList()
        }
    }

    /** 0x11: length(1) + [handle(2) endGroup(2) uuid(2|16)] * n. Maps service decl handle→UUID. */
    private fun parseReadByGroupTypeRsp(p: ByteArray): List<Pair<Int, String>> {
        val elemLen = p[1].toInt() and 0xFF
        if (elemLen < 6) return emptyList()
        val out = mutableListOf<Pair<Int, String>>()
        var i = 2
        while (i + elemLen <= p.size) {
            val handle = le16(p, i)
            val uuid = uuidFromBytes(p.copyOfRange(i + 4, i + elemLen))
            out.add(handle to uuid)
            i += elemLen
        }
        return out
    }

    /**
     * 0x09: length(1) + [handle(2) value(...)] * n. For characteristic discovery each
     * value is properties(1) + valueHandle(2) + uuid(2|16); we map the value handle→UUID.
     * Element lengths of 7 (uuid16) or 21 (uuid128) identify a characteristic declaration.
     */
    private fun parseReadByTypeRsp(p: ByteArray): List<Pair<Int, String>> {
        val elemLen = p[1].toInt() and 0xFF
        if (elemLen != 7 && elemLen != 21) return emptyList() // not a characteristic declaration
        val out = mutableListOf<Pair<Int, String>>()
        var i = 2
        while (i + elemLen <= p.size) {
            // i = declaration handle, i+2 = properties, i+3 = value handle, i+5 = uuid
            val valueHandle = le16(p, i + 3)
            val uuid = uuidFromBytes(p.copyOfRange(i + 5, i + elemLen))
            out.add(valueHandle to uuid)
            i += elemLen
        }
        return out
    }

    /** 0x05: format(1) + [handle(2) uuid(2|16)] * n. format 0x01 = uuid16, 0x02 = uuid128. */
    private fun parseFindInfoRsp(p: ByteArray): List<Pair<Int, String>> {
        val format = p[1].toInt() and 0xFF
        val uuidLen = when (format) { 0x01 -> 2; 0x02 -> 16; else -> return emptyList() }
        val elemLen = 2 + uuidLen
        val out = mutableListOf<Pair<Int, String>>()
        var i = 2
        while (i + elemLen <= p.size) {
            val handle = le16(p, i)
            val uuid = uuidFromBytes(p.copyOfRange(i + 2, i + elemLen))
            out.add(handle to uuid)
            i += elemLen
        }
        return out
    }

    /** Human label for a normalized UUID, e.g. "Battery Level (0x2A19)" or the raw 128-bit UUID. */
    fun uuidLabel(uuid: String): String {
        if (uuid.startsWith("0x") && uuid.length <= 6) {
            val code = uuid.substring(2).toIntOrNull(16) ?: return uuid
            return BluetoothUuids.name(code)?.let { "$it ($uuid)" } ?: uuid
        }
        return uuid // 128-bit vendor-specific
    }

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    /**
     * UUID bytes are little-endian on the wire. Render as "0xXXXX" for 16-bit, and for
     * 128-bit collapse the Bluetooth Base UUID down to its 16-bit form when applicable.
     */
    private fun uuidFromBytes(le: ByteArray): String = when (le.size) {
        2 -> "0x%02X%02X".format(le[1], le[0])
        16 -> {
            val be = le.reversedArray() // big-endian
            if (isSigBase(be)) {
                "0x%02X%02X".format(be[2], be[3])
            } else {
                val hex = be.joinToString("") { "%02x".format(it) }
                "%s-%s-%s-%s-%s".format(
                    hex.substring(0, 8), hex.substring(8, 12), hex.substring(12, 16),
                    hex.substring(16, 20), hex.substring(20, 32),
                ).uppercase(Locale.US)
            }
        }
        else -> le.joinToString("") { "%02X".format(it) }
    }

    /** True if a big-endian 128-bit UUID matches 0000xxxx-0000-1000-8000-00805F9B34FB. */
    private fun isSigBase(be: ByteArray): Boolean {
        val base = intArrayOf(0x00, 0x00, -1, -1, 0x00, 0x00, 0x10, 0x00,
            0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB)
        for (i in base.indices) {
            if (base[i] == -1) continue // variable 16-bit bytes at index 2,3
            if ((be[i].toInt() and 0xFF) != base[i]) return false
        }
        return true
    }
}
