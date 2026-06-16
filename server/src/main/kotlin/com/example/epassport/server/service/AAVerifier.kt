package com.example.epassport.server.service

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * サーバー側用 Active Authentication 署名検証。
 */
object AAVerifier {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    data class ActiveAuthenticationData(
        val publicKeyInfo: ByteArray,
        val challenge: ByteArray,
        val signature: ByteArray
    )

    fun verify(data: ActiveAuthenticationData): Boolean {
        val publicKey = extractPublicKey(data.publicKeyInfo)
        return verifySignature(publicKey, data.challenge, data.signature)
    }

    private fun extractPublicKey(publicKeyInfoBytes: ByteArray): PublicKey {
        val spki = SubjectPublicKeyInfo.getInstance(publicKeyInfoBytes)
        val algorithm = when (spki.algorithm.algorithm.id) {
            "1.2.840.113549.1.1.1" -> "RSA"
            "1.2.840.10045.2.1" -> "EC"
            else -> throw IllegalArgumentException("Unsupported public key algorithm OID: ${spki.algorithm.algorithm.id}")
        }
        val keyFactory = KeyFactory.getInstance(algorithm, "BC")
        return keyFactory.generatePublic(X509EncodedKeySpec(publicKeyInfoBytes))
    }

    private fun verifySignature(publicKey: PublicKey, challenge: ByteArray, signature: ByteArray): Boolean {
        return try {
            val keyType = publicKey.algorithm.uppercase()
            val primaryAlg = when (keyType) {
                "RSA" -> "SHA256withRSA"
                "EC" -> "SHA256withECDSA"
                else -> throw IllegalArgumentException("Unsupported AA public key algorithm: $keyType")
            }
            
            // Try SHA-256 first
            try {
                val sig = Signature.getInstance(primaryAlg, "BC")
                sig.initVerify(publicKey)
                sig.update(challenge)
                if (sig.verify(signature)) return true
            } catch (e: Exception) {
                // Fallback to SHA-1
            }

            val fallbackAlg = when (keyType) {
                "RSA" -> "SHA1withRSA"
                "EC" -> "SHA1withECDSA"
                else -> null
            }

            if (fallbackAlg != null) {
                val sig = Signature.getInstance(fallbackAlg, "BC")
                sig.initVerify(publicKey)
                sig.update(challenge)
                sig.verify(signature)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
