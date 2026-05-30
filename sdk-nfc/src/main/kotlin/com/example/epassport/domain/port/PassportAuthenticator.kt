package com.example.epassport.domain.port

import com.example.epassport.domain.model.BacKey

/**
 * BAC / PACE 認証プロトコルの抽象化。
 * 認証成功時に SecureMessaging のラッパーを返す。
 */
interface PassportAuthenticator {
    /**
     * Mutual Authentication を実施し、
     * セキュアメッセージング対応の Transceiver を返す。
     */
    suspend fun authenticate(
        transceiver: NfcTransceiver,
        bacKey: BacKey
    ): NfcTransceiver  // SecureMessaging でラップ済み
}
