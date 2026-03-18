package com.ymitsuyoshi.epassportnfc.presentation

import com.ymitsuyoshi.epassportnfc.core.EPassportException
import com.ymitsuyoshi.epassportnfc.domain.model.PassportData
import com.ymitsuyoshi.epassportnfc.domain.model.PersonalData

/**
 * Callback interface for receiving the results of an ePassport read operation.
 *
 * Implement this interface in your Activity or Fragment and pass it to the SDK
 * to receive notifications as the passport read progresses.
 */
interface PassportReaderCallback {

    /**
     * Called when the SDK begins communicating with the passport chip.
     * Use this to show a progress indicator.
     */
    fun onReadingStarted()

    /**
     * Called when the personal data (MRZ) has been successfully read and parsed.
     *
     * @param personalData The personal data parsed from DG1.
     */
    fun onPersonalDataRead(personalData: PersonalData)

    /**
     * Called when all requested data groups have been successfully read.
     *
     * @param passportData The complete passport data.
     */
    fun onPassportReadComplete(passportData: PassportData)

    /**
     * Called when the passport reading operation fails.
     *
     * @param exception The [EPassportException] describing the failure.
     */
    fun onReadingFailed(exception: EPassportException)

    /**
     * Called when the NFC tag leaves the field before the read is complete.
     */
    fun onTagLost()
}
