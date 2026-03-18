package com.example.epassport.domain.port

import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data

/**
 * 認証確立後のセキュアチャネルで DG を読み取る。
 */
interface DataGroupReader {
    suspend fun readDg1(transceiver: NfcTransceiver): Dg1Data
    suspend fun readDg2(transceiver: NfcTransceiver): Dg2Data
}
