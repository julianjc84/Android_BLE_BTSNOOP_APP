package com.jc84.blesnooplogger.data.btsnoop

object BtSnoopConstants {
    /** "btsnoop\0" file identification pattern */
    val FILE_MAGIC = byteArrayOf(
        0x62, 0x74, 0x73, 0x6E, 0x6F, 0x6F, 0x70, 0x00
    )

    /** File header size: 8 (magic) + 4 (version) + 4 (datalink type) = 16 bytes */
    const val FILE_HEADER_SIZE = 16

    /** Each record header: 4 (original length) + 4 (included length) + 4 (flags) + 4 (drops) + 8 (timestamp) = 24 bytes */
    const val RECORD_HEADER_SIZE = 24

    /** btsnoop epoch: Jan 1, 0 AD. Offset from Unix epoch in microseconds. */
    const val BTSNOOP_EPOCH_DELTA_US = 0x00dcddb30f2f8000L

    /** HCI packet types (H4 transport) */
    const val H4_CMD = 0x01
    const val H4_ACL = 0x02
    const val H4_SCO = 0x03
    const val H4_EVT = 0x04

    /** Datalink type for HCI UART (H4) */
    const val DATALINK_H4 = 1002L
}
