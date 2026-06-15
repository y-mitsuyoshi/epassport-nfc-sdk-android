package com.example.epassport.domain.port

import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data

/**
 * 認証確立後のセキュアチャネルで DG を読み取る。
 */
interface DataGroupReader {
    suspend fun readDg1(transceiver: NfcTransceiver): Dg1Data
    suspend fun readDg2(transceiver: NfcTransceiver): Dg2Data
    
    /**
     * DG15 (Active Authentication Public Key Info) を生バイトとして読み取ります。
     */
    suspend fun readDg15(transceiver: NfcTransceiver): ByteArray

    /**
     * DG14 (Security Infos: Chip Authentication パラメータ) を生バイトとして読み取ります。
     */
    suspend fun readDg14(transceiver: NfcTransceiver): ByteArray

    /**
     * SOD (Document Security Object: Passive Authentication 用) を生バイトとして読み取ります。
     */
    suspend fun readSod(transceiver: NfcTransceiver): ByteArray

    /**
     * チップに対して INTERNAL AUTHENTICATE を送信し、署名（レスポンス）を取得します。
     */
    suspend fun performActiveAuthentication(transceiver: NfcTransceiver, challenge: ByteArray): ByteArray
}

