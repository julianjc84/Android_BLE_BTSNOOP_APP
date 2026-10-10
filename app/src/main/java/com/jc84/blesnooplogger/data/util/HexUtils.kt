package com.jc84.blesnooplogger.data.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.toHexString(): String =
    joinToString(" ") { "%02X".format(it) }

fun ByteArray.toPrintableAscii(): String =
    map { b -> if (b in 0x20..0x7E) b.toInt().toChar() else '.' }
        .joinToString("")

fun ByteBuffer.readUint16LE(): Int {
    order(ByteOrder.LITTLE_ENDIAN)
    return getShort().toInt() and 0xFFFF
}

fun ByteBuffer.readUint16BE(): Int {
    order(ByteOrder.BIG_ENDIAN)
    return getShort().toInt() and 0xFFFF
}

fun ByteBuffer.readUint32BE(): Long {
    order(ByteOrder.BIG_ENDIAN)
    return getInt().toLong() and 0xFFFFFFFFL
}

fun ByteBuffer.readUint64BE(): Long {
    order(ByteOrder.BIG_ENDIAN)
    return getLong()
}

fun formatBtAddress(bytes: ByteArray): String {
    require(bytes.size == 6) { "BT address must be 6 bytes" }
    return bytes.joinToString(":") { "%02X".format(it) }
}

fun formatBtAddressReversed(bytes: ByteArray): String {
    require(bytes.size == 6) { "BT address must be 6 bytes" }
    return bytes.reversedArray().joinToString(":") { "%02X".format(it) }
}
