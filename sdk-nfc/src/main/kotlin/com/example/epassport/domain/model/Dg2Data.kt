package com.example.epassport.domain.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * DG2（顔写真）をパースした結果。
 * 機密データのため、利用完了後は [clear] を呼び出してメモリをゼロクリアすること。
 */
@OptIn(ExperimentalEncodingApi::class)
class Dg2Data(
    faceImageBytes: ByteArray,  // JPEG or JP2 raw bytes
    val mimeType: String        // "image/jpeg" or "image/jp2"
) {
    /**
     * 外部からの改変を防ぐため、コンストラクタで受け取ったバイト配列のコピーを保持する。
     */
    val faceImageBytes: ByteArray = faceImageBytes.copyOf()

    /**
     * 顔画像を Base64 文字列に変換し、変換後に内部バイト配列をゼロクリアする。
     *
     * **注意**: 返却された [String] は不変であり、GC されるまでヒープに残る。
     * 可能であれば [toBase64CharArray] を使用し、利用後に [CharArray.fill] でクリアすること。
     */
    fun toBase64AndClear(): String {
        val encoded = Base64.encode(faceImageBytes)
        clear()
        return encoded
    }

    /**
     * 顔画像を Base64 エンコードした [CharArray] を返し、内部バイト配列をゼロクリアする。
     * 呼び出し側は利用後に返却された [CharArray] を `fill('\u0000')` などでクリアすること。
     */
    fun toBase64CharArray(): CharArray {
        val encoded = Base64.encode(faceImageBytes)
        clear()
        return encoded.toCharArray()
    }

    /** メモリからのゼロクリア */
    fun clear() {
        faceImageBytes.fill(0)
    }

    override fun toString(): String = "Dg2Data(mimeType=$mimeType, size=${faceImageBytes.size} bytes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Dg2Data) return false
        return mimeType == other.mimeType && faceImageBytes.contentEquals(other.faceImageBytes)
    }

    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + faceImageBytes.contentHashCode()
        return result
    }
}
