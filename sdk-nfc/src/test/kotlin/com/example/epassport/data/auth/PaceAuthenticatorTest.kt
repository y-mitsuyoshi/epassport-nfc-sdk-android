package com.example.epassport.data.auth

import com.example.epassport.domain.model.MrzData
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class PaceAuthenticatorTest {

    private lateinit var authenticator: PaceAuthenticator

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Before
    fun setUp() {
        authenticator = PaceAuthenticator()
    }

    @Test
    fun derivePacePassword_returnsFirst16BytesOfSha1() {
        val mrzData = MrzData(
            documentNumber = "AB123456".toCharArray(),
            dateOfBirth = "900101".toCharArray(),
            dateOfExpiry = "250101".toCharArray()
        )
        val result = invoke<ByteArray>("derivePacePassword", mrzData)
        assertEquals(16, result.size)

        val expectedDigest = java.security.MessageDigest.getInstance("SHA-1").run {
            update(mrzData.mrzInformation.toByteArray(Charsets.UTF_8))
            digest()
        }
        assertTrue(expectedDigest.copyOfRange(0, 16).contentEquals(result))
    }

    @Test
    fun decryptNonce_decryptsAesCbc() {
        val key = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(16) { (16 - it).toByte() }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        val encrypted = cipher.doFinal(nonce)

        val decrypted = invoke<ByteArray>("decryptNonce", encrypted, key)

        assertTrue(nonce.contentEquals(decrypted))
    }

    @Test
    fun parameterIdToCurveName_knownIds() {
        assertEquals("secp256r1", invoke("parameterIdToCurveName", 0x01))
        assertEquals("secp224r1", invoke("parameterIdToCurveName", 0x03))
        assertEquals("secp384r1", invoke("parameterIdToCurveName", 0x09))
        assertEquals("secp521r1", invoke("parameterIdToCurveName", 0x0B))
    }

    @Test
    fun parameterIdToCurveName_unknownId_throws() {
        try {
            invoke<Any>("parameterIdToCurveName", 0xFF)
            org.junit.Assert.fail("Expected AuthenticationException")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            assertTrue(e.cause is com.example.epassport.domain.exception.AuthenticationException)
        }
    }

    @Test
    fun mapNonceToScalar_mapsWithinCurveOrder() {
        val n = BigInteger.valueOf(17)
        val scalar = invoke<BigInteger>("mapNonceToScalar", byteArrayOf(0x15), n)
        assertEquals(BigInteger.valueOf(4), scalar) // 21 mod 17

        val scalarMod = invoke<BigInteger>("mapNonceToScalar", byteArrayOf(0x20), n)
        assertEquals(BigInteger.valueOf(15), scalarMod) // 32 mod 17
    }

    @Test
    fun kdf_producesExpectedLength() {
        val sharedSecret = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val nonce = byteArrayOf(0x05, 0x06, 0x07, 0x08)
        val result = invoke<ByteArray>("kdf", sharedSecret, nonce, 128, "SHA-1")
        assertEquals(32, result.size)
    }

    @Test
    fun wrapAndExtractDynamicAuthData_roundTrip() {
        val data = byteArrayOf(0x11, 0x22, 0x33)
        val wrapped = invoke<ByteArray>("wrapDynamicAuthData", 0x80, data)
        val extracted = invoke<ByteArray>("extractDynamicAuthenticationData", wrapped + byteArrayOf(0x90.toByte(), 0x00.toByte()))
        assertNotNull(extracted)
        assertTrue(data.contentEquals(extracted))
    }

    private fun encodeLength(length: Int): ByteArray {
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), (length and 0xFF).toByte())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invoke(name: String, vararg args: Any?): T {
        val argClasses = args.map {
            when (it) {
                is Int -> Int::class.javaPrimitiveType
                is ByteArray -> ByteArray::class.java
                is BigInteger -> BigInteger::class.java
                else -> it?.javaClass
            }
        }.toTypedArray()
        val method = PaceAuthenticator::class.java.getDeclaredMethod(name, *argClasses)
        method.isAccessible = true
        return method.invoke(authenticator, *args) as T
    }
}
