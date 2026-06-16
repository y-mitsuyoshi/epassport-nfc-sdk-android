package com.example.epassport.server.service

import java.security.PrivateKey
import javax.crypto.Cipher

/**
 * HSM または KMS による復号操作を抽象化するインターフェース。
 * 秘密鍵をアプリケーションメモリ空間（JVMヒープ）に平文で露出させることなく、
 * 安全なハードウェア境界で復号処理を行うために用いる。
 */
interface KmsDecryptionProvider {
    /**
     * RSA-OAEP (SHA-256) を用いて暗号化されたデータを復号する。
     *
     * @param ciphertext 暗号化されたバイト列（JWE の encryptedKey）
     * @return 復号されたバイト列（対称鍵など）
     */
    fun decryptRsaOaep(ciphertext: ByteArray): ByteArray
}

/**
 * ローカル開発およびユニットテスト用のモック KMS 復号プロバイダー。
 * メモリ上に保持する RSA 秘密鍵を使ってソフトウェア的に復号を行う。
 */
class MockKmsDecryptionProvider(private val privateKey: PrivateKey) : KmsDecryptionProvider {
    override fun decryptRsaOaep(ciphertext: ByteArray): ByteArray {
        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", "BC")
        keyCipher.init(Cipher.DECRYPT_MODE, privateKey)
        return keyCipher.doFinal(ciphertext)
    }
}
