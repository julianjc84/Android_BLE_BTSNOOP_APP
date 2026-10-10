package com.jc84.blesnooplogger.data.hci

/**
 * Names for common Bluetooth SIG assigned 16-bit UUIDs (services, characteristics,
 * descriptors and GATT declarations). Used to label GATT handles with human-readable
 * names instead of raw values. Not exhaustive — a practical, widely-seen subset.
 *
 * Source: Bluetooth SIG Assigned Numbers.
 */
object BluetoothUuids {

    private val names: Map<Int, String> = mapOf(
        // --- GATT declarations / descriptors ---
        0x2800 to "Primary Service",
        0x2801 to "Secondary Service",
        0x2802 to "Include",
        0x2803 to "Characteristic",
        0x2900 to "Characteristic Extended Properties",
        0x2901 to "Characteristic User Description",
        0x2902 to "Client Characteristic Config",
        0x2903 to "Server Characteristic Config",
        0x2904 to "Characteristic Presentation Format",
        0x2905 to "Characteristic Aggregate Format",

        // --- Services ---
        0x1800 to "Generic Access",
        0x1801 to "Generic Attribute",
        0x1802 to "Immediate Alert",
        0x1803 to "Link Loss",
        0x1804 to "Tx Power",
        0x1805 to "Current Time",
        0x180A to "Device Information",
        0x180D to "Heart Rate",
        0x180F to "Battery",
        0x1810 to "Blood Pressure",
        0x1812 to "Human Interface Device",
        0x1816 to "Cycling Speed and Cadence",
        0x1818 to "Cycling Power",
        0x1819 to "Location and Navigation",
        0x181A to "Environmental Sensing",
        0x181C to "User Data",
        0x181D to "Weight Scale",
        0x1826 to "Fitness Machine",
        0x183B to "Binary Sensor",
        0xFE59 to "Nordic DFU",

        // --- Characteristics ---
        0x2A00 to "Device Name",
        0x2A01 to "Appearance",
        0x2A04 to "Peripheral Preferred Connection Params",
        0x2A05 to "Service Changed",
        0x2A19 to "Battery Level",
        0x2A23 to "System ID",
        0x2A24 to "Model Number",
        0x2A25 to "Serial Number",
        0x2A26 to "Firmware Revision",
        0x2A27 to "Hardware Revision",
        0x2A28 to "Software Revision",
        0x2A29 to "Manufacturer Name",
        0x2A2B to "Current Time",
        0x2A37 to "Heart Rate Measurement",
        0x2A38 to "Body Sensor Location",
        0x2A63 to "Cycling Power Measurement",
        0x2A5B to "CSC Measurement",
        0x2A6D to "Pressure",
        0x2A6E to "Temperature",
        0x2A6F to "Humidity",
        0x2AB4 to "Uncertainty",
    )

    /** Human name for a 16-bit assigned UUID, or null if unknown. */
    fun name(uuid16: Int): String? = names[uuid16 and 0xFFFF]
}
