package com.example.epassport.data.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.example.epassport.domain.exception.EPassportException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.util.Base64

/**
 * Google Play Integrity API と連携して、デバイスやアプリケーションの
 * 整合性を保証するための整合性トークン (Integrity Token) を取得するチェッカー。
 */
object PlayIntegrityChecker {

    /**
     * Google Play Integrity API を呼び出し、整合性トークンを取得します。
     *
     * @param context Android の Context
     * @param cloudProjectNumber Google Cloud Console から取得したプロジェクト番号（10進数数値の Long）
     * @param challenge サーバーから発行されたチャレンジバイト配列。リプレイ攻撃防止のため nonce のハッシュ生成に使用します。
     * @return 整合性トークン文字列
     */
    suspend fun requestToken(
        context: Context,
        cloudProjectNumber: Long,
        challenge: ByteArray
    ): String = suspendCancellableCoroutine { continuation ->
        try {
            val integrityManager = IntegrityManagerFactory.create(context.applicationContext)
            
            // リプレイ攻撃および偽装対策のため、チャレンジの SHA-256 ハッシュから
            // URL-safe かつパディングなしの Base64 文字列として nonce を構築します。
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(challenge)
            val nonce = Base64.encodeToString(
                hash,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )

            val request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .setNonce(nonce)
                .build()

            integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener { response ->
                    continuation.resume(response.token())
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(
                        EPassportException("Google Play Integrity API request failed", exception)
                    )
                }
        } catch (e: Exception) {
            continuation.resumeWithException(
                EPassportException("Failed to initiate Google Play Integrity API request", e)
            )
        }
    }
}
