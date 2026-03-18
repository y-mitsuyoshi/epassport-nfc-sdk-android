package com.example.epassport.domain.model

/**
 * DG2（顔写真）をパースした結果。
 * 機密データのため、利用完了後は [clear] を呼び出してメモリをゼロクリアすること。
 */
class Dg2Data(
    val faceImageBytes: ByteArray,  // JPEG or JP2 raw bytes
    val mimeType: String            // "image/jpeg" or "image/jp2"
) {
    fun clear() {
        faceImageBytes.fill(0)
    }

    override fun toString(): String = "Dg2Data(mimeType=$mimeType, size=${faceImageBytes.size} bytes)"
}
