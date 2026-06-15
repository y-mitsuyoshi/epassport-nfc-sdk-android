package com.example.epassport.domain.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * サーバー側でアクティブ認証（クローン検知）を検証するために収集された生データ。
 */
@OptIn(ExperimentalEncodingApi::class)
data class ActiveAuthenticationData(
    val publicKeyInfo: ByteArray,  // DG15から取得した公開鍵のASN.1 DERエンコード生バイト
    val challenge: ByteArray,      // 署名に使用した乱数（8バイト）
    val signature: ByteArray       // チップが秘密鍵で署名して返してきた生バイナリ
) {
    /**
     * サーバー送信用のBase64変換されたデータ構造にマッピングします。
     */
    fun toBase64Map(): Map<String, String> {
        return mapOf(
            "publicKeyInfo" to Base64.encode(publicKeyInfo),
            "challenge" to Base64.encode(challenge),
            "signature" to Base64.encode(signature)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ActiveAuthenticationData

        if (!publicKeyInfo.contentEquals(other.publicKeyInfo)) return false
        if (!challenge.contentEquals(other.challenge)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKeyInfo.contentHashCode()
        result = 31 * result + challenge.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    /** メモリからのゼロクリア */
    fun clear() {
        publicKeyInfo.fill(0)
        challenge.fill(0)
        signature.fill(0)
    }
}
