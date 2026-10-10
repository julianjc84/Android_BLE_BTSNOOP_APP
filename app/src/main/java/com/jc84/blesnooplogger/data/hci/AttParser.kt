package com.jc84.blesnooplogger.data.hci

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed ATT PDU.
 */
data class AttPdu(
    val opcode: Int,
    val opcodeName: String,
    val attHandle: Int?,
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttPdu) return false
        return opcode == other.opcode && attHandle == other.attHandle && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = opcode
        result = 31 * result + (attHandle ?: 0)
        result = 31 * result + value.contentHashCode()
        return result
    }
}

object AttParser {

    /**
     * Parse an ATT PDU from L2CAP payload.
     * ATT PDU: opcode(1) + handle(2, for applicable opcodes) + value(variable)
     */
    fun parse(attPayload: ByteArray): AttPdu? {
        if (attPayload.isEmpty()) return null

        val opcode = attPayload[0].toInt() and 0xFF
        val opcodeName = AttOpcode.displayNameForCode(opcode)

        // Extract handle and value based on opcode
        val (attHandle, value) = extractHandleAndValue(opcode, attPayload)

        return AttPdu(
            opcode = opcode,
            opcodeName = opcodeName,
            attHandle = attHandle,
            value = value,
        )
    }

    private fun extractHandleAndValue(opcode: Int, data: ByteArray): Pair<Int?, ByteArray> {
        // Opcodes that have a handle at bytes 1-2
        val hasHandle = when (opcode) {
            0x01 -> data.size >= 5 // Error Response: handle at [1:2], error at [3], errHandle at [4]
            0x04, 0x06, 0x08, 0x10 -> true // Find/Read by range: startHandle at [1:2]
            0x0A, 0x0C -> true // Read Req, Read Blob Req: handle at [1:2]
            0x0E -> false // Read Multiple: list of handles
            0x12, 0x52, 0xD2, 0x16 -> true // Write Req/Cmd/Signed/Prepare: handle at [1:2]
            0x1B, 0x1D -> true // Notification, Indication: handle at [1:2]
            else -> false
        }

        return if (hasHandle && data.size >= 3) {
            val handle = ByteBuffer.wrap(data, 1, 2).order(ByteOrder.LITTLE_ENDIAN)
                .getShort().toInt() and 0xFFFF
            val value = if (data.size > 3) data.copyOfRange(3, data.size) else ByteArray(0)
            handle to value
        } else {
            val value = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)
            null to value
        }
    }
}
