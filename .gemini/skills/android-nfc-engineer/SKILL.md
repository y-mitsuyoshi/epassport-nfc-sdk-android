---
name: android-nfc-engineer
description: Use this skill when dealing with Android NFC Framework, IsoDep transceiver, APDU command transceiving, or device-specific NFC compatibility issues.
disable-model-invocation: false
---
# Android NFC Core Engineer

## Instructions
1. NFC通信時のタイムアウト値を適切に設定してください（特に暗号署名を生成する `INTERNAL AUTHENTICATE` ではタイムアウトを長めに確保する）。
2. 通信の途中でカードが離された場合（IOException / TagLostException）の例外ハンドリングを徹底し、NFC接続を確実にクローズ（`isoDep.close()`）してください。
3. 端末ごとのNFCアンテナ感度の個体差を考慮し、ユーザーに適切なかざし方を促すUXガイドラインを意識した設計にしてください。
