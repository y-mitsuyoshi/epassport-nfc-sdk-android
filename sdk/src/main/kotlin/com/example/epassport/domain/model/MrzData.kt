package com.example.epassport.domain.model

import com.example.epassport.util.CryptoUtils
import java.security.MessageDigest

/**
 * MRZ の 3 要素を保持するデータクラス。
 * BAC キーの導出ロジックを含む。
 */
data class MrzData(
    val documentNumber: String,   // 最大9文字
    val dateOfBirth: String,      // YYMMDD
    val dateOfExpiry: String      // YYMMDD
) {
    val mrzInformation: String get() {
        val docNum = padString(documentNumber.uppercase(), 9)
        val docNumCheckDigit = computeCheckDigit(docNum)
        val dob = padString(dateOfBirth, 6)
        val dobCheckDigit = computeCheckDigit(dob)
        val doe = padString(dateOfExpiry, 6)
        val doeCheckDigit = computeCheckDigit(doe)
        return "$docNum$docNumCheckDigit$dob$dobCheckDigit$doe$doeCheckDigit"
    }

    /** BAC 用の K_seed を導出 (SHA-1 の先頭16バイト) */
    fun deriveBacKeySeed(): ByteArray {
        val mrzInfo = mrzInformation
        System.err.println("MRZ Info: '$mrzInfo'")

        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(mrzInfo.toByteArray(Charsets.UTF_8))
        val hash = digest.digest()

        // ICAO 9303 Part 11: K_seed は SHA-1 ハッシュの先頭16バイト
        return hash.sliceArray(0..15)
    }

    /**
     * K_seed から暗号鍵 (K_Enc) と MAC 鍵 (K_Mac) のペアを生成する。
     * ICAO 9303 Appendix D に基づく 3DES 鍵生成プロトコル。
     */
    fun deriveBacKeys(): BacKey {
        val kSeed = deriveBacKeySeed()
        val encKey = deriveKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x01))
        val macKey = deriveKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x02))
        return BacKey(encKey, macKey)
    }

    /**
     * K_seed とカウンタ (c) から 3DES 鍵を導出する。
     */
    private fun deriveKey(kSeed: ByteArray, c: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(kSeed)
        digest.update(c)
        val hash = digest.digest()

        // 16バイトの鍵とする (Ka, Kb)
        val keyBytes = hash.sliceArray(0..15)

        // パリティビットを調整する
        CryptoUtils.adjustParity(keyBytes)

        return keyBytes
    }

    /** ICAO 9303 チェックディジット計算 */
    fun computeCheckDigit(input: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        for (i in input.indices) {
            val char = input[i]
            val value = when {
                char in '0'..'9' -> char - '0'
                char in 'A'..'Z' -> char - 'A' + 10
                char == '<' -> 0
                else -> throw IllegalArgumentException("Invalid MRZ character: $char")
            }
            sum += value * weights[i % 3]
        }
        return sum % 10
    }

    private fun padString(input: String, length: Int): String {
        return input.padEnd(length, '<').substring(0, length)
    }

}
