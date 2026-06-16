# eKYC Compliance Review (Legal & Regulatory)

## 1. Overview
This compliance review analyzes the alignment of the `epassport-nfc-sdk-android` solution with major global and domestic regulatory frameworks. Given that the SDK processes Personally Identifiable Information (PII) and biometric data, maintaining compliance with privacy and Anti-Money Laundering (AML) standards is critical.

---

## 2. Regulatory Mapping & Compliance Analysis

### 2.1 Japan: Act on Prevention of Transfer of Criminal Proceeds (犯収法)
The Japanese FSA (Financial Services Agency) dictates eKYC methods under Article 6 of the Enforcement Regulations of the Act on Prevention of Transfer of Criminal Proceeds.

| Regulatory Method (Clause) | Requirement | SDK Compliance Implementation | Status |
|---|---|---|---|
| **Method "he" (へ号)** | Reading the IC chip of an official identification document (ePassport) and matching it with a face image taken in real-time. | The SDK reads DG1 (MRZ) and DG2 (biometric face photo) securely via NFC. Authenticity is proved via Passive Authentication (PA) using CSCA. | **Compliant** |
| **Method "to" (と号)** | Reading the IC chip of an official identification document + public key authentication (JPKI/Signature verification). | Passive Authentication (PA) and Active Authentication (AA) signatures are validated. The backend verifies the passport using server-side PA/AA. | **Compliant** |

#### Gap & Mitigation Plan for Method "he" / "to":
- **Gap**: Relies on the client app's honesty regarding verification results. If a client is compromise-hacked (e.g., using Frida), a modified app could bypass local PA/AA checks.
- **Mitigation**: Offload verification entirely to the server-side verification API (`/api/v1/verification/passport`). The client only reads and forwards the raw bytes (JWE-encrypted) to the backend.

---

### 2.2 Global: GDPR (General Data Protection Regulation)
GDPR applies to any European citizen's data and establishes strict mandates on privacy.

- **Data Minimization (Art. 5(1)(c))**: The SDK only reads DG1 and DG2. Extra groups like DG11 (additional personal details) are skipped.
- **Security of Processing (Art. 32)**: End-to-End Encryption (E2EE) prevents intermediate exposure of PII on the Android device's heap or in transit.
- **Storage Limitation (Art. 5(1)(e))**: The SDK does not persistently store ePassport data. In-memory buffers are immediately cleared using `CharArray.fill('\u0000')` and `ByteArray.fill(0)` after processing.

---

### 2.3 Japan: Act on the Protection of Personal Information (個人情報保護法)
- **Safe Management Measures (安全管理措置)**: We enforce encryption at rest for temporary storage (`EncryptedSharedPreferences`) and secure data transmission protocols.
- **Purpose Specification & Consent**: Integrating applications must present an explicit consent screen before triggering the NFC scanner.

---

## 3. Compliance Verification & Gaps Review

### 3.1 Gap Analysis
1. **Biometric Liveness**: The SDK extracts the high-resolution face image (DG2), but does not perform presentation attack detection (PAD).
   - *Plan*: The integrator must pair this SDK with a certified 3D liveness detection engine for the selfie match phase.
2. **Local Memory Residue**: Standard JVM garbage collection is non-deterministic, meaning deleted String objects could reside in memory dumps.
   - *Plan*: The SDK mandates `CharArray` and `ByteArray` for all PII. The SDK's API exposes these mutable structures so integrating applications can zero them out immediately after usage.

### 3.2 Action Item Checklist for Integrators
- [ ] Implement explicit user consent prompt.
- [ ] Configure `FLAG_SECURE` in the scanning Activity to prevent Tapjacking and screen capture.
- [ ] Integrate a certified liveness check on the front-facing camera stream.
- [ ] Configure server-side KMS/HSM for E2EE decryption key management.
- [ ] Set up automatic backend log scrubbers to filter out any PII (MRZ, Passport Number).
