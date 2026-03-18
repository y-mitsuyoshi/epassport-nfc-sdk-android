# epassport-nfc-sdk-android

An Android SDK in Kotlin for reading IC chip passports (ePassports) via NFC.
Compliant with ICAO Doc 9303, supporting BAC and PACE authentication protocols.

---

## Requirements

| Tool | Version |
|------|---------|
| Android SDK | compileSdk 35 / minSdk 24 |
| Kotlin | 2.1.0 |
| AGP | 8.7.3 |
| Java | 17 |

---

## Project Structure

```
sdk/
└── src/
    └── main/
        ├── AndroidManifest.xml          # NFC permissions & features
        └── kotlin/com/ymitsuyoshi/epassportnfc/
            ├── core/                    # Shared error types and utilities
            │   ├── EPassportException.kt    # Sealed exception hierarchy
            │   └── utils/
            │       └── HexUtils.kt          # Hex encoding/decoding helpers
            │
            ├── domain/                  # Pure business logic (no Android deps)
            │   ├── model/
            │   │   ├── PassportData.kt      # Aggregated passport read result
            │   │   ├── PersonalData.kt      # MRZ personal data (DG1)
            │   │   └── DataGroup.kt         # ICAO data group enumeration
            │   └── repository/
            │       └── IPassportRepository.kt  # Repository contract + BacKey
            │
            ├── data/                    # NFC communication & cryptography
            │   ├── nfc/
            │   │   ├── NfcApduCommand.kt    # ISO 7816-4 command builder
            │   │   └── NfcApduResponse.kt   # ISO 7816-4 response parser
            │   ├── auth/
            │   │   ├── BacAuthenticator.kt  # BAC handshake (3DES / SHA-1)
            │   │   └── PaceAuthenticator.kt # PACE stub (AES / ECDH)
            │   └── repository/
            │       └── PassportRepository.kt  # IPassportRepository impl
            │
            └── presentation/            # UI integration layer
                └── PassportReaderCallback.kt  # Result callback interface
```

---

## Architecture

The SDK follows a clean layered architecture:

```
Presentation  ──►  Domain  ◄──  Data
(Callbacks)       (Models        (NFC APDU,
                   Repos)         BAC/PACE,
                                  Repository impl)
                   Core
               (Exceptions, Utils)
```

- **`core`** — shared infrastructure: `EPassportException` sealed class and `HexUtils`.
- **`domain`** — pure Kotlin models and the `IPassportRepository` interface. No Android dependencies.
- **`data`** — implements the domain interface using NFC APDU commands, BAC session key derivation (ICAO 9303 §B.2), and a PACE stub.
- **`presentation`** — `PassportReaderCallback` for Activity/Fragment integration.

---

## Usage

```kotlin
// 1. Wrap IsoDep.transceive in the SDK lambda
val repository = PassportRepository { apduBytes ->
    isoDep.transceive(apduBytes)   // runs on coroutine dispatcher of your choice
}

// 2. Supply the MRZ key fields scanned from the MRZ (e.g. via OCR)
val bacKey = BacKey(
    documentNumber = "AB1234567",
    dateOfBirth    = "900101",
    dateOfExpiry   = "300101",
)

// 3. Read the passport
val passportData: PassportData = repository.readPassport(bacKey)
```

---

## Key Dependencies

| Dependency | Purpose |
|------------|---------|
| `kotlinx-coroutines` | Async NFC communication |
| `bcprov-jdk18on` (Bouncy Castle) | 3DES / AES cryptography for BAC & PACE |
| `junit` | Unit testing |
| `mockk` | Mocking in unit & instrumented tests |

---

## NFC Permissions

`AndroidManifest.xml` declares:

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

---

## Supported Standards

- ICAO Doc 9303 — Machine Readable Travel Documents
- ISO/IEC 14443-4 (ISO-DEP) — contactless IC card communication
- ISO/IEC 7816-4 — APDU command/response structure
- BSI TR-03110 — PACE protocol specification

