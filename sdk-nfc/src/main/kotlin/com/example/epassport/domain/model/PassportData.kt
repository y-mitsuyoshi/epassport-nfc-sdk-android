package com.example.epassport.domain.model

import android.util.Base64

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
     */
    fun toServerTransferData(): PassportServerTransferData {
        val faceImageBase64 = dg2?.let {
            Base64.encodeToString(it.faceImageBytes, Base64.NO_WRAP)
        }
        return PassportServerTransferData(
            dg1 = dg1,
            faceImageBase64 = faceImageBase64,
            faceImageMimeType = dg2?.mimeType,
            activeAuthentication = activeAuthenticationData?.toBase64Map()
        )
    }
}

