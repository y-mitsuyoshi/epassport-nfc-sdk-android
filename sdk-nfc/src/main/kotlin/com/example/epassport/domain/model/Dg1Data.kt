package com.example.epassport.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * DG1（MRZ テキスト情報）をパースした結果。
 */
@Serializable
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
    val secondaryIdentifier: String, // 名
    @Transient
    val rawBytes: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Dg1Data) return false
        return documentCode == other.documentCode &&
                issuingState == other.issuingState &&
                documentNumber == other.documentNumber &&
                optionalData1 == other.optionalData1 &&
                dateOfBirth == other.dateOfBirth &&
                sex == other.sex &&
                dateOfExpiry == other.dateOfExpiry &&
                nationality == other.nationality &&
                optionalData2 == other.optionalData2 &&
                primaryIdentifier == other.primaryIdentifier &&
                secondaryIdentifier == other.secondaryIdentifier
    }

    override fun hashCode(): Int {
        var result = documentCode.hashCode()
        result = 31 * result + issuingState.hashCode()
        result = 31 * result + documentNumber.hashCode()
        result = 31 * result + optionalData1.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        result = 31 * result + sex.hashCode()
        result = 31 * result + dateOfExpiry.hashCode()
        result = 31 * result + nationality.hashCode()
        result = 31 * result + optionalData2.hashCode()
        result = 31 * result + primaryIdentifier.hashCode()
        result = 31 * result + secondaryIdentifier.hashCode()
        return result
    }
}
