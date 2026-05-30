package com.example.epassport.ocr.ai

/**
 * クラウドAI OCRクライアントの共通インターフェース。
 *
 * 各ベンダー（OpenAI, Vertex AI, Bedrock, Google AI Studio等）は
 * このインターフェースを実装することで、統一的に扱えるようになる。
 */
interface AiOcrClient {

    /**
     * 画像のバイト列からMRZテキストを認識する。
     *
     * @param imageBytes JPEGまたはPNG形式の画像データ
     * @return 認識結果（成功時は抽出された生テキスト）
     */
    suspend fun recognize(imageBytes: ByteArray): AiOcrResult
}
