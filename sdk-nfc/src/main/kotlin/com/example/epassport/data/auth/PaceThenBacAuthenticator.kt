package com.example.epassport.data.auth

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.BacKey
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator
import android.util.Log

/**
 * PACE (Password Authenticated Connection Establishment) 認証を優先的に試行し、
 * カードまたは端末がサポートしていない、あるいは失敗した場合には
 * BAC (Basic Access Control) 認証へ自動フォールバックするハイブリッド認証プロバイダ。
 */
class PaceThenBacAuthenticator(
    private val paceAuthenticator: PaceAuthenticator = PaceAuthenticator(),
    private val bacAuthenticator: BacAuthenticator = BacAuthenticator()
) : PassportAuthenticator {

    override suspend fun authenticate(transceiver: NfcTransceiver, bacKey: BacKey): NfcTransceiver {
        // 直接 BACKey が指定された場合は、互換性維持のため BAC を使用
        return bacAuthenticator.authenticate(transceiver, bacKey)
    }

    override suspend fun authenticate(transceiver: NfcTransceiver, mrzData: MrzData): NfcTransceiver {
        return try {
            Log.i("PaceThenBacAuth", "Attempting PACE authentication...")
            paceAuthenticator.authenticate(transceiver, mrzData)
        } catch (e: Exception) {
            Log.w("PaceThenBacAuth", "PACE authentication failed or not supported. Error: ${e.message}. Falling back to BAC...")
            
            // アプレットの状態をリセットするため、再度アプレットを選択する
            try {
                transceiver.selectApp()
            } catch (ex: Exception) {
                Log.w("PaceThenBacAuth", "Failed to re-select app after PACE failure: ${ex.message}")
            }

            // BAC による代替接続を試行
            val bacKey = mrzData.deriveBacKeys()
            try {
                bacAuthenticator.authenticate(transceiver, bacKey)
            } finally {
                bacKey.clear()
            }
        }
    }
}
