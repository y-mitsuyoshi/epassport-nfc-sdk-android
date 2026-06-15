package com.example.epassport.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.scottyab.rootbeer.RootBeer

/**
 * 実行環境の安全性を検証する RASP (Runtime Application Self-Protection) ユーティリティ。
 *
 * Root 化端末・エミュレータ・デバッグ接続など、安全ではない環境を検知する。
 */
class RuntimeSecurityChecker(
    private val context: Context,
    private val rootBeer: RootBeer = RootBeer(context.applicationContext)
) {

    /**
     * 現在の実行環境が安全かどうかを判定する。
     *
     * @return true: 安全と判断（Root/エミュレータ/デバッグ未検出）
     *         false: 安全ではない環境が検出された
     */
    fun isDeviceSecure(): Boolean {
        return !isRooted() && !isEmulator() && !isDebugBuild() && !isDebugged()
    }

    /**
     * 安全でない場合にその理由を返す。
     *
     * @return 検知された脅威のリスト。安全な場合は空リスト。
     */
    fun detectThreats(): List<String> {
        val threats = mutableListOf<String>()
        if (isRooted()) threats.add("rooted_device")
        if (isEmulator()) threats.add("emulator")
        if (isDebugBuild()) threats.add("debug_build")
        if (isDebugged()) threats.add("debugger_attached")
        return threats
    }

    private fun isRooted(): Boolean {
        return rootBeer.isRooted
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.lowercase().contains("emulator") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.lowercase().contains("emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BOARD.lowercase().contains("goldfish") ||
                Build.BOARD.contains("unknown") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("google_sdk") ||
                Build.PRODUCT.contains("sdk_x86") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.PRODUCT.contains("emulator") ||
                Build.PRODUCT.contains("simulator")
    }

    private fun isDebugBuild(): Boolean {
        return context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private fun isDebugged(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }

    /**
     * 改ざん検知（署名検証）の簡易実装。
     * 期待する署名証明書の SHA-256 フィンガープリントと比較する。
     *
     * @param expectedSignatures 期待する署名証明書の SHA-256 フィンガープリント（大文字16進数、コロンなし）のセット
     * @return true: 期待する署名のいずれかと一致
     */
    @Suppress("DEPRECATION")
    fun verifyAppSignature(expectedSignatures: Set<String>): Boolean {
        return try {
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners
            } else {
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }

            signatures?.any { sig ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(sig.toByteArray())
                val fingerprint = hash.joinToString("") { "%02X".format(it) }
                expectedSignatures.contains(fingerprint)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
