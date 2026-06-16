# Real-Device Verification Matrix (PACE / CA / PA / AA)

## 1. Objectives & Scope
This matrix is a guide for executing hardware-in-the-loop QA testing. Since NFC and smart-card behaviors vary based on physical antennas, Android OS versions, and sovereign passport chip designs, this document defines standard test sets, parameters, and failure handling procedures.

---

## 2. Test Passport Matrix

Integrators should test using standard JMRTD mock passports or real passport samples representing different issuers:

| Region / Country | Type | Access Control | Chip Auth (CA) | Active Auth (AA) | Test Case Focus |
|---|---|---|---|---|---|
| **Japan** (post-2015) | TD3 | BAC / PACE | No | Yes (RSA) | Check PACE establishment + AA signature. |
| **Germany** (post-2020)| TD3 | PACE | Yes (ECDH) | No | Check CA session keys transition. |
| **United States** | TD3 | BAC | No | Yes | Standard BAC + AA verification. |
| **Netherlands** | TD3 | BAC / PACE | Yes | Yes | Full suite verification. |

---

## 3. Test Device Compatibility List

Tests must be executed across various Android hardware profiles:

| Brand | Model | Android OS | NFC Chipset | Extended Length APDU |
|---|---|---|---|---|
| **Google** | Pixel 7 | Android 13 | Tensor-embedded | Supported |
| **Samsung** | Galaxy S23 | Android 14 | NXP PN557 | Supported |
| **Sony** | Xperia 5 IV | Android 12 | NXP PN81T | Supported |
| **Xiaomi** | Redmi Note 12 | Android 11 | Broadcom | Restricted / Snaky |

---

## 4. Test Scenario Specifications

Execute the following test cases for each test cycle:

```
                  +----------------------------------------------+
                  |              TEST SCENARIOS                  |
                  +----------------------------------------------+
                  /                       |                      \
                 /                        |                       \
         +---------------+        +---------------+        +---------------+
         | 1. Happy Path |        | 2. TAMPERING  |        | 3. TIMEOUTS   |
         +---------------+        +---------------+        +---------------+
         | - BAC/PACE OK |        | - Alter MRZ   |        | - Pull card   |
         | - Read DG1/2  |        | - Fake DG15   |        |   mid-read    |
         | - PA & AA OK  |        | - Tampered SOD|        | - Check clean |
         +---------------+        +---------------+        +---------------+
```

### 4.1 Happy Path Verification
- **Steps**:
  1. Trigger NFC scan with correct MRZ.
  2. Complete BAC/PACE handshake.
  3. Read DG1, DG2, and DG15.
  4. Perform Active Authentication challenge.
  5. Validate JWE payload decryption on the server.
- **Criteria**: All steps succeed. Execution finishes in under 6 seconds.

### 4.2 Tampering & Error Injection
- **Steps (T1 - Invalid MRZ)**: Inject a wrong birthdate in `MrzData` -> verify BAC/PACE fails with `EPassportException`.
- **Steps (T2 - DG15 Tampering)**: Intercept DG15 read bytes and append garbage -> verify server-side AA fails with `AA_PUBLIC_KEY_MISMATCH` or `AA_SIGNATURE_INVALID`.
- **Steps (T3 - SOD Tampering)**: Corrupt the SOD signature bytes -> verify PA validation fails with `SOD_SIGNATURE_INVALID`.

### 4.3 Network & Physical Interruption
- **Steps**: Intentionally pull the passport away from the phone halfway through the read loop.
- **Criteria**: The SDK must clean up session keys immediately, close the `IsoDep` interface, and emit `NfcTagLostException` or a clean timeout error without leaving raw memory dangling.

---

## 5. Test Execution Template

Use this table structure to log real-device tests in issue reports:

| Run ID | Device | Passport Issuer | BAC/PACE | PA | AA | E2EE | Result (Pass/Fail) | Notes |
|---|---|---|---|---|---|---|---|---|
| `#001` | Pixel 7 | Japan (2018) | PASS (PACE) | PASS | PASS | PASS | **PASS** | Completed in 4.2s |
| `#002` | Galaxy S23| Germany (2021) | PASS (PACE) | PASS | N/A | PASS | **PASS** | AA skipped (no DG15) |
| `#003` | Pixel 7 | Modified test | FAIL | N/A | N/A | N/A | **PASS** | Failed at PACE (expected)|
