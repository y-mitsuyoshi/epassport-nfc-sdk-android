# Third-Party Security Audit Plan

## 1. Objectives & Engagement Strategy
Prior to the commercial release of the `epassport-nfc-sdk-android` suite, a formal third-party security audit must be conducted by an independent security firm. The objective is to validate that cryptographic implementations are free from vulnerabilities, PII memory handling is robust, and the server-side zero-trust verification cannot be bypassed.

---

## 2. Audit Scope

The audit spans the complete integration surface of the client-side SDK and the verification server.

```
       +-------------------------------------------------------------+
       |                         AUDIT SCOPE                         |
       +-------------------------------------------------------------+
                            /                       \
                           /                         \
  +--------------------------------+       +---------------------------------+
  |          Android Client        |       |        Spring Boot Server       |
  +--------------------------------+       +---------------------------------+
  | - BAC/PACE/AA APDU Exchanges   |       | - SodParser (Signature / Hash)  |
  | - RASP (Root & Debug Checks)   |       | - E2EEDecryption (KMS Boundary) |
  | - Memory Zeroing (CharArray)   |       | - Verification API Endpoints    |
  | - Local Storage Encryption     |       | - Key Rotation Grace Period     |
  +--------------------------------+       +---------------------------------+
```

---

## 3. Methodology & Threat Modeling
The audit must employ a combination of static, dynamic, and hardware-simulation testing.

### 3.1 Static Application Security Testing (SAST)
- **Manual Code Review**: Focused on cryptographic implementations in `SecureMessaging.kt`, `CryptoUtils.kt`, `E2EECipher.kt`, and `SodParser.kt`.
- **Side-Channel Analysis**: Verification that critical comparison operations (such as hash matching and signature comparisons) use constant-time algorithms (e.g., `MessageDigest.isEqual`) to prevent timing attacks.

### 3.2 Dynamic Application Security Testing (DAST)
- **Runtime Intrusion (RASP Validation)**: Penetration testers will use runtime manipulation frameworks (like Frida, Xposed) to attempt to bypass:
  - Root beer root check signatures.
  - Emulator guards.
  - The local `PassportVerifier` signature validation (testing if mock responses can trigger successful states in `ReadPassportUseCase`).
- **NFC Fuzzing**: Injecting malformed APDU responses (e.g., buffer overflows, illegal status words) during the read loop to check for crashes (DoS) or unexpected state transitions.
- **Memory Dump Inspection**: Capturing memory dumps (using `hprof` analysis tools) of the Android process during and immediately after the reading phase to check for plaintext PII residues (MRZ, Face photos).

---

## 4. Auditor Selection Benchmarks
We will evaluate and select external audit firms based on the following criteria:
- **NFC & Smart Card Expertise**: Proven track record of auditing solutions built on ISO 7816, ISO 14443, and ICAO Doc 9303.
- **Android Hardening Knowledge**: Deep familiarity with Android's platform security features (TEE, StrongBox, Keystore, R8 optimization).
- **Reputable Firms**: Engagement list includes industry-recognized groups such as **Cure53**, **Trail of Bits**, **NCC Group**, or **Synopsys**.

---

## 5. Vulnerability Triage & Remediation Workflow

Any discovered vulnerabilities will be tracked and remediated using the following severity-based SLA:

```
[Vulnerability Discovered] 
         |
         +---> CRITICAL / HIGH  (SLA: 7 Days)  --> Hotfix & Re-Audit
         |
         +---> MEDIUM           (SLA: 21 Days) --> Fix in Next Sprint
         |
         +---> LOW              (SLA: 60 Days) --> Backlog / Refactor
```

### 5.1 Re-Audit Process
1. Upon completing patches, the code changes are isolated into a dedicated release branch.
2. The audit firm is re-engaged to run delta-verification tests on the updated code.
3. The final commercial release is blocked until all Critical and High vulnerabilities are signed off as "Resolved".
