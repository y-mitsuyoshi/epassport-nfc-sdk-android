package com.example.epassport.data.auth

import com.example.epassport.domain.exception.InvalidDataException
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

/**
 * SOD (Document Security Object) をパースして各 DG の期待ハッシュ値を取得する。
 *
 * 完全な Passive Authentication（署名検証・CSCA 証明書チェーン検証）は行わず、
 * SOD 内のハッシュ値と実際に読み取った DG バイト列のハッシュを照合する機能を提供する。
 */
object SodParser {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * SOD から各 DG の期待ハッシュ値を抽出する。
     *
     * @param sodBytes EF.SOD ファイルの生バイト列
     * @return DG 番号 (1, 2, 3, ...) から期待ハッシュ値へのマップ
     */
    fun parseDataGroupHashes(sodBytes: ByteArray): Map<Int, ByteArray> {
        return try {
            val cms = CMSSignedData(sodBytes)
            val content = cms.signedContent.content as? ByteArray
                ?: throw InvalidDataException("SOD signed content is not a byte array")

            // LDSSecurityObject ::= SEQUENCE { version, hashAlgorithm, dataGroupHashValues }
            val ldsSequence = ASN1Sequence.getInstance(content)
            if (ldsSequence.size() < 3) {
                throw InvalidDataException("Invalid LDSSecurityObject structure")
            }
            val dataGroupHashValues = ASN1Sequence.getInstance(ldsSequence.getObjectAt(2))

            val hashes = mutableMapOf<Int, ByteArray>()
            for (i in 0 until dataGroupHashValues.size()) {
                val dgh = ASN1Sequence.getInstance(dataGroupHashValues.getObjectAt(i))
                if (dgh.size() != 2) continue
                val dgNumber = ASN1Integer.getInstance(dgh.getObjectAt(0)).value.toInt()
                val hashValue = DEROctetString.getInstance(dgh.getObjectAt(1)).octets
                hashes[dgNumber] = hashValue
            }
            hashes
        } catch (e: Exception) {
            throw InvalidDataException("Failed to parse SOD data group hashes", e)
        }
    }

    /**
     * SOD から署名アルゴリズムの OID を取得する。
     */
    fun parseDigestAlgorithm(sodBytes: ByteArray): String {
        return try {
            val cms = CMSSignedData(sodBytes)
            val digestAlgorithms = cms.digestAlgorithmIDs
            if (digestAlgorithms.isEmpty()) {
                throw InvalidDataException("SOD does not contain digest algorithms")
            }
            (digestAlgorithms.iterator().next() as AlgorithmIdentifier).algorithm.id
        } catch (e: Exception) {
            throw InvalidDataException("Failed to parse SOD digest algorithm", e)
        }
    }

    /**
     * 指定された DG 番号に対応するハッシュアルゴリズム名を返す。
     *
     * ICAO 9303 Part 11/12 では SHA-1 または SHA-256 が一般的。
     */
    fun digestAlgorithmOidToName(oid: String): String {
        return when (oid) {
            "1.3.14.3.2.26" -> "SHA-1"
            "2.16.840.1.101.3.4.2.1" -> "SHA-256"
            "2.16.840.1.101.3.4.2.2" -> "SHA-384"
            "2.16.840.1.101.3.4.2.3" -> "SHA-512"
            else -> throw IllegalArgumentException("Unsupported digest algorithm OID: $oid")
        }
    }

    /**
     * データグループのハッシュ値を計算する。
     */
    fun computeHash(data: ByteArray, algorithmName: String): ByteArray {
        val digest = MessageDigest.getInstance(algorithmName)
        digest.update(data)
        return digest.digest()
    }

    /**
     * 実際に読み取った DG バイト列のハッシュと、SOD 内の期待ハッシュを照合する。
     *
     * @return すべての DG でハッシュが一致すれば true
     */
    fun verifyHashes(sodBytes: ByteArray, dataGroups: Map<Int, ByteArray>): Boolean {
        val expectedHashes = parseDataGroupHashes(sodBytes)
        val digestAlgorithmOid = parseDigestAlgorithm(sodBytes)
        val algorithmName = digestAlgorithmOidToName(digestAlgorithmOid)

        for ((dgNumber, dgBytes) in dataGroups) {
            val expected = expectedHashes[dgNumber] ?: return false
            val actual = computeHash(dgBytes, algorithmName)
            if (!MessageDigest.isEqual(expected, actual)) {
                return false
            }
        }
        return true
    }
}
