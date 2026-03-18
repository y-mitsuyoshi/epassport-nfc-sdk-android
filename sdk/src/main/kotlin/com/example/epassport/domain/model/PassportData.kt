package com.example.epassport.domain.model

/**
 * SDK が最終的に返却するパスポートデータ
 */
data class PassportData(
    val dg1: Dg1Data,
    val dg2: Dg2Data? = null // 顔写真が含まれない場合を考慮
)
