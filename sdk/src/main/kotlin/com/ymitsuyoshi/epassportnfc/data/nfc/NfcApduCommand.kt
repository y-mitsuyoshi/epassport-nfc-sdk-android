package com.ymitsuyoshi.epassportnfc.data.nfc

/**
 * Represents an ISO 7816-4 APDU (Application Protocol Data Unit) command sent to the passport chip.
 *
 * The command APDU has the format: CLA | INS | P1 | P2 [| Lc | Data] [| Le]
 *
 * @property cla Class byte indicating the command class.
 * @property ins Instruction byte identifying the command.
 * @property p1 First parameter byte.
 * @property p2 Second parameter byte.
 * @property data Optional command data field.
 * @property le Expected length of the response data field (0x00 = any length).
 */
data class NfcApduCommand(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val data: ByteArray? = null,
    val le: Int? = null,
) {
    /**
     * Serialises this command into a raw byte array ready to be transmitted via [android.nfc.tech.IsoDep].
     */
    fun toBytes(): ByteArray {
        val lc = data?.size ?: 0
        val hasData = lc > 0
        val hasLe = le != null

        val size = 4 + (if (hasData) 1 + lc else 0) + (if (hasLe) 1 else 0)
        val result = ByteArray(size)
        var offset = 0
        result[offset++] = cla
        result[offset++] = ins
        result[offset++] = p1
        result[offset++] = p2
        if (hasData) {
            result[offset++] = lc.toByte()
            data!!.copyInto(result, offset)
            offset += lc
        }
        if (le != null) {
            result[offset] = (le and 0xFF).toByte()
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NfcApduCommand) return false
        return cla == other.cla && ins == other.ins && p1 == other.p1 && p2 == other.p2 &&
            (data?.contentEquals(other.data ?: byteArrayOf()) ?: (other.data == null)) &&
            le == other.le
    }

    override fun hashCode(): Int {
        var result = cla.hashCode()
        result = 31 * result + ins.hashCode()
        result = 31 * result + p1.hashCode()
        result = 31 * result + p2.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + (le ?: 0)
        return result
    }

    companion object {
        /** SELECT application by AID */
        fun selectApplication(aid: ByteArray): NfcApduCommand =
            NfcApduCommand(cla = 0x00, ins = 0xA4.toByte(), p1 = 0x04, p2 = 0x0C, data = aid)

        /** SELECT elementary file by short file identifier */
        fun selectFile(fid: ByteArray): NfcApduCommand =
            NfcApduCommand(cla = 0x00, ins = 0xA4.toByte(), p1 = 0x02, p2 = 0x0C, data = fid)

        /** READ BINARY — read [length] bytes from [offset] */
        fun readBinary(offset: Int, length: Int): NfcApduCommand =
            NfcApduCommand(
                cla = 0x00,
                ins = 0xB0.toByte(),
                p1 = ((offset shr 8) and 0xFF).toByte(),
                p2 = (offset and 0xFF).toByte(),
                le = length,
            )

        /** GET CHALLENGE for BAC */
        val getChallenge: NfcApduCommand
            get() = NfcApduCommand(cla = 0x00, ins = 0x84.toByte(), p1 = 0x00, p2 = 0x00, le = 8)

        /** EXTERNAL AUTHENTICATE for BAC */
        fun externalAuthenticate(data: ByteArray): NfcApduCommand =
            NfcApduCommand(cla = 0x00, ins = 0x82.toByte(), p1 = 0x00, p2 = 0x00, data = data, le = 40)
    }
}
