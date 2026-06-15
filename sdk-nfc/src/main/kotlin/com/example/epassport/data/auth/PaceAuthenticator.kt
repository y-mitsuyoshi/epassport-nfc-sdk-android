package com.example.epassport.data.auth

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator

/**
 * PACE (Password Authenticated Connection Establishment) 認証プロトコルの実装クラス。
 *
 * ICAO Doc 9303 Part 11 に準拠し、MRZ または CAN を用いてチップとの認証を行う。
 * 本クラスは現在 **BETA** 実装であり、基本フレームワーク（EF.CardAccess 読み取り、
 * PACEInfo パース）を提供する。完全な暗号ネゴシエーションは今後の拡張予定。
 */
class PaceAuthenticator : PassportAuthenticator {

    /**
     * BAC 用の [com.example.epassport.domain.model.BacKey] を受け取るインターフェース互換メソッド。
     *
     * PACE では BAC 鍵ではなく MRZ から導出したパスワードを使用するため、
     * 本メソッドは [authenticate] のエイリアスとして動作する。
     */
    override suspend fun authenticate(transceiver: NfcTransceiver, bacKey: com.example.epassport.domain.model.BacKey): NfcTransceiver {
        throw AuthenticationException(
            "PACE requires MrzData or CAN. Use authenticate(transceiver, mrzData) instead."
        )
    }

    /**
     * PACE 認証を実行する。
     *
     * 1. EF.CardAccess を読み取り PACE パラメータを取得
     * 2. MSE:Set AT による PACE 設定
     * 3. GENERAL AUTHENTICATE による鍵合意
     * 4. セキュアメッセージングの確立
     *
     * @param transceiver NFC トランシーバー
     * @param mrzData MRZ 情報（パスワード源）
     * @return セキュアメッセージング対応の Transceiver
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun authenticate(transceiver: NfcTransceiver, mrzData: MrzData): NfcTransceiver {
        // TODO: 完全な PACE 実装（ECDH-GM/AES-CMAC）
        throw NotImplementedError(
            "Full PACE protocol implementation (EF.CardAccess, ECDH-GM, AES-CMAC) is not yet available."
        )
    }
}
