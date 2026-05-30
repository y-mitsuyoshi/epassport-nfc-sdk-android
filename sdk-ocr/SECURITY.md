# AI OCR Security Guide

## Overview

`sdk-ocr` now uses **cloud AI OCR** (OpenAI GPT-4o / Vertex AI / Bedrock / Google AI Studio) for MRZ recognition.  
This means **passport images are sent to external APIs**. Security and privacy must be handled carefully.

---

## API Key Management

**Never hardcode API keys in source code or `BuildConfig`.**  
APK reverse-engineering can extract them easily.

### Recommended Approaches (by security level)

#### Level 1: Server-Side Dynamic Delivery (Default / Recommended)
Fetch the API key from your backend during login or app launch.  
Keep it only in memory (no local storage). Rotate keys on the server side.

```kotlin
val provider = SecureApiKeyProvider {
    backendService.getAiOcrApiKey() // Fetch from your server
}

val config = AiOcrConfig(
    vendor = "openai",
    apiKeyProvider = provider,
    model = "gpt-4o-mini"
)
```

#### Level 2: EncryptedSharedPreferences + Android Keystore
Store the API key encrypted on the device. Retrieve and decrypt at runtime.

```kotlin
val provider = SecureApiKeyProvider {
    encryptedPrefs.getString("ai_ocr_api_key", "")?.let { decrypt(it) } ?: ""
}

val config = AiOcrConfig(
    vendor = "openai",
    apiKeyProvider = provider,
    model = "gpt-4o-mini"
)
```

#### Level 3: Backend For Frontend (BFF) Proxy
Do not call cloud AI APIs directly from the device.  
Send images to your own server, and let the server call the AI API with the key.

```kotlin
val config = AiOcrConfig(
    vendor = "openai",
    apiKey = "unused", // Key lives on server only
    endpoint = "https://your-server.com/api/v1/ocr", // Your proxy endpoint
    apiKeyProvider = null
)
```

#### Level 4: JNI Native Obfuscation
Store the key in a native C/C++ library. Increases reverse-engineering difficulty, but not foolproof.

### Local Development Only

For **local development and testing**, you can use `AiOcrConfig.forLocalTesting()` which reads the key from `BuildConfig`. This is **convenient but insecure for production** because the key is embedded in the APK.

```kotlin
val config = AiOcrConfig.forLocalTesting(
    vendor = "openai",
    apiKey = BuildConfig.AI_OCR_API_KEY,
    model = "gpt-4o-mini"
)
```

Add the key to `local.properties` (git-ignored):
```properties
AI_OCR_API_KEY=sk-your-test-key-here
```

### Not Recommended for Production

- `local.properties` → `BuildConfig` generation
- Hardcoded strings in Kotlin/Java files
- SharedPreferences without encryption

---

## Privacy Considerations

### Passport Images Sent to Cloud

- Passport MRZ images contain **PII** (Personal Identifiable Information).
- When using cloud AI APIs, images are transmitted to third-party servers (OpenAI, Google, AWS).
- Review each vendor's **data processing policy** before deploying.

| Vendor | Data Retention Policy |
|--------|----------------------|
| OpenAI | Generally does not train on API inputs (as of 2024). Review latest ToS. |
| Google Vertex / AI Studio | Subject to Google Cloud data processing terms. |
| AWS Bedrock | Subject to AWS data processing terms. |

### Mitigations

1. **Use on-device ML Kit as primary** (if ML Kit accuracy is acceptable for your use case).
2. **Add user consent** before sending images to cloud AI.
3. **Implement image retention policies** — do not store images locally after successful recognition.
4. **Use regional endpoints** to keep data within required jurisdictions (e.g., `gcp-asia-northeast1`).

---

## Fallback Configuration

Use `fallbackConfig` to switch vendors if the primary fails (rate limits, outages):

```kotlin
val primary = AiOcrConfig(
    vendor = "openai",
    apiKeyProvider = openAiKeyProvider,
    model = "gpt-4o-mini"
)
val fallback = AiOcrConfig(
    vendor = "google_ai_studio",
    apiKeyProvider = googleKeyProvider,
    model = "gemini-1.5-flash"
)
val config = primary.copy(fallbackConfig = fallback)
val client = AiOcrClientFactory.create(config)
```

---

## Network Security

- All HTTP requests use **TLS 1.2+** (enforced by OkHttp defaults).
- For additional pinning, configure a custom `OkHttpClient` with certificate pinning (advanced).

---

## Audit Checklist

- [ ] API keys are not committed to version control
- [ ] API keys are not present in `BuildConfig` or `local.properties`
- [ ] API keys are encrypted at rest (if stored on device)
- [ ] API keys are rotated periodically
- [ ] User consent is obtained before cloud AI processing
- [ ] Fallback vendor is configured for production reliability
- [ ] Regional endpoint compliance is verified for data residency requirements
