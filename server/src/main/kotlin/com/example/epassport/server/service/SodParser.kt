package com.example.epassport.server.service

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cms.CMSSignedData
import java.security.MessageDigest

/**
 * サーバー側用 SOD パーサー。
 */
object SodParser {

    fun parseDataGroupHashes(sodBytes: ByteArray): Map<Int, ByteArray> {
        val cms = CMSSignedData(sodBytes)
        val content = cms.signedContent.content as? ByteArray
            ?: throw IllegalArgumentException("SOD signed content is not a byte array")

        val ldsSequence = ASN1Sequence.getInstance(content)
        require(ldsSequence.size() >= 3) { "Invalid LDSSecurityObject structure" }
        val dataGroupHashValues = ASN1Sequence.getInstance(ldsSequence.getObjectAt(2))

        val hashes = mutableMapOf<Int, ByteArray>()
        for (i in 0 until dataGroupHashValues.size()) {
            val dgh = ASN1Sequence.getInstance(dataGroupHashValues.getObjectAt(i))
            if (dgh.size() != 2) continue
            val dgNumber = ASN1Integer.getInstance(dgh.getObjectAt(0)).value.toInt()
            val hashValue = DEROctetString.getInstance(dgh.getObjectAt(1)).octets
            hashes[dgNumber] = hashValue
        }
        return hashes
    }

    fun parseDigestAlgorithm(sodBytes: ByteArray): String {
        val cms = CMSSignedData(sodBytes)
        val digestAlgorithms = cms.digestAlgorithmIDs
        require(digestAlgorithms.isNotEmpty()) { "SOD does not contain digest algorithms" }
        return (digestAlgorithms.iterator().next() as AlgorithmIdentifier).algorithm.id
    }

    fun digestAlgorithmOidToName(oid: String): String {
        return when (oid) {
            "1.3.14.3.2.26" -> "SHA-1"
            "2.16.840.1.101.3.4.2.1" -> "SHA-256"
            "2.16.840.1.101.3.4.2.2" -> "SHA-384"
            "2.16.840.1.101.3.4.2.3" -> "SHA-512"
            else -> throw IllegalArgumentException("Unsupported digest algorithm OID: $oid")
        }
    }

    fun computeHash(data: ByteArray, algorithmName: String): ByteArray {
        val digest = MessageDigest.getInstance(algorithmName)
        digest.update(data)
        return digest.digest()
    }

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
