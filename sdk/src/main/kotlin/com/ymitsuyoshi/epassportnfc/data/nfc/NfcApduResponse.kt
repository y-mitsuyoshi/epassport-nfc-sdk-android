package com.ymitsuyoshi.epassportnfc.data.nfc

import com.ymitsuyoshi.epassportnfc.core.EPassportException

/**
 * Represents an ISO 7816-4 APDU response received from the passport chip.
 *
 * @property data The optional response data body.
 * @property sw1 Status word byte 1.
 * @property sw2 Status word byte 2.
 */
data class NfcApduResponse(
    val data: ByteArray,
    val sw1: Byte,
    val sw2: Byte,
) {
    /** The combined 16-bit status word (SW1 || SW2). */
    val statusWord: Int get() = ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)

    /** Returns true when the status word indicates success (0x9000). */
    val isSuccess: Boolean get() = statusWord == STATUS_SUCCESS

    /**
     * Throws an [EPassportException.NfcCommunicationException] unless [isSuccess] is true.
     *
     * @param context A description of the operation being performed (used in the error message).
     */
    fun requireSuccess(context: String = "APDU command") {
        if (!isSuccess) {
            throw EPassportException.NfcCommunicationException(
                "$context failed with status word: ${statusWord.toHexStatusWord()}"
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NfcApduResponse) return false
        return data.contentEquals(other.data) && sw1 == other.sw1 && sw2 == other.sw2
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sw1.hashCode()
        result = 31 * result + sw2.hashCode()
        return result
    }

    private fun Int.toHexStatusWord(): String = "0x%04X".format(this)

    companion object {
        const val STATUS_SUCCESS = 0x9000
        const val STATUS_FILE_NOT_FOUND = 0x6A82
        const val STATUS_SECURITY_NOT_SATISFIED = 0x6982
        const val STATUS_WRONG_DATA = 0x6A80

        /**
         * Parses a raw byte array from the NFC layer into an [NfcApduResponse].
         *
         * @param rawResponse The raw bytes received from [android.nfc.tech.IsoDep.transceive].
         * @throws EPassportException.NfcCommunicationException if the response is too short.
         */
        fun fromBytes(rawResponse: ByteArray): NfcApduResponse {
            if (rawResponse.size < 2) {
                throw EPassportException.NfcCommunicationException(
                    "APDU response too short: ${rawResponse.size} byte(s)"
                )
            }
            val data = rawResponse.copyOf(rawResponse.size - 2)
            val sw1 = rawResponse[rawResponse.size - 2]
            val sw2 = rawResponse[rawResponse.size - 1]
            return NfcApduResponse(data = data, sw1 = sw1, sw2 = sw2)
        }
    }
}
