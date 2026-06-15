package com.example.epassport.server.controller

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@AutoConfigureMockMvc
class PassportControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun decrypt_returnsPlaintext() {
        val plaintext = "sensitive passport data"
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val jwe = createJwe(plaintext.toByteArray(Charsets.UTF_8), keyPair.public)
        val privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)

        val requestBody = """
            {
                "jwe": "$jwe",
                "privateKeyBase64": "$privateKeyBase64"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/passport/decrypt") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.plaintext") { value(plaintext) }
        }
    }

    private fun createJwe(plaintext: ByteArray, publicKey: java.security.PublicKey): String {
        val aesKey = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertextWithTag = cipher.doFinal(plaintext)
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)

        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", "BC")
        keyCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedKey = keyCipher.doFinal(aesKey)

        val header = """{"alg":"RSA-OAEP-256","enc":"A256GCM","typ":"JWT"}""".toByteArray(Charsets.UTF_8)
        val encoder = Base64.getUrlEncoder().withoutPadding()

        return listOf(header, encryptedKey, iv, ciphertext, tag)
            .joinToString(".") { encoder.encodeToString(it) }
    }
}
