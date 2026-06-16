package com.example.epassport.server.service

import com.google.cloud.kms.v1.AsymmetricDecryptResponse
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GcpKmsDecryptionProviderTest {

    @Test
    fun decryptRsaOaep_withMockedGcpKms_returnsPlaintext() {
        val plaintext = "decrypted-aes-key".toByteArray()
        val kmsClient = mock<KeyManagementServiceClient>()
        val response = AsymmetricDecryptResponse.newBuilder()
            .setPlaintext(ByteString.copyFrom(plaintext))
            .build()
        whenever(kmsClient.asymmetricDecrypt(any())).thenReturn(response)

        val keyName = CryptoKeyVersionName.of("project", "location", "key-ring", "crypto-key", "1")
        val provider = GcpKmsDecryptionProvider(kmsClient, keyName)
        val result = provider.decryptRsaOaep("encrypted-key".toByteArray())

        assertArrayEquals(plaintext, result)
    }
}
