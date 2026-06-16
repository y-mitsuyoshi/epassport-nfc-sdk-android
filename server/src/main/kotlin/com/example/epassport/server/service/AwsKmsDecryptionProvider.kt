package com.example.epassport.server.service

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec

class AwsKmsDecryptionProvider(
    private val kmsClient: KmsClient,
    private val keyArn: String
) : KmsDecryptionProvider {

    override fun decryptRsaOaep(ciphertext: ByteArray): ByteArray {
        val request = DecryptRequest.builder()
            .keyId(keyArn)
            .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
            .encryptionAlgorithm(EncryptionAlgorithmSpec.RSAES_OAEP_SHA_256)
            .build()
        val response = kmsClient.decrypt(request)
        return response.plaintext().asByteArray()
    }
}
