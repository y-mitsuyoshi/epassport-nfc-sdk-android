package com.example.epassport.usecase

import com.example.epassport.data.auth.CscaTrustStore
import com.example.epassport.data.auth.PassportVerifier
import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.exception.NfcTagLostException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.model.PassportData
import com.example.epassport.domain.port.DataGroupReader
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator

/**
 * パスポート読み取りの進行状況を通知するコールバックインターフェース。
 */
enum class ReadProgress {
    CONNECTING,
    AUTHENTICATING,
    READING_DG1,
    READING_DG2,
    PERFORMING_ACTIVE_AUTH,
    SUCCESS,
    ERROR
}

/**
 * NFC タグと MRZ 情報を用いてパスポートから DG1, DG2 を読み取るオーケストレーション。
 */
class ReadPassportUseCase(
    private val authenticator: PassportAuthenticator,
    private val reader: DataGroupReader
) {
    /**
     * パスポートから DG1, DG2, および（サポートされている場合は）Active Authentication データを読み取る。
     * 呼び出し元（EPassportReader）が IO スレッド上での実行を保証するため、
     * このメソッド自体は dispatcher の切り替えを行わない。
     */
    suspend fun execute(
        transceiver: NfcTransceiver,
        mrzData: MrzData,
        challenge: ByteArray? = null,
        trustStore: CscaTrustStore? = null,
        cachedData: CachedPassportData? = null,
        onCacheUpdate: (CachedPassportData) -> Unit = {},
        onProgress: (ReadProgress) -> Unit = {}
    ): PassportData {
        try {
            onProgress(ReadProgress.CONNECTING)

            // 1. SELECT Applet
            transceiver.selectApp()

            // 2. Authenticate (PACE / BAC)
            onProgress(ReadProgress.AUTHENTICATING)
            val secureTransceiver = try {
                authenticator.authenticate(transceiver, mrzData)
            } finally {
                // セキュリティ要件: 認証の成否にかかわらず、MRZ 情報は
                // 使用直後にヒープメモリからゼロクリアする。
                mrzData.clear()
            }

            // secureTransceiver のスコープ: 認証後にセッション鍵を確実にゼロクリアするため
            // Closeable (SecureMessaging) であれば finally で close() を呼ぶ。
            try {
                // キャッシュの期限切れチェック: expired の場合は新規として扱う
                var currentCache = if (cachedData != null && !cachedData.isExpired()) {
                    cachedData
                } else {
                    CachedPassportData()
                }

                // 3. Read DG1
                val dg1 = if (currentCache.dg1 != null) {
                    currentCache.dg1!!
                } else {
                    onProgress(ReadProgress.READING_DG1)
                    val readDg1 = reader.readDg1(secureTransceiver)
                    currentCache = currentCache.copy(dg1 = readDg1, timestamp = System.currentTimeMillis())
                    onCacheUpdate(currentCache)
                    readDg1
                }

                // 4. Read DG2
                val dg2 = if (currentCache.dg2 != null) {
                    currentCache.dg2!!
                } else {
                    onProgress(ReadProgress.READING_DG2)
                    val readDg2 = reader.readDg2(secureTransceiver)
                    currentCache = currentCache.copy(dg2 = readDg2, timestamp = System.currentTimeMillis())
                    onCacheUpdate(currentCache)
                    readDg2
                }

                // 5. Read SOD for Passive Authentication
                var sodBytes = currentCache.sodBytes
                if (trustStore != null && sodBytes == null) {
                    try {
                        sodBytes = reader.readSod(secureTransceiver)
                        currentCache = currentCache.copy(sodBytes = sodBytes, timestamp = System.currentTimeMillis())
                        onCacheUpdate(currentCache)
                    } catch (e: EPassportException) {
                        android.util.Log.w("ReadPassportUseCase", "Failed to read SOD: ${e.message}")
                    }
                }

                // 6. Try Active Authentication (AA) - failure here should not block success of reading
                var aaData = currentCache.aaData
                if (aaData == null) {
                    try {
                        onProgress(ReadProgress.PERFORMING_ACTIVE_AUTH)
                        val finalChallenge = challenge ?: ByteArray(8).apply {
                            java.security.SecureRandom().nextBytes(this)
                        }

                        // Read DG15 (Public Key Info)
                        val dg15Bytes = reader.readDg15(secureTransceiver)

                        // Perform INTERNAL AUTHENTICATE
                        val signature = reader.performActiveAuthentication(secureTransceiver, finalChallenge)

                        aaData = com.example.epassport.domain.model.ActiveAuthenticationData(
                            publicKeyInfo = dg15Bytes,
                            challenge = finalChallenge,
                            signature = signature
                        )
                        currentCache = currentCache.copy(aaData = aaData, timestamp = System.currentTimeMillis())
                        onCacheUpdate(currentCache)
                    } catch (e: NfcTagLostException) {
                        // AA 中にタグが失われた場合はセッション自体が無効なので再スローする
                        throw e
                    } catch (e: EPassportException) {
                        // AA はオプショナル。DG15 未実装のパスポートなどで失敗する可能性あり。
                        android.util.Log.i("ReadPassportUseCase", "Active Authentication not supported or failed: ${e.message}")
                    } catch (e: Exception) {
                        // 上記以外のエラー (e.g. チップライブラリのバグ) も非致命的
                        android.util.Log.w("ReadPassportUseCase", "Unexpected error during Active Authentication: ${e.message}")
                    }
                }

                // 7. Perform Passive / Active Authentication verification if trust store is provided
                val verificationResult = if (trustStore != null && sodBytes != null) {
                    val dataGroups = mutableMapOf<Int, ByteArray>(1 to dg1.rawBytes, 2 to dg2.rawBytes)
                    aaData?.publicKeyInfo?.let { dataGroups[15] = it }
                    PassportVerifier().verify(sodBytes, dataGroups, aaData, trustStore)
                } else null

                onProgress(ReadProgress.SUCCESS)
                return PassportData(
                    dg1 = dg1,
                    dg2 = dg2,
                    activeAuthenticationData = aaData,
                    verificationResult = verificationResult
                )
            } finally {
                // セッション鍵のゼロクリア (SecureMessaging が Closeable を実装している場合)
                (secureTransceiver as? java.io.Closeable)?.close()
            }

        } catch (e: Exception) {
            onProgress(ReadProgress.ERROR)
            if (e is EPassportException) {
                throw e
            }
            throw EPassportException("Unexpected error during passport reading", e)
        }
    }
}

