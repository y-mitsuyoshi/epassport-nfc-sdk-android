package com.example.epassport.data.nfc

/**
 * APDU (Application Protocol Data Unit) コマンドとレスポンスのドメインモデルおよびファクトリ。
 */
object ApduCommand {

    /** eMRTD アプリケーションの SELECT コマンド (A0 00 00 02 47 10 01) */
    fun selectApplet(): ByteArray {
        return byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x0C.toByte(), 0x07.toByte(),
            0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x47.toByte(),
            0x10.toByte(), 0x01.toByte()
        )
    }

    /** GET CHALLENGE コマンド (8バイト取得) */
    fun getChallenge(): ByteArray {
        return byteArrayOf(0x00.toByte(), 0x84.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08.toByte())
    }

    /** MUTUAL AUTHENTICATE コマンド (EXTERNAL AUTHENTICATE) */
    fun mutualAuthenticate(authData: ByteArray): ByteArray {
        // Short APDU の Lc は 1 バイトのため最大 255 バイト。BAC では常に 40 バイトなので問題ない。
        require(authData.size <= 255) { "authData が Short APDU の Lc 上限 (255 bytes) を超えています" }
        val apdu = ByteArray(5 + authData.size + 1)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0x82.toByte()
        apdu[2] = 0x00.toByte()
        apdu[3] = 0x00.toByte()
        apdu[4] = authData.size.toByte()
        System.arraycopy(authData, 0, apdu, 5, authData.size)
        apdu[apdu.size - 1] = 0x28.toByte() // Le (40 bytes expected MAC) - but usually it's 0x28 for BAC
        return apdu
    }

    /** SELECT FILE コマンド */
    fun selectFile(fileId: ByteArray): ByteArray {
        val apdu = ByteArray(5 + fileId.size)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0xA4.toByte()
        apdu[2] = 0x02.toByte()
        apdu[3] = 0x0C.toByte()
        apdu[4] = fileId.size.toByte()
        System.arraycopy(fileId, 0, apdu, 5, fileId.size)
        return apdu
    }

    /** READ BINARY コマンド（ショートフォーマット：オフセットが15ビット以内の場合） */
    fun readBinary(offset: Int, le: Int): ByteArray {
        require(le in 0..256) { "Short APDU Le must be in 0..256 (0 means 256)" }
        val p1 = (offset ushr 8).toByte()
        val p2 = (offset and 0xFF).toByte()
        return byteArrayOf(
            0x00.toByte(), 0xB0.toByte(), p1, p2, le.toByte()
        )
    }

    /** READ BINARY コマンド（拡張レングス・フォーマット） */
    fun readBinaryExtended(offset: Int, le: Int): ByteArray {
        require(offset in 0..0x7FFF) { "Extended offset must be in 0..32767" }
        require(le in 0..65536) { "Extended Le must be in 0..65536 (0 means 65536)" }
        val p1 = (offset ushr 8).toByte()
        val p2 = (offset and 0xFF).toByte()
        return byteArrayOf(
            0x00.toByte(), 0xB0.toByte(), p1, p2,
            0x00.toByte(),
            (le ushr 8).toByte(),
            (le and 0xFF).toByte()
        )
    }

    /** INTERNAL AUTHENTICATE コマンド (Active Authentication用) */
    fun internalAuthenticate(challenge: ByteArray): ByteArray {
        val apdu = ByteArray(5 + challenge.size + 1)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0x88.toByte()
        apdu[2] = 0x00.toByte()
        apdu[3] = 0x00.toByte()
        apdu[4] = challenge.size.toByte()
        System.arraycopy(challenge, 0, apdu, 5, challenge.size)
        apdu[apdu.size - 1] = 0x00.toByte() // Le = 0x00 (256 bytes max response)
        return apdu
    }

    /** EF.CardAccess 選択コマンド */
    fun selectCardAccess(): ByteArray = selectFile(byteArrayOf(0x01, 0x1C))

    /** PACE MSE:Set AT コマンド */
    fun paceMseSetAt(oidBytes: ByteArray, passwordRef: Byte): ByteArray {
        // Data: 0x80 (OID tag) + len + OID + 0x83 (password reference tag) + len + ref
        val data = ByteArray(2 + oidBytes.size + 3)
        data[0] = 0x80.toByte()
        data[1] = oidBytes.size.toByte()
        System.arraycopy(oidBytes, 0, data, 2, oidBytes.size)
        data[2 + oidBytes.size] = 0x83.toByte()
        data[3 + oidBytes.size] = 0x01.toByte()
        data[4 + oidBytes.size] = passwordRef

        val apdu = ByteArray(5 + data.size)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0x22.toByte()
        apdu[2] = 0xC1.toByte()
        apdu[3] = 0xA4.toByte()
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)
        return apdu
    }

    /** PACE GENERAL AUTHENTICATE コマンド */
    fun generalAuthenticate(data: ByteArray): ByteArray {
        require(data.size <= 255) { "Short APDU data limit exceeded" }
        val apdu = ByteArray(5 + data.size + 1)
        apdu[0] = 0x10.toByte() // CLA: command chaining if needed
        apdu[1] = 0x86.toByte()
        apdu[2] = 0x00.toByte()
        apdu[3] = 0x00.toByte()
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)
        apdu[apdu.size - 1] = 0x00.toByte()
        return apdu
    }

    /** PACE Get Nonce (GENERAL AUTHENTICATE step 0) */
    fun paceGetNonce(): ByteArray {
        return generalAuthenticate(byteArrayOf(0x7C, 0x00))
    }
}

