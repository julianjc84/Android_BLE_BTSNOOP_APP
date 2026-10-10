package com.jc84.blesnooplogger.data.hci

enum class AttOpcode(val code: Int, val displayName: String) {
    ERROR_RSP(0x01, "Error Response"),
    EXCHANGE_MTU_REQ(0x02, "Exchange MTU Req"),
    EXCHANGE_MTU_RSP(0x03, "Exchange MTU Rsp"),
    FIND_INFORMATION_REQ(0x04, "Find Info Req"),
    FIND_INFORMATION_RSP(0x05, "Find Info Rsp"),
    FIND_BY_TYPE_VALUE_REQ(0x06, "Find By Type Req"),
    FIND_BY_TYPE_VALUE_RSP(0x07, "Find By Type Rsp"),
    READ_BY_TYPE_REQ(0x08, "Read By Type Req"),
    READ_BY_TYPE_RSP(0x09, "Read By Type Rsp"),
    READ_REQ(0x0A, "Read Req"),
    READ_RSP(0x0B, "Read Rsp"),
    READ_BLOB_REQ(0x0C, "Read Blob Req"),
    READ_BLOB_RSP(0x0D, "Read Blob Rsp"),
    READ_MULTIPLE_REQ(0x0E, "Read Multiple Req"),
    READ_MULTIPLE_RSP(0x0F, "Read Multiple Rsp"),
    READ_BY_GROUP_TYPE_REQ(0x10, "Read By Group Req"),
    READ_BY_GROUP_TYPE_RSP(0x11, "Read By Group Rsp"),
    WRITE_REQ(0x12, "Write Req"),
    WRITE_RSP(0x13, "Write Rsp"),
    WRITE_CMD(0x52, "Write Cmd"),
    SIGNED_WRITE_CMD(0xD2, "Signed Write Cmd"),
    PREPARE_WRITE_REQ(0x16, "Prepare Write Req"),
    PREPARE_WRITE_RSP(0x17, "Prepare Write Rsp"),
    EXECUTE_WRITE_REQ(0x18, "Execute Write Req"),
    EXECUTE_WRITE_RSP(0x19, "Execute Write Rsp"),
    HANDLE_VALUE_NTF(0x1B, "Notification"),
    HANDLE_VALUE_IND(0x1D, "Indication"),
    HANDLE_VALUE_CFM(0x1E, "Confirmation"),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Int): AttOpcode? = byCode[code]

        fun displayNameForCode(code: Int): String =
            byCode[code]?.displayName ?: "ATT 0x%02X".format(code)
    }
}
