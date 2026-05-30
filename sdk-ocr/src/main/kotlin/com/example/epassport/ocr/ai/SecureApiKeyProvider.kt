package com.example.epassport.ocr.ai

/**
 * APIキーを安全に提供するための抽象化インターフェース。
 *
 * SDK内部では直接文字列としてAPIキーを保持せず、このプロバイダを通じて
 * 動的に取得することで、利用者側のセキュリティ戦略に委ねる。
 *
 * ## 推奨される実装方針（セキュリティレベル別）
 *
 * ### レベル1: EncryptedSharedPreferences + Android Keystore
 * デバイス上で暗号化されたSharedPreferencesにAPIキーを保存し、
 * 実行時に復号して返す。APK内に平文のキーを埋め込まない。
 *
 * ### レベル2: サーバーサイド動的配信（最推奨）
 * ログイン時やアプリ起動時に、自社サーバーから暗号化されたAPIキーを取得し、
 * メモリ上のみで保持する。サーバー側でキーのローテーションも可能。
 *
 * ### レベル3: 自社プロキシ経由（Backend For Frontend）
 * クライアントはクラウドAI APIに直接アクセスせず、自社サーバーの
 * プロキシエンドポイントに画像を送信。APIキーはサーバー側のみで管理。
 *
 * ### レベル4: JNI ネイティブレイヤー難読化
 * NDKでAPIキーを保持しJNI経由で取得。リバースエンジニアリングの
 * 難易度を上げるが、完全ではない。
 *
 * ### 非推奨: BuildConfig / local.properties / ハードコード
 * APKの逆コンパイルで簡単に読み取れる。絶対に避けるべき。
 */
fun interface SecureApiKeyProvider {
    /**
     * APIキーを提供する。
     *
     * @return AI OCR APIキー文字列
     */
    suspend fun provide(): String
}
