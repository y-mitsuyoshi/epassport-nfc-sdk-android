# HSM/KMS Integration Design for Server-Side Private Key Management

## 1. Objectives & Security Requirements
To prevent the private keys used for ePassport E2EE decryption and signature verification from being compromised, the server-side backend must delegate all raw private key operations to a Hardware Security Module (HSM) or cloud Key Management Service (KMS). 

---

## 2. Target Architecture
The application server acts as an orchestrator. It receives JWE payloads from clients, extracts the encrypted metadata, and requests decryption from the KMS using standard API limits without storing private keys in the server's local storage or JVM heap memory.

```
+---------------+                +--------------------+                +---------------+
|               |  1. Send JWE   |                    | 2. Decrypt RSA |               |
| Client App    | -------------> | Application Server | -------------> | Cloud KMS/HSM |
|               |                | (Spring Boot)      | <------------- | (AWS/GCP/etc.)|
|               |  5. Success    |                    | 3. Plain AES   |               |
|               | <------------- |                    |    (in memory) |               |
+---------------+                +--------------------+                +---------------+
                                           |
                                   4. Decrypt Payload
                                   (Zero AES key after)
```

---

## 3. Cryptographic Key Lifecycles & Rotation

To maintain secure long-term operations, keys must be rotated regularly without causing service interruptions.

### 3.1 Grace Period for Key Rotation
When a new encryption key is generated, clients must retrieve the new public key. However, passports encrypted with the older public key might still be in transit or cached by backend request queues. 

```
                                ROTATION TIMELINE
                                
   Key A (Active)   ============================> (Retired)
                                                 \
   Key B (Inactive)                 ==============> (Active) ===================>
                                    |            |
                                    |<-- Grace ->|
                                        Period
```

- **Grace Period (e.g., 30 Days)**: During the grace period, both the retired Key A and the new active Key B are kept active in the HSM/KMS.
- **Key Version Identifiers**: The client prefixes JWE payloads or injects a key ID (`kid` header) indicating which key version was used. The server uses this metadata to request decryption of the matching key version from the KMS.

---

## 4. Cloud Integration Details

### 4.1 AWS KMS Integration
AWS KMS supports RSA-OAEP asymmetric decryption. The server calls the AWS SDK:

```kotlin
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
```

### 4.2 GCP Cloud KMS Integration
For GCP KMS, asymmetric decryption is executed via the GCP KeyManagementServiceClient:

```kotlin
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
```

---

## 5. Local Development Mock Implementation
During local development and unit testing, we bypass network overhead using `MockKmsDecryptionProvider`, which uses a local Java `KeyPair` to mimic HSM behavior.

```kotlin
val localKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair()
val mockKmsProvider = MockKmsDecryptionProvider(localKeyPair.private)

// Inject mock provider into E2EEDecryptionService
val decryptionService = E2EEDecryptionService()
val plaintext = decryptionService.decrypt(jweCompactString, mockKmsProvider)
```
This ensures high testability in CI/CD loops without external cloud dependency blockades.
