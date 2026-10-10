package com.jc84.blesnooplogger.data.btsnoop

/**
 * One btsnoop HCI record, parsed from the binary log.
 *
 * @param originalLength original packet length
 * @param includedLength bytes actually captured
 * @param flags bit 0: direction (0=sent, 1=received), bit 1: command/event flag
 * @param timestampUs microseconds since btsnoop epoch
 * @param data raw HCI packet bytes (H4 type byte NOT included — btsnoop strips it)
 */
data class BtSnoopRecord(
    val originalLength: Int,
    val includedLength: Int,
    val flags: Int,
    val timestampUs: Long,
    val data: ByteArray,
) {
    /** True if this is a received packet (host ← controller) */
    val isReceived: Boolean get() = (flags and 0x01) != 0

    /** True if this is a sent packet (host → controller) */
    val isSent: Boolean get() = !isReceived

    /** True if this is a command or event (bit 1 set) */
    val isCommandOrEvent: Boolean get() = (flags and 0x02) != 0

    /** Derive H4 packet type from flags:
     *  - Command/Event + Sent = HCI Command (0x01)
     *  - Command/Event + Received = HCI Event (0x04)
     *  - Data + Sent/Received = ACL Data (0x02)
     */
    val h4Type: Int
        get() = when {
            isCommandOrEvent && isSent -> BtSnoopConstants.H4_CMD
            isCommandOrEvent && isReceived -> BtSnoopConstants.H4_EVT
            else -> BtSnoopConstants.H4_ACL
        }

    /** Unix timestamp in milliseconds */
    val unixTimestampMs: Long
        get() = (timestampUs - BtSnoopConstants.BTSNOOP_EPOCH_DELTA_US) / 1000

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BtSnoopRecord) return false
        return originalLength == other.originalLength &&
                includedLength == other.includedLength &&
                flags == other.flags &&
                timestampUs == other.timestampUs &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = originalLength
        result = 31 * result + includedLength
        result = 31 * result + flags
        result = 31 * result + timestampUs.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
