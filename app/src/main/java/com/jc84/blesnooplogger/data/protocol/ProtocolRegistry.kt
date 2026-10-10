package com.jc84.blesnooplogger.data.protocol

/**
 * Ordered set of [ProtocolIdentifier]s consulted when labelling a device's traffic.
 *
 * The capture pipeline calls [identify] only — it never references a specific protocol.
 * Add support for new device families by registering an identifier here; ships with EUC
 * identification enabled by default.
 */
object ProtocolRegistry {
    private val identifiers = mutableListOf<ProtocolIdentifier>(EucProtocolIdentifier)

    /** Append an identifier. Later registrations are consulted after the defaults. */
    fun register(identifier: ProtocolIdentifier) {
        identifiers.add(identifier)
    }

    /** First non-null label from a registered identifier, or null if unrecognized. */
    fun identify(data: ByteArray): String? {
        for (id in identifiers) id.identify(data)?.let { return it }
        return null
    }
}
