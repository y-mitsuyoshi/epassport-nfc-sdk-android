package com.example.epassport.server.controller

import com.example.epassport.server.service.E2EEDecryptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * パスポート E2EE データを受け取り、復号して返すサンプルコントローラー。
 *
 * 本番運用では秘密鍵を環境変数や HSM/KMS から取得し、平文データをログに出力しないよう
 * 厳重に管理すること。
 */
@RestController
@RequestMapping("/api/v1/passport")
class PassportController(
    private val decryptionService: E2EEDecryptionService
) {

    /**
     * JWE 形式の暗号化データを復号する。
     *
     * @param request JWE 文字列と Base64 エンコードされた PKCS#8 秘密鍵
     * @return 復号された平文（UTF-8 文字列）
     */
    @PostMapping("/decrypt")
    fun decrypt(@RequestBody request: DecryptRequest): ResponseEntity<DecryptResponse> {
        val privateKey = loadPrivateKey(request.privateKeyBase64)
        val plaintext = decryptionService.decrypt(request.jwe, privateKey)
        return ResponseEntity.ok(DecryptResponse(String(plaintext, Charsets.UTF_8)))
    }

    private fun loadPrivateKey(base64Key: String): RSAPrivateKey {
        val keyBytes = java.util.Base64.getDecoder().decode(base64Key)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(spec) as RSAPrivateKey
    }

    data class DecryptRequest(
        val jwe: String,
        val privateKeyBase64: String
    )

    data class DecryptResponse(
        val plaintext: String
    )
}
