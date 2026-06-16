package com.example.epassport.server.service

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse

class AwsKmsDecryptionProviderTest {

    @Test
    fun decryptRsaOaep_withMockedKms_returnsPlaintext() {
        val plaintext = "decrypted-aes-key".toByteArray()
        val kmsClient = mock<KmsClient>()
        val response = DecryptResponse.builder()
            .plaintext(SdkBytes.fromByteArray(plaintext))
            .build()
        whenever(kmsClient.decrypt(any<DecryptRequest>())).thenReturn(response)

        val provider = AwsKmsDecryptionProvider(kmsClient, "arn:aws:kms:us-east-1:123456789:key/test-key")
        val result = provider.decryptRsaOaep("encrypted-key".toByteArray())

        assertArrayEquals(plaintext, result)
    }
}
