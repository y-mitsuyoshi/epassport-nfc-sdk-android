package com.example.epassport.domain.exception

/** SDK 基底例外 */
open class EPassportException(message: String, cause: Throwable? = null)
    : Exception(message, cause)

/** APDU ステータスワードエラー (SW != 9000) */
class ApduException(val sw1: Int, val sw2: Int, message: String)
    : EPassportException(message)

/** BAC/PACE 認証失敗 */
class AuthenticationException(message: String, cause: Throwable? = null)
    : EPassportException(message, cause)

/** NFC タグロスト */
class NfcTagLostException(cause: Throwable? = null)
    : EPassportException("NFC tag was lost during communication", cause)

/** データフォーマット・パース関連のエラー */
class InvalidDataException(message: String, cause: Throwable? = null)
    : EPassportException(message, cause)
