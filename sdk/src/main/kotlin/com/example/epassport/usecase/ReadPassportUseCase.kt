package com.example.epassport.usecase

import com.example.epassport.domain.exception.EPassportException
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
        onProgress: (ReadProgress) -> Unit = {}
    ): PassportData {
        try {
            onProgress(ReadProgress.CONNECTING)
            
            // 1. SELECT Applet
            transceiver.selectApp()

            // 2. Authenticate (BAC)
            onProgress(ReadProgress.AUTHENTICATING)
            val bacKey = mrzData.deriveBacKeys()
            val secureTransceiver = try {
                authenticator.authenticate(transceiver, bacKey)
            } finally {
                // セキュリティ要件: 認証の成否にかかわらず、BAC 鍵は
                // 使用直後にヒープメモリからゼロクリアする。
                bacKey.clear()
            }

            // 3. Read DG1
            onProgress(ReadProgress.READING_DG1)
            val dg1 = reader.readDg1(secureTransceiver)

            // 4. Read DG2
            onProgress(ReadProgress.READING_DG2)
            val dg2 = reader.readDg2(secureTransceiver)

            // 5. Try Active Authentication (AA) - failure here should not block success of reading
            var aaData: com.example.epassport.domain.model.ActiveAuthenticationData? = null
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
            } catch (e: Exception) {
                // Active Authentication is optional or could fail on some passports (e.g. no DG15 support).
                // We capture and ignore the exception so that normal passport data is still returned.
                android.util.Log.w("ReadPassportUseCase", "Active Authentication failed or not supported: ${e.message}")
            }

            onProgress(ReadProgress.SUCCESS)
            return PassportData(dg1 = dg1, dg2 = dg2, activeAuthenticationData = aaData)

        } catch (e: Exception) {
            onProgress(ReadProgress.ERROR)
            if (e is EPassportException) {
                throw e
            }
            throw EPassportException("Unexpected error during passport reading", e)
        }
    }
}

