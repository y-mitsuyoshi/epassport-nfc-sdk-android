package com.ymitsuyoshi.epassportnfc.domain.repository

import com.ymitsuyoshi.epassportnfc.domain.model.DataGroup
import com.ymitsuyoshi.epassportnfc.domain.model.PassportData
import com.ymitsuyoshi.epassportnfc.domain.model.PersonalData

/**
 * Repository interface that defines the contract for reading data from an ePassport chip.
 *
 * Implementations in the `data` layer communicate with the physical NFC chip via APDU commands,
 * performing BAC or PACE authentication before reading the requested data groups.
 */
interface IPassportRepository {

    /**
     * Reads and returns the complete passport data including MRZ personal data and face image.
     *
     * @param bacKey The BAC key derived from the MRZ (document number, date of birth, expiry date).
     * @param dataGroups The set of data groups to read. Defaults to DG1 and DG2.
     * @return [PassportData] containing all successfully read data groups.
     * @throws com.ymitsuyoshi.epassportnfc.core.EPassportException on any read or auth failure.
     */
    suspend fun readPassport(
        bacKey: BacKey,
        dataGroups: Set<DataGroup> = setOf(DataGroup.DG1, DataGroup.DG2),
    ): PassportData

    /**
     * Reads only the personal data (MRZ, DG1) from the passport chip.
     *
     * @param bacKey The BAC key derived from the MRZ.
     * @return [PersonalData] parsed from the MRZ data group.
     * @throws com.ymitsuyoshi.epassportnfc.core.EPassportException on any read or auth failure.
     */
    suspend fun readPersonalData(bacKey: BacKey): PersonalData

    /**
     * Checks whether the NFC tag currently in the field is an ePassport chip.
     *
     * @return True if the tag responds to ePassport APDU select commands.
     */
    suspend fun isEPassport(): Boolean
}

/**
 * Holds the three MRZ fields required to derive the BAC session key.
 *
 * @property documentNumber Passport document number (9 characters, padded with '<').
 * @property dateOfBirth Date of birth in YYMMDD format.
 * @property dateOfExpiry Expiry date in YYMMDD format.
 */
data class BacKey(
    val documentNumber: String,
    val dateOfBirth: String,
    val dateOfExpiry: String,
)
