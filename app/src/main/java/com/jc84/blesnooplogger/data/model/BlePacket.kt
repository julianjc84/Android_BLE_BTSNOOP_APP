package com.jc84.blesnooplogger.data.model

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI-ready decoded BLE/BT packet.
 *
 * `formattedTime` is computed once at construction so the list item composable
 * doesn't have to run SimpleDateFormat on every recomposition.
 */
@Immutable
data class BlePacket(
    val index: Long,
    val timestampMs: Long,
    val direction: Direction,
    val deviceAddress: String,
    val packetType: String,
    val opcode: String,
    val attHandle: Int?,
    /** Resolved GATT characteristic/descriptor name for [attHandle], e.g. "Battery Level (0x2A19)". */
    val handleName: String? = null,
    val dataHex: String,
    val dataAscii: String,
    val rawDataBytes: ByteArray,
    val protocolLabel: String = "",
    val formattedTime: String = formatTime(timestampMs),
) {
    enum class Direction(val label: String) {
        TX("TX"),
        RX("RX"),
        EVENT("EV"),
    }

    val handleHex: String
        get() = attHandle?.let { "0x%04X".format(it) } ?: ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlePacket) return false
        return index == other.index
    }

    override fun hashCode(): Int = index.hashCode()

    companion object {
        // SimpleDateFormat isn't thread-safe; one per thread keeps it cheap.
        private val timeFormat = ThreadLocal.withInitial {
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }

        fun formatTime(timestampMs: Long): String =
            timeFormat.get()!!.format(Date(timestampMs))
    }
}
