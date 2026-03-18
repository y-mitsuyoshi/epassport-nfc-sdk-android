package com.example.epassport.domain.port

/**
 * NFC APDU 通信の抽象化。
 * テスト時は Mock 実装を注入する。
 */
interface NfcTransceiver {
    /** eMRTD アプレットを SELECT */
    suspend fun selectApp()

    /** 任意の APDU コマンドを送信し、レスポンス (data + SW) を返す */
    suspend fun transceive(command: ByteArray): ByteArray

    /** 接続が生きているか */
    val isConnected: Boolean

    /** タイムアウト (ms) の設定 */
    var timeout: Int
}
