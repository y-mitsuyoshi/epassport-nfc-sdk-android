# sdk-nfc ProGuard rules for release builds

# リリースビルドでデバッグログ (Log.d / Log.v) を自動削除する
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
