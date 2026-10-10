package com.jc84.blesnooplogger.data.hci

/**
 * Minimal RFCOMM frame parser to extract the payload data from SPP connections.
 * RFCOMM runs over L2CAP and wraps serial data in frames.
 *
 * Frame format:
 *   address(1) + control(1) + length(1-2) + [credits(1)] + payload(N) + fcs(1)
 *
 * We only care about UIH frames (control = 0xEF or 0xFF) which carry data.
 */
object RfcommParser {

    /** UIH frame without poll bit */
    private const val CTRL_UIH = 0xEF
    /** UIH frame with poll bit (credit-based flow) */
    private const val CTRL_UIH_PF = 0xFF

    data class RfcommFrame(
        val dlci: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RfcommFrame) return false
            return dlci == other.dlci && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int = 31 * dlci + payload.contentHashCode()
    }

    /**
     * Try to parse an RFCOMM UIH data frame. Returns null if not a data frame
     * or if the frame is a control/signaling frame (DLCI 0).
     */
    fun parseDataFrame(l2capPayload: ByteArray): RfcommFrame? {
        if (l2capPayload.size < 4) return null

        val address = l2capPayload[0].toInt() and 0xFF
        val control = l2capPayload[1].toInt() and 0xFF

        // Only extract data from UIH frames
        if (control != CTRL_UIH && control != CTRL_UIH_PF) return null

        // DLCI from address byte: bits 2-7 (EA bit = bit 0, C/R = bit 1)
        val dlci = (address shr 2) and 0x3F

        // Skip DLCI 0 (multiplexer control channel)
        if (dlci == 0) return null

        // Length field: if bit 0 is 1, length is in bits 1-7 of one byte
        // If bit 0 is 0, length is 15 bits across two bytes
        var offset = 2
        val lengthByte = l2capPayload[offset].toInt() and 0xFF
        val length: Int
        if (lengthByte and 0x01 != 0) {
            // Short length (1 byte)
            length = lengthByte shr 1
            offset++
        } else {
            // Long length (2 bytes)
            if (l2capPayload.size < offset + 2) return null
            val secondByte = l2capPayload[offset + 1].toInt() and 0xFF
            length = (lengthByte shr 1) or (secondByte shl 7)
            offset += 2
        }

        // Credit byte present in UIH frames with PF bit set
        if (control == CTRL_UIH_PF) {
            offset++ // skip credit byte
        }

        if (length <= 0) return null
        val payloadEnd = minOf(offset + length, l2capPayload.size - 1) // -1 for FCS byte
        if (payloadEnd <= offset) return null

        val payload = l2capPayload.copyOfRange(offset, payloadEnd)

        return RfcommFrame(dlci = dlci, payload = payload)
    }
}
