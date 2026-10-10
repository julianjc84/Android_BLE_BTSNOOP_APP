package com.jc84.blesnooplogger.data.csv

import com.jc84.blesnooplogger.data.model.BlePacket
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvWriter(private val capturesDir: File) {

    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var rowCount = 0

    companion object {
        private const val HEADER = "timestamp,direction,device_address,packet_type,opcode,handle,data_hex,data_ascii"
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        private val FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }

    fun open(prefix: String = "ble_capture", filename: String? = null): File {
        capturesDir.mkdirs()
        val finalName = filename ?: "${prefix}_${FILENAME_FORMAT.format(Date())}.csv"
        val file = File(capturesDir, finalName)
        writer = BufferedWriter(FileWriter(file))
        writer?.appendLine(HEADER)
        writer?.flush()
        currentFile = file
        rowCount = 0
        Timber.i("FileSystem: CSV opened: %s", file.absolutePath)
        return file
    }

    fun writePacket(packet: BlePacket) {
        val w = writer ?: return
        val timestamp = TIMESTAMP_FORMAT.format(Date(packet.timestampMs))
        val row = buildString {
            append(timestamp)
            append(',')
            append(packet.direction.label)
            append(',')
            append(packet.deviceAddress)
            append(',')
            append(packet.packetType)
            append(',')
            append(escapeCsv(packet.opcode))
            append(',')
            append(packet.handleHex)
            append(',')
            append(escapeCsv(packet.dataHex))
            append(',')
            append(escapeCsv(packet.dataAscii))
        }
        w.appendLine(row)
        rowCount++
        // Flush every 50 rows for durability
        if (rowCount % 50 == 0) {
            w.flush()
        }
    }

    fun close() {
        writer?.let { w ->
            try {
                w.flush()
                w.close()
                Timber.i("FileSystem: CSV closed, %d rows written to %s", rowCount, currentFile?.name)
            } catch (e: Exception) {
                Timber.e("FileSystem: error closing CSV: %s", e.message)
            }
        }
        writer = null
        currentFile = null
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
