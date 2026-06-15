# Agent Instructions

## Project Overview
- **Name**: EPassportNfcSdk
- **Type**: Android multi-module project (libraries + sample app)
- **Modules**: `:sdk-nfc`, `:sdk-ocr`, `:app`
- **Build Tool**: Gradle with Kotlin DSL
- **Test Framework**: JUnit + MockK + Robolectric (`sdk-nfc`), JUnit (`sdk-ocr` pending)

## Quality Gates & Git Hooks

### Pre-Push Hook (`pre-push`)
プッシュ前に **必ず** 以下が自動実行されます。いずれかが失敗すればプッシュは中止されます：
1. `./gradlew :sdk-nfc:compileReleaseKotlin :sdk-ocr:compileReleaseKotlin :app:compileReleaseKotlin`
2. `./gradlew :sdk-nfc:testDebugUnitTest :sdk-ocr:testDebugUnitTest :app:testDebugUnitTest`
3. `./gradlew :sdk-nfc:lint :sdk-ocr:lint :app:lint`

### Pre-Commit Hook (`pre-commit`)
コミット対象に `src/main/.../*.kt` が含まれる場合、対応する `src/test/.../*Test.kt` の存在を確認します。
テストがない場合、ターミナルに警告が表示されます（コミットはブロックしません）。

### prepare-commit-msg Hook
コミットメッセージ編集テンプレートに、未テストのファイル一覧がコメントとして追加されます。

### Hook Installation
初回セットアップ時に以下を実行してください：

```bash
# 手動インストール
chmod +x scripts/git-hooks/* && cp scripts/git-hooks/* .git/hooks/

# または Gradleタスク
./gradlew installGitHooks
```

## AI Tool Configurations

### Antigravity (Gemini) Harness
`.gemini/settings.json` に `PreToolUse` フックが設定されています。
`run_command` ツール使用前に自動的にビルド・テスト・Lintが実行されます。
- **注意**: モジュール名は `:sdk-nfc:` `:sdk-ocr:` `:app:` を使用。古い `:sdk:` は使いません。

### Claude Harness
`.claude/settings.json` も同様の `PreToolUse` フックを持ちます。

### Opencode Harness
`.opencode/opencode.json` に `verify` コマンドが定義されています。
`.opencode/plugin/quality-gate.ts` により、`./gradlew` コマンド実行前に品質ゲートが走ります。

## Unit Test Policy
- `src/main/kotlin/.../Foo.kt` を追加・変更したら、原則として `src/test/kotlin/.../FooTest.kt` を作成してください。
- `sdk-ocr` は現在テストが不足しています。新規開発時は優先的にテストを追加してください。
- `sdk-nfc` のテストカバレッジは Jacoco で計測されます（`./gradlew :sdk-nfc:jacocoTestReport`）。

## Module Details
| Module | Type | Has Tests | Lint | Jacoco |
|--------|------|-----------|------|--------|
| `:sdk-nfc` | Android Library | Yes | Yes | Yes |
| `:sdk-ocr` | Android Library | No (pending) | Yes | No |
| `:app` | Android Application | No | Yes | No |

## Coding Conventions
- Kotlin, JVM target 1.8
- `SdkNfc` / `SdkOcr` prefix for public API classes where appropriate
- Keep `domain/` layer pure (no Android framework dependencies)
