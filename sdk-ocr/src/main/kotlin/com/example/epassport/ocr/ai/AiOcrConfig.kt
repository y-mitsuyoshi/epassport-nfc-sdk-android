package com.example.epassport.ocr.ai

/**
 * AI OCRクライアントの設定。
 *
 * 呼び出し側（アプリ層）で APIキー・ベンダー・モデル・エンドポイント を指定し、
 * [AiOcrClientFactory] に渡すことで、対応するクライアントインスタンスを生成する。
 *
 * ## セキュリティ注意
 * `apiKey` 文字列を直接指定するのは **非推奨**。
 * 可能な限り `apiKeyProvider` を使用し、暗号化ストレージや動的配信からキーを取得すること。
 *
 * @param vendor ベンダー識別子。"openai", "vertex", "bedrock", "google_ai_studio" など。
 * @param apiKey 各ベンダーのAPIキー（平文字列。非推奨だが後方互換のため残存）。
 * @param apiKeyProvider APIキーを安全に提供する [SecureApiKeyProvider]。推奨。
 * @param model 使用するモデル名（オプション）。未指定の場合は各ベンダーのデフォルト。
 * @param endpoint カスタムエンドポイントURL（オプション）。プロキシやプライベートエンドポイント向け。
 * @param timeoutMs リクエストタイムアウト（ミリ秒）。デフォルトは30秒。
 * @param fallbackConfig プライマリ失敗時にフォールバックする設定（オプション）。
 */
data class AiOcrConfig(
    val vendor: String,
    val apiKey: String = "",
    val apiKeyProvider: SecureApiKeyProvider? = null,
    val model: String? = null,
    val endpoint: String? = null,
    val timeoutMs: Long = 30_000L,
    val fallbackConfig: AiOcrConfig? = null
) {
    init {
        require(vendor.isNotBlank()) { "vendor must not be blank" }
        require(apiKey.isNotBlank() || apiKeyProvider != null) {
            "Either apiKey or apiKeyProvider must be provided"
        }
    }

    /**
     * 実行時にAPIキーを取得する。
     * Providerが設定されていればProvider優先、なければ平文apiKeyを返す。
     */
    suspend fun resolveApiKey(): String {
        return apiKeyProvider?.provide() ?: apiKey
    }

    companion object {
        /**
         * ローカル開発・テスト用: BuildConfig などのビルド時定数から
         * 簡易的に設定を構築するファクトリメソッド。
         *
         * **本番運用では [SecureApiKeyProvider] を使用すること。**
         * このメソッドは開発時の手軽さを優先したものであり、
         * ビルド時にキーが APK に埋め込まれるためセキュリティ上の注意が必要。
         *
         * @param vendor ベンダー識別子
         * @param apiKey ビルド時定数として埋め込むAPIキー
         * @param model モデル名（省略可）
         * @param endpoint カスタムエンドポイント（省略可）
         */
        fun forLocalTesting(
            vendor: String,
            apiKey: String,
            model: String? = null,
            endpoint: String? = null
        ): AiOcrConfig = AiOcrConfig(
            vendor = vendor,
            apiKey = apiKey,
            model = model,
            endpoint = endpoint
        )
    }
}
