package com.example.epassport.domain.model

import com.example.epassport.data.security.E2EECipher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.PublicKey

/**
 * SDK が最終的に返却するパスポートデータ
 */
data class PassportData(
    val dg1: Dg1Data,
    val dg2: Dg2Data? = null, // 顔写真が含まれない場合を考慮
    val activeAuthenticationData: ActiveAuthenticationData? = null
) {
    /**
     * TRUSTDOCK サーバーへの転送用にデータを Base64 シリアライズ形式に変換します。
     *
     * 変換後は内部の顔画像バイト配列をゼロクリアするため、呼び出し元は返却された
     * Base64 文字列を適切に扱うこと。
     */
    fun toServerTransferData(): PassportServerTransferData {
        val faceImageBase64 = dg2?.toBase64AndClear()
        return PassportServerTransferData(
            dg1 = dg1,
            faceImageBase64 = faceImageBase64,
            faceImageMimeType = dg2?.mimeType,
            activeAuthentication = activeAuthenticationData?.toBase64Map()
        )
    }

    /**
     * サーバー公開鍵で暗号化された転送データを生成する。
     *
     * 平文の転送データを JSON シリアライズした上で、AES-256-GCM + RSA-OAEP-256
     * によるハイブリッド暗号化（JWE 形式）を施す。復号はサーバー側の秘密鍵で行う。
     *
     * @param serverPublicKey サーバーの RSA 公開鍵
     * @return 暗号化された転送データ
     */
    fun toEncryptedServerTransferData(serverPublicKey: PublicKey): EncryptedPassportServerTransferData {
        val transferData = toServerTransferData()
        val plaintext = Json.encodeToString(transferData).toByteArray(Charsets.UTF_8)
        val jwe = E2EECipher.encrypt(plaintext, serverPublicKey)
        plaintext.fill(0)
        return EncryptedPassportServerTransferData(jwe)
    }

    /**
     * 機密なバイト配列データ（顔画像、Active Authentication 生データ）をゼロクリアする。
     * [Dg1Data] に含まれる文字列PII は [String] の不変性により完全な消去が困難なため、
     * ホストアプリ側でも速やかに参照を破棄することを推奨する。
     */
    fun clear() {
        dg2?.clear()
        activeAuthenticationData?.clear()
    }
}

