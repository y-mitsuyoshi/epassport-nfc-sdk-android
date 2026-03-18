package com.example.epassport.usecase

import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.model.PassportData
import com.example.epassport.domain.port.DataGroupReader
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * パスポート読み取りの進行状況を通知するコールバックインターフェース。
 */
enum class ReadProgress {
    CONNECTING,
    AUTHENTICATING,
    READING_DG1,
    READING_DG2,
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
    suspend fun execute(
        transceiver: NfcTransceiver,
        mrzData: MrzData,
        onProgress: (ReadProgress) -> Unit = {}
    ): PassportData = withContext(Dispatchers.IO) {
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
                bacKey.clear() // 鍵は認証後すぐにメモリから破棄する
            }

            // 3. Read DG1
            onProgress(ReadProgress.READING_DG1)
            val dg1 = reader.readDg1(secureTransceiver)

            // 4. Read DG2
            onProgress(ReadProgress.READING_DG2)
            val dg2 = reader.readDg2(secureTransceiver)

            onProgress(ReadProgress.SUCCESS)
            return@withContext PassportData(dg1, dg2)

        } catch (e: Exception) {
            onProgress(ReadProgress.ERROR)
            if (e is EPassportException) {
                throw e
            }
            throw EPassportException("Unexpected error during passport reading", e)
        }
    }
}
