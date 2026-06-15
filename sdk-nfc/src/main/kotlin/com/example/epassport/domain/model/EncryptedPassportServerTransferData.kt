package com.example.epassport.domain.model

/**
 * サーバー公開鍵で暗号化されたパスポート転送データ。
 *
 * JWE Compact Serialization 形式の文字列を保持する。
 * 復号はサーバー側の秘密鍵で行う。
 */
data class EncryptedPassportServerTransferData(
    val jwe: String
)
