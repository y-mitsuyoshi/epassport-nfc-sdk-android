package com.example.epassport.domain.model

/**
 * BAC で使用する暗号鍵ペア (3DES)。
 *
 * セキュリティを考慮し、不要になった場合は速やかに [clear] を呼び出してヒープメモリからデータを削除すること。
 */
class BacKey(
    val encKey: ByteArray,  // K_Enc (16 bytes)
    val macKey: ByteArray   // K_Mac (16 bytes)
) {
    /** メモリからのゼロクリア */
    fun clear() {
        encKey.fill(0)
        macKey.fill(0)
    }

    // copy() 等でログ出力されないよう、念のため toString 等をオーバーライドする運用が良い
    override fun toString(): String = "BacKey(***)"
}
