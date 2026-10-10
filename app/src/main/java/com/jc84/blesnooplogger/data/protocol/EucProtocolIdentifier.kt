package com.jc84.blesnooplogger.data.protocol

/**
 * Identifies electric-unicycle (EUC) firmware protocols by their framing magic bytes.
 *
 * This object is the single place brand-specific EUC knowledge lives. Remove it from
 * [ProtocolRegistry] (or add more identifiers alongside it) without touching the core.
 */
object EucProtocolIdentifier : ProtocolIdentifier {
    override fun identify(data: ByteArray): String? {
        if (data.size < 2) return null
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        return when ((b0 shl 8) or b1) {
            0x55AA -> "Gotway/Begode"
            0xDC5A -> "Veteran"
            0xAA55 -> "KingSong"
            else -> if (b0 == 0xAA && b1 == 0xAA) "InMotion" else null
        }
    }
}
