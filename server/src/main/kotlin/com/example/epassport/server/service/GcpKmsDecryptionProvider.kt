package com.example.epassport.server.service

import com.google.cloud.kms.v1.AsymmetricDecryptRequest
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString

class GcpKmsDecryptionProvider(
    private val client: KeyManagementServiceClient,
    private val keyName: CryptoKeyVersionName
) : KmsDecryptionProvider {

    override fun decryptRsaOaep(ciphertext: ByteArray): ByteArray {
        val request = AsymmetricDecryptRequest.newBuilder()
            .setName(keyName.toString())
            .setCiphertext(ByteString.copyFrom(ciphertext))
            .build()
        val response = client.asymmetricDecrypt(request)
        return response.plaintext.toByteArray()
    }
}
