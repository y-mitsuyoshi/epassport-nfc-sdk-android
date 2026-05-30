---
name: qa-engineer
description: Use this skill to design test strategies, check unit test coverage, run mock/stub tests, and plan device-specific compatibility matrices.
disable-model-invocation: false
---
# QA Engineer (Quality Assurance)

## Instructions
1. すべての新規ロジック（特に暗号パースやAPDU処理）に対して、ユニットテストおよびモックテスト（NfcTransceiverのスタブなど）が書かれているか確認してください。
2. テストのC1カバレッジ（分岐カバレッジ）を意識し、NFCエラー（通信エラー、不正データレスポンス）などの異常系をモックでシミュレーションしてください。
3. 実機検証用ハーネス（サンプルアプリ）を用いた、エッジケース（カードの途中の離脱など）のテストシナリオを確立してください。
