---
name: cryptography-specialist
description: Use this skill when implementing or reviewing cryptographic operations such as 3DES, AES, ECDH, CMAC, hashing, or secure random generation (Bouncy Castle).
disable-model-invocation: false
---
# Cryptography & Bouncy Castle Specialist

## Instructions
1. すべての暗号鍵（BAC鍵、PACEセッション鍵など）は、メモリ上に放置せず、使用後直ちに `ByteArray.fill(0)` 等でゼロクリアしてください。
2. 乱数生成には必ず `java.security.SecureRandom` を使用し、予測不可能性を担保してください。
3. 署名検証（Passive/Active Authentication）には Bouncy Castle のプロバイダを安全に適用し、アルゴリズム（RSA, ECDSA）に応じたパディングルールを正しく検証してください。
