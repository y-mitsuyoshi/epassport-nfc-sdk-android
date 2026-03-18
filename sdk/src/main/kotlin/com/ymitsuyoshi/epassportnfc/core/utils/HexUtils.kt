package com.ymitsuyoshi.epassportnfc.core.utils

/**
 * Utility functions for hexadecimal encoding and decoding used throughout
 * APDU command/response handling and cryptographic operations.
 */
object HexUtils {

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /**
     * Converts a byte array to its uppercase hexadecimal string representation.
     *
     * @param bytes The byte array to convert.
     * @return A hex string with no separator (e.g. "DEADBEEF").
     */
    fun toHexString(bytes: ByteArray): String {
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val octet = byte.toInt()
            result.append(HEX_CHARS[(octet shr 4) and 0x0F])
            result.append(HEX_CHARS[octet and 0x0F])
        }
        return result.toString()
    }

    /**
     * Parses a hexadecimal string into a byte array.
     *
     * @param hex A string of hexadecimal characters (case-insensitive, even length).
     * @return The decoded byte array.
     * @throws IllegalArgumentException if the string has an odd length or contains non-hex characters.
     */
    fun fromHexString(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even number of characters, got: ${hex.length}" }
        val normalized = hex.uppercase()
        return ByteArray(normalized.length / 2) { i ->
            val high = hexCharToInt(normalized[i * 2])
            val low = hexCharToInt(normalized[i * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    /**
     * Formats a byte array as a hex string with a colon separator for display purposes
     * (e.g. "DE:AD:BE:EF").
     */
    fun toFormattedHexString(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it) }

    private fun hexCharToInt(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Invalid hex character: '$c'")
    }
}
