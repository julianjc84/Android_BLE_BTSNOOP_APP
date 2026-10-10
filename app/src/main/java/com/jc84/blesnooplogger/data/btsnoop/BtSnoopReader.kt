package com.jc84.blesnooplogger.data.btsnoop

import com.jc84.blesnooplogger.data.util.readUint32BE
import com.jc84.blesnooplogger.data.util.readUint64BE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Incrementally parses a btsnoop HCI binary stream into records.
 * Designed to work with a continuous `tail -f` stream from root shell.
 */
class BtSnoopReader {

    /**
     * Read btsnoop records from a continuous input stream.
     * First validates the 16-byte file header, then emits records as they arrive.
     */
    fun readStream(input: InputStream): Flow<BtSnoopRecord> = flow {
        // Read and validate file header
        val header = readExact(input, BtSnoopConstants.FILE_HEADER_SIZE)
            ?: throw BtSnoopException("Stream ended before file header")

        validateFileHeader(header)
        Timber.i("Bluetooth: btsnoop file header validated")

        // Continuously read records
        while (true) {
            val record = readRecord(input) ?: break
            emit(record)
        }

        Timber.i("Bluetooth: btsnoop stream ended")
    }.flowOn(Dispatchers.IO)

    private fun validateFileHeader(header: ByteArray) {
        // Check magic bytes
        for (i in BtSnoopConstants.FILE_MAGIC.indices) {
            if (header[i] != BtSnoopConstants.FILE_MAGIC[i]) {
                throw BtSnoopException("Invalid btsnoop magic at byte $i")
            }
        }

        val buf = ByteBuffer.wrap(header, 8, 8).order(ByteOrder.BIG_ENDIAN)
        val version = buf.getInt().toLong() and 0xFFFFFFFFL
        val datalink = buf.getInt().toLong() and 0xFFFFFFFFL

        if (version != 1L) {
            Timber.w("Bluetooth: unexpected btsnoop version: $version")
        }
        if (datalink != BtSnoopConstants.DATALINK_H4) {
            throw BtSnoopException("Unsupported datalink type: $datalink (expected ${BtSnoopConstants.DATALINK_H4})")
        }
    }

    private fun readRecord(input: InputStream): BtSnoopRecord? {
        val headerBytes = readExact(input, BtSnoopConstants.RECORD_HEADER_SIZE) ?: return null
        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)

        val originalLength = buf.readUint32BE().toInt()
        val includedLength = buf.readUint32BE().toInt()
        val flags = buf.readUint32BE().toInt()
        @Suppress("UNUSED_VARIABLE")
        val drops = buf.readUint32BE()
        val timestampUs = buf.readUint64BE()

        if (includedLength < 0 || includedLength > 65535) {
            Timber.w("Bluetooth: suspicious record length: $includedLength, skipping")
            return null
        }

        val data = readExact(input, includedLength) ?: return null

        return BtSnoopRecord(
            originalLength = originalLength,
            includedLength = includedLength,
            flags = flags,
            timestampUs = timestampUs,
            data = data,
        )
    }

    /**
     * Read exactly [count] bytes from stream, blocking until available.
     * Returns null on EOF.
     */
    private fun readExact(input: InputStream, count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read == -1) return null
            offset += read
        }
        return buffer
    }
}

class BtSnoopException(message: String) : Exception(message)
