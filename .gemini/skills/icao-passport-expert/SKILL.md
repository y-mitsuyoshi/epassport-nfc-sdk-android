---
name: icao-passport-expert
description: Use this skill when you need to understand, design, or implement ICAO Doc 9303 ePassport specifications (BAC, PACE, Active/Passive Authentication, Data Groups parser).
disable-model-invocation: false
---
# ICAO Doc 9303 ePassport Specification Expert

## Instructions
1. 電子パスポートの読み取り仕様を設計・実装する際は、常に ICAO Doc 9303 Part 11 に準拠してください。
2. データグループ（DG1〜DG16）および SOD (Document Security Object) のパースには、ASN.1 DER デコード規格を正しく適用してください。
3. BAC（Basic Access Control）や PACE（Password Authenticated Connection Establishment）の暗号鍵生成時には、MRZ情報（パスポート番号、生年月日、有効期限）から導出される鍵シード（Kseed）の定義を厳格に検証してください。
