package com.example.epassport.data.parser

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaceInfoParserTest {

    @Test
    fun parse_withPaceInfo_returnsPaceInfo() {
        val paceInfo = DERSequence(arrayOf(
            ASN1ObjectIdentifier("0.4.0.127.0.7.2.2.4.2.4"),
            ASN1Integer(2),
            ASN1Integer(0x0D)
        ))
        val cardAccess = DERSequence(arrayOf(paceInfo))

        val result = PaceInfoParser.parse(cardAccess.encoded)

        assertNotNull(result)
        assertEquals("0.4.0.127.0.7.2.2.4.2.4", result!!.protocolOid)
        assertEquals(2, result.version)
        assertEquals(0x0D, result.parameterId)
    }

    @Test
    fun parse_withoutPaceInfo_returnsNull() {
        val cardAccess = DERSequence(arrayOf())

        val result = PaceInfoParser.parse(cardAccess.encoded)

        assertNull(result)
    }

    @Test
    fun parse_withNonPaceOid_returnsNull() {
        val otherInfo = DERSequence(arrayOf(
            ASN1ObjectIdentifier("1.2.3.4.5"),
            ASN1Integer(1)
        ))
        val cardAccess = DERSequence(arrayOf(otherInfo))

        val result = PaceInfoParser.parse(cardAccess.encoded)

        assertNull(result)
    }
}
