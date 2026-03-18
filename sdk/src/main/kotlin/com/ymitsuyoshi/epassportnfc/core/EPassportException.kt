package com.ymitsuyoshi.epassportnfc.core

/**
 * Base exception class for all ePassport SDK errors.
 *
 * @param message Human-readable description of the error.
 * @param cause The underlying cause, if any.
 */
sealed class EPassportException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Thrown when NFC communication with the passport chip fails. */
    class NfcCommunicationException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)

    /** Thrown when Basic Access Control (BAC) authentication fails. */
    class BacAuthenticationException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)

    /** Thrown when Password Authenticated Connection Establishment (PACE) fails. */
    class PaceAuthenticationException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)

    /** Thrown when data read from the passport chip cannot be parsed or validated. */
    class DataParsingException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)

    /** Thrown when a required data group is not found on the passport chip. */
    class DataGroupNotFoundException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)

    /** Thrown when passive authentication of the passport data fails. */
    class PassiveAuthenticationException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)

    /** Thrown when an unsupported operation or feature is encountered. */
    class UnsupportedFeatureException(message: String, cause: Throwable? = null) :
        EPassportException(message, cause)
}
