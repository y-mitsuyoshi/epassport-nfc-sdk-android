package com.ymitsuyoshi.epassportnfc.domain.model

import java.util.Date

/**
 * Represents the personal data read from ePassport data group 1 (DG1 / MRZ).
 *
 * @property surname Holder's surname as printed on the MRZ.
 * @property givenNames Holder's given names as printed on the MRZ.
 * @property documentNumber Passport document number (9 characters).
 * @property nationality Three-letter nationality code (ICAO 3166-1 alpha-3).
 * @property dateOfBirth Holder's date of birth.
 * @property gender Holder's gender ('M', 'F', or '<' for unspecified).
 * @property dateOfExpiry Passport expiry date.
 * @property personalNumber Optional personal number / national ID from MRZ.
 */
data class PersonalData(
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val nationality: String,
    val dateOfBirth: Date,
    val gender: Char,
    val dateOfExpiry: Date,
    val personalNumber: String? = null,
) {
    /** The holder's full name (given names followed by surname). */
    val fullName: String get() = "$givenNames $surname".trim()

    /** Returns true when the passport has not yet passed its expiry date. */
    fun isValid(referenceDate: Date = Date()): Boolean = dateOfExpiry.after(referenceDate)
}
