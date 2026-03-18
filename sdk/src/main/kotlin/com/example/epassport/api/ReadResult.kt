package com.example.epassport.api

import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.model.PassportData

/**
 * パスポートの読み取り結果を表すシールドクラス。
 */
sealed class ReadResult {
    data class Success(val data: PassportData) : ReadResult()
    data class Error(val exception: EPassportException) : ReadResult()
}
