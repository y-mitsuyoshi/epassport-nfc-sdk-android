package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException
import com.example.epassport.domain.model.PaceInfo
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence

/**
 * EF.CardAccess から PACEInfo をパースする。
 */
object PaceInfoParser {

    /**
     * EF.CardAccess の生バイト列から PACEInfo を抽出する。
     *
     * @param cardAccessBytes EF.CardAccess ファイルの内容
     * @return 最初に見つかった PACEInfo。PACEInfo がない場合は null
     */
    fun parse(cardAccessBytes: ByteArray): PaceInfo? {
        return try {
            val root = ASN1Sequence.getInstance(cardAccessBytes)
            for (i in 0 until root.size()) {
                val seq = ASN1Sequence.getInstance(root.getObjectAt(i))
                if (seq.size() < 2) continue
                val oid = ASN1ObjectIdentifier.getInstance(seq.getObjectAt(0)).id
                if (isPaceOid(oid)) {
                    val version = ASN1Integer.getInstance(seq.getObjectAt(1)).value.toInt()
                    val parameterId = if (seq.size() > 2) {
                        ASN1Integer.getInstance(seq.getObjectAt(2)).value.toInt()
                    } else null
                    return PaceInfo(oid, version, parameterId)
                }
            }
            null
        } catch (e: Exception) {
            throw InvalidDataException("Failed to parse PACEInfo", e)
        }
    }

    private fun isPaceOid(oid: String): Boolean {
        return oid.startsWith("0.4.0.127.0.7.2.2.4.")
    }
}
