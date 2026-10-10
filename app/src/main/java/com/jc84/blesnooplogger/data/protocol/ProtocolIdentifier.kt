package com.jc84.blesnooplogger.data.protocol

/**
 * Recognizes an application/device protocol from its payload bytes.
 *
 * Implementations are registered in [ProtocolRegistry]; the capture pipeline itself
 * knows nothing about any specific device family, keeping the app protocol-agnostic.
 */
fun interface ProtocolIdentifier {
    /** A short label (e.g. "InMotion") if this identifier recognizes [data], else null. */
    fun identify(data: ByteArray): String?
}
