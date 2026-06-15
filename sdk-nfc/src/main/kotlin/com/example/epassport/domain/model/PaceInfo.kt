package com.example.epassport.domain.model

/**
 * PACEInfo の内容を表すデータクラス。
 *
 * @param protocolOid PACE プロトコルの OID
 * @param version PACE バージョン（通常 2）
 * @param parameterId 楕円曲線・暗号パラメータ ID（省略可能）
 */
data class PaceInfo(
    val protocolOid: String,
    val version: Int,
    val parameterId: Int?
) {
    /** ECDH を使用するかどうか */
    val isEcdh: Boolean get() = protocolOid.contains("2.2.4.2")

    /** AES-CMAC を使用するかどうか */
    val isAesCmac: Boolean get() = protocolOid.contains("AES-CBC-CMAC")

    /** 鍵長（128/192/256）。3DES の場合は 112 相当 */
    val keyLength: Int get() = when {
        protocolOid.endsWith(".4") || protocolOid.endsWith(".8") || protocolOid.endsWith(".12") -> 256
        protocolOid.endsWith(".3") || protocolOid.endsWith(".7") || protocolOid.endsWith(".11") -> 192
        protocolOid.endsWith(".2") || protocolOid.endsWith(".6") || protocolOid.endsWith(".10") -> 128
        else -> 112
    }
}
