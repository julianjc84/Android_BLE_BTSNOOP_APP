package com.jc84.blesnooplogger.data.hci

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed L2CAP frame.
 */
data class L2capFrame(
    val length: Int,
    val cid: Int,
    val payload: ByteArray,
) {
    /** ATT channel (CID 0x0004) */
    val isAtt: Boolean get() = cid == CID_ATT

    companion object {
        const val CID_ATT = 0x0004
        const val CID_LE_SIGNALING = 0x0005
        const val CID_SMP = 0x0006
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is L2capFrame) return false
        return cid == other.cid && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * cid + payload.contentHashCode()
}

object L2capParser {

    /**
     * Parse an L2CAP frame from ACL payload.
     * L2CAP basic header: length(2 LE) + CID(2 LE) + payload.
     */
    fun parse(aclPayload: ByteArray): L2capFrame? {
        if (aclPayload.size < 4) return null

        val buf = ByteBuffer.wrap(aclPayload, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
        val length = buf.getShort().toInt() and 0xFFFF
        val cid = buf.getShort().toInt() and 0xFFFF

        val payloadEnd = minOf(4 + length, aclPayload.size)
        val payload = aclPayload.copyOfRange(4, payloadEnd)

        return L2capFrame(length = length, cid = cid, payload = payload)
    }
}
