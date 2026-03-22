# ePassport NFC SDK for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

Android 端末の NFC 機能を利用して、IC 旅券（ePassport）のデータを読み取るための Kotlin 製 SDK です。
ICAO Doc 9303 に基づく BAC（Basic Access Control）認証と Secure Messaging をサポートしています。

## 🚀 主な機能

- **シンプルな Facade API**: `EPassportReader` を通じた直感的な操作
- **認証と通信保護**: BAC 認証および Secure Messaging（3DES/CMAC）の実装
- **データ抽出**:
  - **DG1**: MRZ（Machine Readable Zone）情報のパース（姓名、生年月日、有効期限など）
  - **DG2**: 顔写真（JPEG/JPEG2000）のバイナリ抽出
- **進行状況通知**: 認証から読み取り完了までの詳細なステータス取得
- **セキュリティ**: 機密データのメモリクリア処理を内蔵

---

## 📦 導入方法

### 1. 依存関係の追加

プロジェクトの `build.gradle.kts` に SDK モジュールを追加します。

```kotlin
dependencies {
    implementation(project(":sdk"))
    // または AAR を使用する場合
    // implementation(files("libs/epassport-sdk-release.aar"))
}
```

### 2. AndroidManifest.xml の設定

NFC を使用するため、以下の権限と機能を宣言する必要があります。

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.NFC" />
    <uses-feature android:name="android.hardware.nfc" android:required="true" />

    <application>
        <!-- 必要に応じて NFC の Intent Filter を Activity に追加 -->
    </application>
</manifest>
```

---

## 💡 使用例

`EPassportReader` を使用して、NFC タグと MRZ 情報からパスポートデータを読み取ります。

```kotlin
import androidx.lifecycle.lifecycleScope
import com.example.epassport.api.EPassportReader
import com.example.epassport.api.ReadResult
import com.example.epassport.domain.model.MrzData
import kotlinx.coroutines.launch

// 1. OCR 等で取得した MRZ 情報を準備
val mrz = MrzData(
    documentNumber = "L898902C<", 
    dateOfBirth = "690806", 
    dateOfExpiry = "940623"
)

// 2. NFC Tag 検知時に読み取り開始
lifecycleScope.launch {
    val result = EPassportReader.read(tag = nfcTag, mrzData = mrz) { progress ->
        when (progress) {
            ReadProgress.CONNECTING -> println("接続中...")
            ReadProgress.AUTHENTICATING -> println("認証中 (BAC)...")
            ReadProgress.READING_DG1 -> println("DG1 (MRZ) 読み取り中...")
            ReadProgress.READING_DG2 -> println("DG2 (顔写真) 読み取り中...")
            ReadProgress.SUCCESS -> println("読み取り完了")
            ReadProgress.ERROR -> println("エラー発生")
        }
    }

    when (result) {
        is ReadResult.Success -> {
            val data = result.data
            // DG1: 氏名や生年月日へのアクセス
            println("Name: ${data.dg1.primaryIdentifier} ${data.dg1.secondaryIdentifier}")
            
            // DG2: 顔写真のバイナリ取得
            data.dg2?.let { face ->
                val bitmap = BitmapFactory.decodeByteArray(face.faceImageBytes, 0, face.faceImageBytes.size)
                // 利用後はメモリからクリアすることを推奨
                face.clear()
            }
        }
        is ReadResult.Error -> {
            println("Error: ${result.exception.message}")
        }
    }
}
```

---

## 📱 検証用（テスト用）アプリの使い方

SDK の動作を実際の Android 端末ですぐに確認できるように、簡易的な検証用アプリモジュール（`:app`）を同梱しています。

1. **プロジェクトを開く**
   Android Studio でこのリポジトリのルートフォルダを開きます。
2. **モジュールの選択と実行**
   上部の実行構成（Run Configuration）から **`app`** モジュールを選択し、お手持ちの Android 実機をUSB接続して「Run (実行)」ボタンをクリックします。
3. **実機での読み取りテスト**
   - 画面に表示される MRZ（旅券番号、生年月日、有効期限）の入力欄をご自身のパスポートのものに書き換えます（ダミーデータが初期入力されています）。
   - パスポートをスマートフォンの NFC センサー（背面等）にかざすと、自動で検知して SDK 経由での認証と読み取りプロセスが開始されます。
   - ※ デフォルトでは `DataGroupReader` のスタブ（仮実装）が動作するようになっており、読み取りフロー全体の動作確認が可能です。実際のバイナリ解析を行うには、ご自身の Reader 実装を組み込んでください。

---

## 🏗 アーキテクチャ

Clean Architecture に基づいた堅牢な設計を採用しています。

```text
[ API Layer ]         - EPassportReader (Facade)
      |
[ UseCase Layer ]     - ReadPassportUseCase (Orchestration)
      |
[ Domain Layer ]      - PassportData, MrzData (Models)
      |               - NfcTransceiver, PassportAuthenticator (Interfaces)
      |
[ Data Layer ]        - BacAuthenticator (BAC Auth logic)
                      - SecureMessaging (Encryption/MAC)
                      - IcaoDataGroupReader (APDU communication)
                      - TlvParser (BER-TLV decoding)
```

---

## 🔒 セキュリティ

- **鍵の破棄**: BAC 認証で使用する `BacKey` は、認証成功後すぐにメモリから消去されます。
- **画像データの保護**: `Dg2Data` は RAW バイトを保持しますが、`clear()` メソッドを呼び出すことで明示的にメモリ上のデータをゼロクリアできます。

---

## 🛠 開発とテスト

### ビルド
```bash
./gradlew :sdk:assembleRelease
```

### テストの実行
```bash
./gradlew :sdk:test
```
ユニットテストは `sdk/src/test/kotlin` にあり、認証フローや TLV パースのロジックをカバーしています。

---

## 📝 ライセンス

このプロジェクトは MIT ライセンスの下で公開されています。
詳細は [LICENSE](LICENSE) ファイルを参照してください。



