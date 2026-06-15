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
            val algorithm = when (publicKey.algorithm.uppercase()) {
                "RSA" -> "SHA1withRSA"
                "EC" -> "SHA1withECDSA"
                else -> throw IllegalArgumentException("Unsupported AA public key algorithm: ${publicKey.algorithm}")
            }
            val sig = Signature.getInstance(algorithm, "BC")
            sig.initVerify(publicKey)
            sig.update(challenge)
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }
}
