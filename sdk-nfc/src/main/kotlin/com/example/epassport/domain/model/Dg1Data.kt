package com.example.epassport.domain.model

/**
 * DG1（MRZ テキスト情報）をパースした結果。
 */
data class Dg1Data(
    val documentCode: String,
    val issuingState: String,
    val documentNumber: String,
    val optionalData1: String,
    val dateOfBirth: String,
    val sex: String,
    val dateOfExpiry: String,
    val nationality: String,
    val optionalData2: String,
    val primaryIdentifier: String, // 姓
    val secondaryIdentifier: String // 名
)
