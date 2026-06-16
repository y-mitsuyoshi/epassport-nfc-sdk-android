package com.example.epassport.usecase

import com.example.epassport.domain.model.ActiveAuthenticationData
import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data

/**
 * NFC 読み取り処理の途中で成功したデータを一時キャッシュするためのデータ保持クラス。
 * 瞬断からの復帰（レジューム）に使用されます。
 */
data class CachedPassportData(
    val dg1: Dg1Data? = null,
    val dg2: Dg2Data? = null,
    val sodBytes: ByteArray? = null,
    val aaData: ActiveAuthenticationData? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * キャッシュの有効期限が切れているかどうかを判定します（デフォルト: 5分）。
     */
    fun isExpired(timeoutMs: Long = 300000): Boolean {
        return System.currentTimeMillis() - timestamp > timeoutMs
    }
}
