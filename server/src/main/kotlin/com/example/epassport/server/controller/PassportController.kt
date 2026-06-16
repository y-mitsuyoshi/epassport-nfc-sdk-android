package com.example.epassport.server.controller

import com.example.epassport.server.service.E2EEDecryptionService
import com.example.epassport.server.service.KmsDecryptionProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * パスポート E2EE データを受け取り、復号して返すコントローラー。
 *
 * 本番運用では秘密鍵をクライアントから受け取らず、
 * [KmsDecryptionProvider]（HSM/KMS）またはコンストラクタ引数経由で
 * サーバー側で安全に管理すること。
 */
@RestController
@RequestMapping("/api/v1/passport")
class PassportController(
    private val decryptionService: E2EEDecryptionService,
    private val privateKeyProvider: () -> RSAPrivateKey = {
        val keyBase64 = System.getenv("EPASSPORT_PRIVATE_KEY_BASE64")
            ?: throw IllegalStateException(
                "Server private key not configured. " +
                "Set EPASSPORT_PRIVATE_KEY_BASE64 environment variable."
            )
        val keyBytes = java.util.Base64.getDecoder().decode(keyBase64)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        keyFactory.generatePrivate(spec) as RSAPrivateKey
    }
) {

    /**
     * JWE 形式の暗号化データをサーバー側の秘密鍵で復号する。
     *
     * 秘密鍵は [privateKeyProvider]（デフォルトは `EPASSPORT_PRIVATE_KEY_BASE64` 環境変数）で指定する。
     *
     * @param request JWE 文字列
     * @return 復号された平文（UTF-8 文字列）
     */
    @PostMapping("/decrypt")
    fun decrypt(@RequestBody request: DecryptRequest): ResponseEntity<DecryptResponse> {
        val plaintext = decryptionService.decrypt(request.jwe, privateKeyProvider())
        return ResponseEntity.ok(DecryptResponse(String(plaintext, Charsets.UTF_8)))
    }

    data class DecryptRequest(
        val jwe: String
    )

    data class DecryptResponse(
        val plaintext: String
    )
}
