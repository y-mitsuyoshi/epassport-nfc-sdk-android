# epassport-nfc-sdk-android
Android 向け Kotlin ライブラリ / SDK — NFC を使って IC 旅券（ePassport）からデータを読み取るための実装です。

本 SDK は ICAO Doc 9303 の仕様に基づいた基本的なパスポート読み取りフロー（BAC による認証、Secure Messaging、DG1/DG2 の読み取りとパース）を提供します。

**主な特徴**
- `EPassportReader` フェサードを通じたシンプルな高水準 API
- BAC（Basic Access Control）を用いた認証と Secure Messaging の実装
- DG1（MRZ）/DG2（顔画像） の読み取りおよび BER-TLV パーサ
- Kotlin + Coroutines ベース、ユニットテストあり
- 3DES/CMAC 等の暗号処理は BouncyCastle 等のプロバイダに依存します

**重要ファイル**
- SDK モジュール設定: [sdk/build.gradle.kts](sdk/build.gradle.kts#L1-L120)
- 高レベル API: [sdk/src/main/kotlin/com/example/epassport/api/EPassportReader.kt](sdk/src/main/kotlin/com/example/epassport/api/EPassportReader.kt#L1-L120)
- 読取ユースケース: [sdk/src/main/kotlin/com/example/epassport/usecase/ReadPassportUseCase.kt](sdk/src/main/kotlin/com/example/epassport/usecase/ReadPassportUseCase.kt#L1-L200)
- BAC 認証: [sdk/src/main/kotlin/com/example/epassport/data/auth/BacAuthenticator.kt](sdk/src/main/kotlin/com/example/epassport/data/auth/BacAuthenticator.kt#L1-L200)
- Secure Messaging: [sdk/src/main/kotlin/com/example/epassport/data/auth/SecureMessaging.kt](sdk/src/main/kotlin/com/example/epassport/data/auth/SecureMessaging.kt#L1-L400)
- TLV / DG パーサ: [sdk/src/main/kotlin/com/example/epassport/data/parser/TlvParser.kt](sdk/src/main/kotlin/com/example/epassport/data/parser/TlvParser.kt#L1-L240)
- MRZ ユーティリティ: [sdk/src/main/kotlin/com/example/epassport/domain/model/MrzData.kt](sdk/src/main/kotlin/com/example/epassport/domain/model/MrzData.kt#L1-L200)
- サンプル（MRZ → K_seed）: [TestSha.kt](TestSha.kt#L1-L200)

**クイックスタート**

1. ビルド

```bash
./gradlew :sdk:assembleRelease
```

AAR は `sdk/build/outputs/aar/` に出力されます。プロジェクトにモジュールとして組み込む場合は `implementation project(":sdk")` を利用してください。

2. テスト

```bash
./gradlew :sdk:test
```

ユニットテストは `sdk/src/test/kotlin` 配下にあります（例: `ReadPassportUseCaseTest`, `BacAuthenticatorTest`, `TlvParserTest`）。

**簡単な使用例（Android, Kotlin）**

以下は `Tag`（Android NFC の Tag オブジェクト）と MRZ 情報を与えてパスポートを読み取る例です。

```kotlin
import androidx.lifecycle.lifecycleScope
import com.example.epassport.api.EPassportReader
import com.example.epassport.domain.model.MrzData

// Activity / Fragment 内で
lifecycleScope.launch {
	val mrz = MrzData(documentNumber = "L898902C<", dateOfBirth = "690806", dateOfExpiry = "940623")

	val result = EPassportReader.read(tag = tag, mrzData = mrz) { progress ->
		// 進捗通知: CONNECTING, AUTHENTICATING, READING_DG1, READING_DG2, ...
	}

	when (result) {
		is com.example.epassport.api.ReadResult.Success -> {
			val passportData = result.data
			// passportData.dg1 / passportData.dg2 を利用
		}
		is com.example.epassport.api.ReadResult.Error -> {
			// エラー処理
		}
	}
}
```

`EPassportReader` は内部で `IsoDep` を `IsoDepTransceiver` にラップし、`BacAuthenticator` → `IcaoDataGroupReader` の組合せで DG1/DG2 を取得します。

**アーキテクチャ概略**

- モジュール構成: 単一の `sdk` モジュール（Android ライブラリ）
- パッケージ構造 (主要部分)
  - `com.example.epassport.api` — 公開 API (`EPassportReader`, `ReadResult`)
  - `com.example.epassport.usecase` — オーケストレーション (`ReadPassportUseCase`, 進行状況列挙)
  - `com.example.epassport.data` — データ層
	- `nfc` — `IsoDepTransceiver`, `ApduCommand`（APDU の組立）
	- `auth` — `BacAuthenticator`, `SecureMessaging`（認証と保護通信）
	- `reader` — `IcaoDataGroupReader`（DG 読取ロジック）
	- `parser` — `TlvParser`, `Dg1Parser`, `Dg2Parser`
  - `com.example.epassport.domain` — ドメインモデル、ポート、例外 (`MrzData`, `PassportData`, `BacKey`, `NfcTransceiver` など)

フロー（概略）:
1. NFC Tag から `IsoDep` を取得
2. `IsoDepTransceiver.selectApp()` で eMRTD アプレットを選択
3. MRZ から BAC 鍵を導出し、`BacAuthenticator.authenticate` を実行
4. `SecureMessaging` による APDU の暗号化/検証を行いつつ DG1/DG2 を読み出す
5. TLV をパースして MRZ 情報や顔画像を抽出

**セキュリティ上の注意**
- MRZ 情報や生成した鍵は機密データです。メモリ上の保管は最小限にし、不要になったら速やかに破棄してください（SDK 内でも配慮しています）。
- ICAO の仕様に基づき 3DES/CMAC を使用する箇所があります。プロダクション用途では使用する暗号プロバイダやアルゴリズムの適合性を確認してください。

**開発・貢献**
- バグ修正や機能追加は Fork → ブランチ → Pull Request をお願いします。
- 変更前に `./gradlew :sdk:test` で既存テストが通ることを確認してください。

**参考 / 参照ファイル**
- サンプル MRZ → K_seed: [TestSha.kt](TestSha.kt#L1-L200)
- API 実装の入り口: [sdk/src/main/kotlin/com/example/epassport/api/EPassportReader.kt](sdk/src/main/kotlin/com/example/epassport/api/EPassportReader.kt#L1-L120)

---



