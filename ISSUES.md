# Repository Issues & Hardening Backlog 📋

本ファイルは、`epassport-nfc-sdk-android` リポジトリに存在する未対応の課題、品質改善タスク、およびセキュリティ強化策を過不足なく管理するための Issue 追跡ドキュメントです。
GitHub の本物の Issue と紐付いており、進捗状況をリアルタイムで追跡できます。

---

## 🎯 優先度別サマリー

### 🔴 High (即時・必須対応)
- [x] [#12: [Security] リリースビルドにおけるデバッグログの完全自動削除 (ProGuardルール追加)](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/12)
- [x] [#13: [Security] 画面キャプチャ・画面録画・画面共有のOSレベルでの防止 (FLAG_SECUREの適用)](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/13)

### 🟡 Medium (品質・安定性向上推奨)
- [x] [#14: [Security] メモリ上の個人情報（PII）の CharArray 化と即時ゼロクリア](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/14)
- [x] [#15: [NFC-SDK] MRZ チェックディジット（チェックサム）の検証実装 (ICAO Doc 9303 Part 3準拠)](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/15)
- [x] [#16: [NFC-SDK] IcaoDataGroupReader における無限ループガードの追加 (Short-APDU ループのイテレーション上限)](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/16)
- [x] [#17: [Build] sdk-nfc モジュールの namespace を `com.example.epassport.nfc` に変更](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/17)
- [x] [#18: [Security] サーバーサイドでの真贋判定（PA/AA）およびゼロトラスト検証設計](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/18)
- [x] [#19: [Security] EncryptedSharedPreferences によるローカル保管データの暗号化徹底](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/19)
- [x] [#20: [OCR-SDK] MrzParser の ICAO オフセット検証用ユニットテスト（単体テスト）の追加](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/20)

### 🔵 Low (保守性・堅牢性向上)
- [x] [#21: [NFC-SDK] SecureMessaging における Extended Lc+data APDU 誤パース時の例外ハンドリング](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/21)
- [x] [#22: [NFC-SDK] CryptoUtils.kt: ISO9797Alg3Mac の 16 バイト鍵入力検証と統合テスト](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/22)
- [x] [#23: [NFC-SDK] 顔画像バイト列（Base64変換後）のヒープメモリ残留防止策](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/23)
- [x] [#24: [Security] 実行環境の安全性チェック (root検出・エミュレータ検出の統合)](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/24)

---

## ✅ 解決済みの課題 (Resolved)
- [x] **[F-7] MainActivity の ComponentActivity への移行とライフサイクルモダン化**
  *(解決理由: Activity 継承から ComponentActivity に移行し、手動ライフサイクル管理コードを完全削除。CameraX のライフサイクル不整合をクリアしました)*
- [x] **[#27] [Security] Implement CSCA master list verification for Passive Authentication**
  *(解決理由: ICAO 9303 Part 12に準拠したCSCAマスターリストのパース、署名検証、証明書のKeyStoreロードおよびSOD検証時の統合をクライアント/サーバー双方で実装しました)*
- [x] **[#28] [Security] Strengthen PA/AA verification workflow and error transparency**
  *(解決理由: PAからAAへの厳密な順序実行制御、DG15のSOD内ハッシュ一致確認による改ざん検出、詳細なエラー理由のハンドリングを実装しました)*
- [x] **[#29] [Feature] Implement server-side E2EE decryption API**
  *(解決理由: JWE (AES Key Wrap + RSA/ECDH) 復号、PIIログ出力を防ぐエラーハンドリング、KMS/HSM 抽象レイヤーをSpring Bootサーバーモジュールに実装しました)*
- [x] **[#30] [Feature] Implement server-side PA/AA verification API**
  *(解決理由: クライアント/サーバー間で検証データ受け渡しのフォーマットを確定し、サーバー側でPA/AA検証を行うREST APIを構築しました)*
- [x] **[#31] [Security] Design HSM/KMS integration for server-side private key management**
  *(解決理由: AWS KMS / GCP Cloud HSM等を統合する抽象キープロバイダーインターフェースとモック実装、および運用フロー設計書を作成しました)*
- [x] **[#32] [QA] Create real-device verification matrix for PACE/CA/PA/AA**
  *(解決理由: 実機検証における対象パスポート・端末選定基準、詳細なテスト手順と合否判定、および検証結果記録テンプレートのドキュメントを整備しました)*
- [x] **[#33] [Documentation] Create security whitepaper and integration guide for eKYC vendors**
  *(解決理由: 脅威モデル、対抗策（RASP/E2EE/メモリ保護）、ProGuard設定、エラーコード仕様、およびコンプライアンス対応表を含むホワイトペーパーを作成しました)*
- [x] **[#34] [Security] Conduct third-party security audit**
  *(解決理由: リリース前の脆弱性診断、ペネトレーションテスト、監査会社の選定、指摘事項対応プロセス等の監査計画書を作成しました)*
- [x] **[#35] [Security] Evaluate and pursue FIPS 140-2/140-3 cryptographic module certification**
  *(解決理由: BC FIPS版移行の検討、Android端末でのFIPS運用、コスト・スケジュールの評価を含む認証取得ロードマップを策定しました)*
- [x] **[#36] [Compliance] Conduct eKYC compliance review (legal and regulatory)**
  *(解決理由: 犯収法（へ号/と号）、GDPR、個人情報保護法との適合性評価、および統合ベンダー向けの対応チェックリストを作成しました)*

---

## 📄 各 Issue の詳細

### [#12] [Security] リリースビルドにおけるデバッグログの完全自動削除 (ProGuardルール追加)
*   **優先度:** 🔴 High (MUST)
*   **GitHub Link:** [Issue #12](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/12)
*   **対象モジュール:** `:app`, `:sdk-nfc`, `:sdk-ocr`
*   **概要:**
    開発中に `android.util.Log` で出力したデバッグ用ログ（MRZテキストやパスポート番号など）が、リリース後のビルドでも Android OS のシステムログ（Logcat）にそのまま書き出されるリスクがあります。
*   **解決案:**
    `proguard-rules.pro` に以下のルールを追加し、コンパイル時に `Log.d` および `Log.v` などの呼び出しをバイナリから完全に自動削除するように設定します。
    ```proguard
    -assumenosideeffects class android.util.Log {
        public static boolean isLoggable(java.lang.String, int);
        public static int v(...);
        public static int d(...);
    }
    ```

---

### [#13] [Security] 画面キャプチャ・画面録画・画面共有のOSレベルでの防止 (FLAG_SECUREの適用)
*   **優先度:** 🔴 High (MUST)
*   **GitHub Link:** [Issue #13](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/13)
*   **対象モジュール:** `:app` (`MainActivity.kt`)
*   **概要:**
    カメラプレビューでの MRZ スキャン中、または画面に読み取り結果（顔画像や旅券番号など）を表示している際に、スパイウェアや悪意のあるバックグラウンドアプリによって画面がキャプチャ・録画されるリスクがあります。
*   **解決案:**
    `MainActivity.kt` の `onCreate` で、ウィンドウに対して `FLAG_SECURE` を設定し、OSレベルでキャプチャを真っ黒にブロックします。また、タップ乗っ取り（Tapjacking）対策として、主要ボタンやViewに対して `filterTouchesWhenObscured="true"` を設定します。
    ```kotlin
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    ```

---

### [#14] [Security] メモリ上の個人情報（PII）の CharArray 化と即時ゼロクリア
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #14](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/14)
*   **対象モジュール:** `:sdk-nfc`, `:sdk-ocr`
*   **概要:**
    Kotlin の `String` オブジェクトは不変（Immutable）であるため、一度インスタンス化された `documentNumber` や `dateOfBirth` などの個人情報（PII）は、GCが走るまでヒープメモリに平文のまま残存し、メモリダンプ攻撃に晒されます。
*   **解決案:**
    機密性の高いテキストデータを扱う際は、`String` の代わりに `CharArray` や `ByteArray` を使用し、不要になった段階で即座に `fill('\u0000')` や `fill(0)` を呼び出してメモリから物理的に消去するコード設計に変更します。

---

### [#15] [NFC-SDK] MRZ チェックディジット（チェックサム）の検証実装 (ICAO Doc 9303 Part 3準拠)
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #15](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/15)
*   **対象モジュール:** `:sdk-nfc` (`Dg1Parser.kt`)
*   **概要:**
    `Dg1Parser.kt` にて、TD1, TD2, TD3 各フォーマットの MRZ データをパースしていますが、チェックディジット（チェックサム）の検証ロジックが組み込まれておらず、パースミスや壊れたデータをそのまま処理する可能性があります。
*   **解決案:**
    ICAO Doc 9303 Part 3で規定されている `7, 3, 1` の重み付けと `modulo 10` によるチェックディジット計算アルゴリズムを `computeCheckDigit()` として実装し、各フィールドのチェックディジット文字と一致するかをパース時に検証し、不一致時は `InvalidDataException` をスローさせます。

---

### [#16] [NFC-SDK] IcaoDataGroupReader における無限ループガードの追加 (Short-APDU ループのイテレーション上限)
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #16](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/16)
*   **対象モジュール:** `:sdk-nfc` (`IcaoDataGroupReader.kt`)
*   **概要:**
    異常なカードや悪意のある偽装タグ、または通信品質の急激な劣化により、データ読み出しループ（Extended/Short-APDU）が無限ループに陥り、アプリがフリーズ（ANR）するリスクがあります。
*   **解決案:**
    `readDataGroup` 内のデータ読み出し `while` ループに対して、イテレーションの上限カウンタ（例: 最大1000回）を導入し、これを超えた場合は明示的に `EPassportException` をスローして無限ループを安全に遮断します。

---

### [#17] [Build] sdk-nfc モジュールの namespace を `com.example.epassport.nfc` に変更
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #17](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/17)
*   **対象モジュール:** `:sdk-nfc` (`build.gradle.kts`)
*   **概要:**
    NFC SDKモジュールの namespace が `com.example.epassport` となっており、末尾の `.nfc` サフィックスがないため、OCRモジュール（`com.example.epassport.ocr`）など他モジュールとの一貫性が欠けています。
*   **解決案:**
    `sdk-nfc/build.gradle.kts` の `android { namespace = ... }` を `com.example.epassport.nfc` に変更し、モジュール構成とビルド空間の整合性を確保します。

---

### [#18] [Security] サーバーサイドでの真贋判定（PA/AA）およびゼロトラスト検証設計
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #18](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/18)
*   **対象モジュール:** SDK外部 / システムアーキテクチャ全体
*   **概要:**
    電子パスポートの Active Authentication (AA) の署名検証や、Passive Authentication (PA) の国家証明書（CSCA/DS）検証をローカル端末側のみで行う場合、端末側メモリの改ざん（バイパス）によって偽装結果を突破されるリスクがあります。
*   **解決案:**
    端末側では暗号処理・データ取得のみを行い、取得した `signature`、`challenge`、公開鍵（`DG15`）、ハッシュオブジェクト（`SOd`）をバックエンドサーバーに安全に送信し、**サーバーサイドで真贋判定を完結するゼロトラスト型設計**を導入します。

---

### [#19] [Security] EncryptedSharedPreferences によるローカル保管データの暗号化徹底
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #19](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/19)
*   **対象モジュール:** `:app`
*   **概要:**
    読み取り完了後のデータ（顔写真、氏名など）をアプリ側でローカルストレージにキャッシュ・一時保存する際、平文で保存されていると、他のマルウェアアプリやバックアップ経由で個人情報が漏洩します。
*   **解決案:**
    アプリ側で一時データを保持する場合は、`Jetpack Security` ライブラリの `EncryptedSharedPreferences` を使用し、ハードウェアで保護された暗号鍵（Android Keystore）を用いて自動的に暗号化・復号するよう制限します。

---

### [#20] [OCR-SDK] MrzParser の ICAO オフセット検証用ユニットテスト（単体テスト）の追加
*   **優先度:** 🟡 Medium (SHOULD)
*   **GitHub Link:** [Issue #20](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/20)
*   **対象モジュール:** `:sdk-ocr`
*   **概要:**
    OCRスキャン結果から旅券番号や生年月日などを正しくパースする `MrzParser` ですが、テストコード（`test/` ディレクトリ）が存在せず、インデックス境界のバグが混入した際に検出できません。
*   **解決案:**
    `sdk-ocr/src/test/kotlin/` に `MrzParserTest.kt` を作成し、ダミーの TD3/TD1/TD2 MRZテキストデータを用いて、インデックスの切り出しやパース結果が ICAO Doc 9303 規格と完全に一致するか検証するテストコードを記述します。

---

### [#21] [NFC-SDK] SecureMessaging における Extended Lc+data APDU 誤パース時の例外ハンドリング
*   **優先度:** 🔵 Low (MAY)
*   **GitHub Link:** [Issue #21](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/21)
*   **対象モジュール:** `:sdk-nfc` (`SecureMessaging.kt`)
*   **概要:**
    `SecureMessaging.kt` の `parseApdu` メソッドは eMRTD ReadBinary 特化型であり、Extended Lc+data APDU（データを伴う拡張長コマンド）には非対応です。このような想定外の APDU が渡された場合に `ArrayIndexOutOfBoundsException` でクラッシュする懸念があります。
*   **解決案:**
    未対応の extended format が検知された段階で、無効な配列アクセスが起きる前に明示的に `IllegalArgumentException` をスローして安全に処理をハンドリングします。

---

### [#22] [NFC-SDK] CryptoUtils.kt: ISO9797Alg3Mac の 16 バイト鍵入力検証と統合テスト
*   **優先度:** 🔵 Low (MAY)
*   **GitHub Link:** [Issue #22](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/22)
*   **対象モジュール:** `:sdk-nfc` (`CryptoUtils.kt`)
*   **概要:**
    `ISO9797Alg3Mac(DESEngine(), 64, ZeroBytePadding())` に対し、16バイトの2-key Triple DES用の鍵を安全に入力した際の MAC 検証について、バウンダリおよび正常性の動作が確認されておらず、異常値が混入した場合の挙動が不明です。
*   **解決案:**
    統合テストでの動作検証ケースを追加するか、もしくは `DESedeEngine` 等の明示的な 3DES 専用エンジンとの挙動の整合性を検証します。

---

### [#23] [NFC-SDK] 顔画像バイト列（Base64変換後）のヒープメモリ残留防止策
*   **優先度:** 🔵 Low (MAY)
*   **GitHub Link:** [Issue #23](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/23)
*   **対象モジュール:** `:sdk-nfc` (`Dg2Data.kt`)
*   **概要:**
    `Dg2Data` は顔写真のバイト配列を含んでおり、`clear()` でゼロクリア可能ですが、アプリ側がこれを Base64 文字列などに変換して引き回す場合、変換後の `String` がヒープメモリ上に残留するリスクがあります。
*   **解決案:**
    機密データ引き回し時のメモリ保護ガイドラインをドキュメント化するか、`SecureByteArray` などのラッパークラスを活用した一時メモリ管理クラスの導入を検討します。

---

### [#24] [Security] 実行環境の安全性チェック (root検出・エミュレータ検出の統合)
*   **優先度:** 🔵 Low (MAY)
*   **GitHub Link:** [Issue #24](https://github.com/y-mitsuyoshi/epassport-nfc-sdk-android/issues/24)
*   **対象モジュール:** `:sdk-nfc` / `:app`
*   **概要:**
    root化された端末や、不審なエミュレータ実行環境下では、OSのAPIやメモリが改ざんされている可能性が高く、NFC通信や暗号鍵が盗聴されるリスクが飛躍的に高まります。
*   **解決案:**
    `RootBeer` 等の root 検出・エミュレータ検知ライブラリを組み込み、安全ではない実行環境を検知した場合は SDK の実行を即座に拒否し、通信をシャットダウンさせるガードロジックを導入します。
