# sdk-ocr consumer ProGuard rules
# These rules are applied to any app that depends on sdk-ocr.

# CameraX: 内部リフレクションを使用するため保護が必要
-keep class androidx.camera.** { *; }
-keepclassmembers class androidx.camera.** { *; }

# ML Kit Text Recognition: 内部 GMS 実装を保護
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
