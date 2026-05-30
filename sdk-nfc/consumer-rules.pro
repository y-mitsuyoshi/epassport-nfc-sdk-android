# sdk-nfc consumer ProGuard rules
# These rules are applied to any app that depends on sdk-nfc.

# BouncyCastle: リフレクション経由でアルゴリズムが登録されるため保護が必要
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ePassport SDK パブリック API を保護
-keep class com.example.epassport.api.** { *; }
-keep class com.example.epassport.domain.model.** { *; }
-keep class com.example.epassport.domain.exception.** { *; }
