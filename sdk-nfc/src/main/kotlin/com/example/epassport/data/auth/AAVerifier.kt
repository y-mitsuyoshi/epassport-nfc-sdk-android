package com.example.epassport.data.auth

import com.example.epassport.domain.exception.InvalidDataException
import com.example.epassport.domain.model.ActiveAuthenticationData
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Active Authentication (AA) の署名をローカル端末で検証する。
 *
 * DG15 に格納された ICAO 公開鍵情報 (SubjectPublicKeyInfo) から [PublicKey] を復元し、
 * チップが返却した署名が指定されたチャレンジに対する有効な署名であるかを検証する。
 */
object AAVerifier {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Active Authentication 署名を検証する。
     *
     * @param data [ReadPassportUseCase] 等で収集した ActiveAuthenticationData
     * @return true: 署名が有効（クローンではない可能性が高い）
     *         false: 署名が無効、または公開鍵の復元に失敗
     * @throws InvalidDataException DG15 データから公開鍵を復元できない場合
     */
    fun verify(data: ActiveAuthenticationData): Boolean {
        val publicKey = extractPublicKey(data.publicKeyInfo)
        return verifySignature(publicKey, data.challenge, data.signature)
    }

    /**
     * DG15 (SubjectPublicKeyInfo) から [PublicKey] を復元する。
     */
    fun extractPublicKey(publicKeyInfoBytes: ByteArray): PublicKey {
        return try {
            val spki = SubjectPublicKeyInfo.getInstance(publicKeyInfoBytes)
            val algorithm = when (spki.algorithm.algorithm.id) {
                "1.2.840.113549.1.1.1" -> "RSA"   // rsaEncryption
                "1.2.840.10045.2.1" -> "EC"       // id-ecPublicKey
                else -> throw InvalidDataException("Unsupported public key algorithm OID: ${spki.algorithm.algorithm.id}")
            }
            val keyFactory = KeyFactory.getInstance(algorithm, "BC")
            keyFactory.generatePublic(X509EncodedKeySpec(publicKeyInfoBytes))
        } catch (e: Exception) {
            throw InvalidDataException("Failed to extract public key from DG15", e)
        }
    }

    /**
     * 署名を検証する。
     *
     * ICAO 9303 Part 11 では AA 署名に SHA-1 ベースのアルゴリズムが一般的に使用される。
     * 鍵タイプに応じて "SHA1withRSA" または "SHA1withECDSA" を選択する。
     */
    private fun verifySignature(publicKey: PublicKey, challenge: ByteArray, signature: ByteArray): Boolean {
        return try {
            val algorithm = when (publicKey.algorithm.uppercase()) {
                "RSA" -> "SHA1withRSA"
                "EC" -> "SHA1withECDSA"
                else -> throw InvalidDataException("Unsupported AA public key algorithm: ${publicKey.algorithm}")
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
