# Security Whitepaper & Integration Guide for eKYC Vendors

## 1. Introduction
The `epassport-nfc-sdk-android` provides a zero-trust architecture for reading and verifying electronic machine-readable travel documents (eMRTD) in compliance with ICAO Doc 9303. This document outlines the security architecture and serves as an integration manual for eKYC developers.

---

## 2. Threat Model & Security Mitigations

```
               +-------------------------------------------------+
               |                   THREAT MODEL                  |
               +-------------------------------------------------+
               /                        |                        \
              /                         |                         \
      +---------------+         +---------------+         +-----------------+
      |  Eavesdropping |         |  Chip Cloning  |         | Memory Tampering|
      +---------------+         +---------------+         +-----------------+
      | Intercepting  |         | Copying raw   |         | Hooking memory  |
      | NFC signals.  |         | chip data.    |         | (e.g., Frida).  |
      +---------------+         +---------------+         +-----------------+
              |                         |                         |
              v                         v                         v
      +---------------+         +---------------+         +-----------------+
      |  MITIGATION   |         |  MITIGATION   |         |   MITIGATION    |
      +---------------+         +---------------+         +-----------------+
      | BAC / PACE +  |         | Active Auth   |         | RASP + E2EE +   |
      | Secure Msg    |         | (AA / DG15)   |         | PII Zero-Clear  |
      +---------------+         +---------------+         +-----------------+
```

### 2.1 Eavesdropping on NFC Channel
- **Threat**: Attackers using high-gain antennas to sniff wireless communications between the smartphone and the passport.
- **Mitigation**: The SDK establishes a secure session using **Basic Access Control (BAC)** or **PACE (Password Authenticated Connection Establishment)**. All subsequent exchanges are wrapped in **Secure Messaging (SM)** utilizing AES-128/256 or Triple DES, ensuring confidentiality and integrity.

### 2.2 Chip Cloning
- **Threat**: Duplicating the file structure of a valid passport onto a blank smart card to bypass verification.
- **Mitigation**: The SDK performs **Active Authentication (AA)**. It challenges the chip with a random 8-byte value (`INTERNAL AUTHENTICATE`). The chip must sign this challenge using its private key (stored in secure hardware on the passport chip). The SDK verifies this signature against the public key extracted from `DG15`, which is cryptographically bound to the passport's digital signature (`EF.SOD`).

### 2.3 Memory Tampering (Man-in-the-Endpoint)
- **Threat**: Malicious applications or hooking frameworks (e.g., Frida, Xposed) extracting passport data from the application's RAM.
- **Mitigation**:
  - **End-to-End Encryption (E2EE)**: The SDK encrypts extracted PII using JSON Web Encryption (JWE) with the server's public key immediately after reading. The plaintext never stays on the heap long-term.
  - **Immediate Zero-Clearing**: Sensitive buffers (like MRZ data, session keys, and face images) are overwritten in memory using `.fill(0)` or `.fill('\u0000')` immediately after use.
  - **RASP Guard**: Built-in runtime checks block execution if root access, emulators, or active debuggers are detected.

---

## 3. Integration Steps

### Step 1: Add Dependency
Include the SDK in your `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.example.epassport:sdk-nfc:0.0.1")
}
```

### Step 2: Initialize & Scan
Trigger the NFC reader by passing the `NfcTag` and the `MrzData` (typically parsed from an OCR scan of the document's photo page).

```kotlin
import com.example.epassport.api.EPassportReader
import com.example.epassport.api.ReadResult
import com.example.epassport.domain.model.MrzData

// Derive MRZ info (e.g., from OCR)
val mrzData = MrzData(
    documentNumber = "L898902C3".toCharArray(),
    dateOfBirth = "740812".toCharArray(),
    dateOfExpiry = "120415".toCharArray()
)

lifecycleScope.launch {
    val result = EPassportReader.read(
        context = this@MainActivity,
        tag = nfcTag,
        mrzData = mrzData,
        challenge = serverChallenge, // Crucial for AA
        trustStore = myCscaTrustStore // For on-device verification if needed
    )

    when (result) {
        is ReadResult.Success -> {
            val passportData = result.data
            // Obtain JWE-encrypted payload for server-side verification
            val encryptedPayload = passportData.toEncryptedPayload(serverPublicKey)
            sendPayloadToBackend(encryptedPayload)
        }
        is ReadResult.Error -> {
            Log.e("eKYC", "Verification Failed", result.exception)
        }
    }
}
```

### Step 3: Mandatory ProGuard/R8 Obfuscation Rules
To prevent reverse-engineering of critical verification logic, add these rules to your `proguard-rules.pro`:

```proguard
# Keep SDK Public APIs
-keep class com.example.epassport.api.** { *; }
-keep class com.example.epassport.domain.model.** { *; }

# Strip all debug logs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Protect Bouncy Castle crypto calls from renaming
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
```
