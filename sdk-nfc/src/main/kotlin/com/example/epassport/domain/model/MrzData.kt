package com.example.epassport.domain.model

import com.example.epassport.util.CryptoUtils
import java.security.MessageDigest
import java.util.Arrays

/**
 * MRZ の 3 要素を保持するクラス。
 *
 * 機密情報（PII）のヒープ残留を減らすため、各フィールドは [CharArray] で保持される。
 * 利用完了後は必ず [clear] を呼び出してメモリをゼロクリアすること。
 */
class MrzData(
    documentNumber: CharArray,   // 最大9文字
    dateOfBirth: CharArray,      // YYMMDD
    dateOfExpiry: CharArray      // YYMMDD
) {
    /** 文書番号（可変長）。外部からの改変を防ぐためコピーを保持する。 */
    val documentNumber: CharArray = documentNumber.copyOf()
    /** 生年月日（YYMMDD）。外部からの改変を防ぐためコピーを保持する。 */
    val dateOfBirth: CharArray = dateOfBirth.copyOf()
    /** 有効期限（YYMMDD）。外部からの改変を防ぐためコピーを保持する。 */
    val dateOfExpiry: CharArray = dateOfExpiry.copyOf()

    /** BAC 用 MRZ 情報（チェックディジット付き）。一時的な文字列として生成される。 */
    val mrzInformation: String get() {
        val docNum = padCharArray(documentNumber.uppercaseCharArray(), 9)
        val docNumCheckDigit = computeCheckDigit(docNum)
        val dob = padCharArray(dateOfBirth.copyOf(), 6)
        val dobCheckDigit = computeCheckDigit(dob)
        val doe = padCharArray(dateOfExpiry.copyOf(), 6)
        val doeCheckDigit = computeCheckDigit(doe)

        val result = StringBuilder(30)
            .append(docNum)
            .append(docNumCheckDigit)
            .append(dob)
            .append(dobCheckDigit)
            .append(doe)
            .append(doeCheckDigit)
            .toString()

        // 一時配列をゼロクリア
        Arrays.fill(docNum, '\u0000')
        Arrays.fill(dob, '\u0000')
        Arrays.fill(doe, '\u0000')

        return result
    }

    /** BAC 用の K_seed を導出 (SHA-1 の先頭16バイト) */
    fun deriveBacKeySeed(): ByteArray {
        val mrzInfo = mrzInformation
        // NOTE: mrzInfo はパスポート番号・生年月日・有効期限を含む機密情報であるため、
        // いかなるビルド構成でもログ出力してはならない。

        val bytes = mrzInfo.toByteArray(Charsets.UTF_8)
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(bytes)
            val hash = digest.digest()

            // ICAO 9303 Part 11: K_seed は SHA-1 ハッシュの先頭16バイト
            return hash.sliceArray(0..15)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * K_seed から暗号鍵 (K_Enc) と MAC 鍵 (K_Mac) のペアを生成する。
     * ICAO 9303 Appendix D に基づく 3DES 鍵生成プロトコル。
     */
    fun deriveBacKeys(): BacKey {
        val kSeed = deriveBacKeySeed()
        val encKey = deriveKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x01))
        val macKey = deriveKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x02))
        kSeed.fill(0)
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
    fun computeCheckDigit(input: CharArray): Int {
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

    /** @deprecated 後方互換のため残す。新規コードでは [computeCheckDigit] ([CharArray]) を推奨。 */
    fun computeCheckDigit(input: String): Int = computeCheckDigit(input.toCharArray())

    companion object {
        /**
         * ICAO Doc 9303 チェックディジット計算。
         * インスタンスを生成せずに利用可能。
         */
        @JvmStatic
        fun computeCheckDigitStatic(input: CharArray): Int {
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
    }

    /** 全フィールドをゼロクリアする。利用完了後に必ず呼び出すこと。 */
    fun clear() {
        Arrays.fill(documentNumber, '\u0000')
        Arrays.fill(dateOfBirth, '\u0000')
        Arrays.fill(dateOfExpiry, '\u0000')
    }

    private fun padCharArray(input: CharArray, length: Int): CharArray {
        require(input.size <= length) {
            "MRZ field size (${input.size}) exceeds maximum length $length"
        }
        if (input.size == length) return input
        return CharArray(length) { i -> if (i < input.size) input[i] else '<' }
    }

    private fun CharArray.uppercaseCharArray(): CharArray {
        return CharArray(size) { this[it].uppercaseChar() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MrzData) return false
        return documentNumber.contentEquals(other.documentNumber) &&
                dateOfBirth.contentEquals(other.dateOfBirth) &&
                dateOfExpiry.contentEquals(other.dateOfExpiry)
    }

    override fun hashCode(): Int {
        var result = documentNumber.contentHashCode()
        result = 31 * result + dateOfBirth.contentHashCode()
        result = 31 * result + dateOfExpiry.contentHashCode()
        return result
    }

    override fun toString(): String = "MrzData(***)"
}
