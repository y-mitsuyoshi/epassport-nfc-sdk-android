package com.example.epassport.domain.model

import kotlinx.serialization.Serializable

/**
 * TRUSTDOCK バックエンドサーバーに転送するための、
 * シリアライズ（Base64化）済みのパスポートデータ。
 */
@Serializable
data class PassportServerTransferData(
    val dg1: Dg1Data,
    val faceImageBase64: String?,
    val faceImageMimeType: String?,
    val activeAuthentication: Map<String, String>?,
    val playIntegrityToken: String? = null
)
