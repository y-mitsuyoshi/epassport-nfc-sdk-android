package com.example.epassport.usecase

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.port.DataGroupReader
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Security

class ReadPassportUseCaseTest {

    private lateinit var transceiver: NfcTransceiver
    private lateinit var authenticator: PassportAuthenticator
    private lateinit var reader: DataGroupReader
    private lateinit var useCase: ReadPassportUseCase

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Before
    fun setUp() {
        transceiver = mockk(relaxed = true)
        authenticator = mockk()
        reader = mockk()
        useCase = ReadPassportUseCase(authenticator, reader)
    }

    @Test
    fun execute_success_returnsPassportData() { runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val secureTransceiver = mockk<NfcTransceiver>()
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()

        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } returns secureTransceiver
        coEvery { reader.readDg1(secureTransceiver) } returns mockDg1
        coEvery { reader.readDg2(secureTransceiver) } returns mockDg2

        val progresses = mutableListOf<ReadProgress>()

        val result = useCase.execute(transceiver, mrzData) { prog ->
            progresses.add(prog)
        }

        assertEquals(mockDg1, result.dg1)
        assertEquals(mockDg2, result.dg2)

        coVerify { transceiver.selectApp() }
        coVerify { authenticator.authenticate(transceiver, any<MrzData>()) }
        coVerify { reader.readDg1(secureTransceiver) }
        coVerify { reader.readDg2(secureTransceiver) }

        assertEquals(6, progresses.size)
        assertEquals(ReadProgress.CONNECTING, progresses[0])
        assertEquals(ReadProgress.AUTHENTICATING, progresses[1])
        assertEquals(ReadProgress.READING_DG1, progresses[2])
        assertEquals(ReadProgress.READING_DG2, progresses[3])
        assertEquals(ReadProgress.PERFORMING_ACTIVE_AUTH, progresses[4])
        assertEquals(ReadProgress.SUCCESS, progresses[5])
    } }

    @Test(expected = AuthenticationException::class)
    fun execute_authFailure_throwsException() { runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        
        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } throws AuthenticationException("Fail")

        val progresses = mutableListOf<ReadProgress>()
        
        try {
            useCase.execute(transceiver, mrzData) { prog ->
                progresses.add(prog)
            }
        } finally {
            assertEquals(ReadProgress.ERROR, progresses.last())
        }
    } }

    @Test(expected = AuthenticationException::class)
    fun execute_authThrowsEPassportException_rethrowsOriginalException() { runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } throws AuthenticationException("Fail")
        useCase.execute(transceiver, mrzData)
    } }

    @Test
    fun execute_genericException_isWrappedInEPassportException() { runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val secureTransceiver = mockk<NfcTransceiver>()
        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } returns secureTransceiver
        coEvery { reader.readDg1(secureTransceiver) } throws RuntimeException("Unexpected runtime error")
        
        try {
            useCase.execute(transceiver, mrzData)
            org.junit.Assert.fail("Exception should have been thrown")
        } catch (e: Exception) {
            org.junit.Assert.assertTrue(e is com.example.epassport.domain.exception.EPassportException)
            assertEquals("Unexpected error during passport reading", e.message)
            org.junit.Assert.assertTrue(e.cause is RuntimeException || e.cause?.cause is RuntimeException)
        }
    } }

    @Test
    fun execute_withCachedDg1_skipsReadingDg1() = runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val secureTransceiver = mockk<NfcTransceiver>()
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()

        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } returns secureTransceiver
        coEvery { reader.readDg2(secureTransceiver) } returns mockDg2

        val cachedData = CachedPassportData(dg1 = mockDg1)

        val result = useCase.execute(
            transceiver = transceiver,
            mrzData = mrzData,
            cachedData = cachedData
        )

        assertEquals(mockDg1, result.dg1)
        assertEquals(mockDg2, result.dg2)

        coVerify(exactly = 0) { reader.readDg1(any()) }
        coVerify(exactly = 1) { reader.readDg2(secureTransceiver) }
    }

    @Test
    fun execute_withCachedDg1AndDg2_skipsReadingBoth() = runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val secureTransceiver = mockk<NfcTransceiver>()
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()

        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } returns secureTransceiver

        val cachedData = CachedPassportData(dg1 = mockDg1, dg2 = mockDg2)

        val result = useCase.execute(
            transceiver = transceiver,
            mrzData = mrzData,
            cachedData = cachedData
        )

        assertEquals(mockDg1, result.dg1)
        assertEquals(mockDg2, result.dg2)

        coVerify(exactly = 0) { reader.readDg1(any()) }
        coVerify(exactly = 0) { reader.readDg2(any()) }
    }

    @Test
    fun execute_callsOnCacheUpdateAfterEachRead() = runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val secureTransceiver = mockk<NfcTransceiver>()
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()

        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } returns secureTransceiver
        coEvery { reader.readDg1(secureTransceiver) } returns mockDg1
        coEvery { reader.readDg2(secureTransceiver) } returns mockDg2

        val cacheUpdates = mutableListOf<CachedPassportData>()

        useCase.execute(
            transceiver = transceiver,
            mrzData = mrzData,
            onCacheUpdate = { cacheUpdates.add(it) }
        )

        assertTrue(cacheUpdates.size >= 2)
        assertEquals(mockDg1, cacheUpdates[0].dg1)
        assertEquals(mockDg2, cacheUpdates[1].dg2)
    }

    @Test
    fun execute_withExpiredCache_ignoresCache() = runBlocking {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val secureTransceiver = mockk<NfcTransceiver>()
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()
        val staleDg1 = mockk<Dg1Data>()

        coEvery { authenticator.authenticate(transceiver, any<MrzData>()) } returns secureTransceiver
        coEvery { reader.readDg1(secureTransceiver) } returns mockDg1
        coEvery { reader.readDg2(secureTransceiver) } returns mockDg2

        val expiredCache = CachedPassportData(
            dg1 = staleDg1,
            timestamp = System.currentTimeMillis() - 600000 // 10 min ago (> 5 min TTL)
        )

        val result = useCase.execute(
            transceiver = transceiver,
            mrzData = mrzData,
            cachedData = expiredCache
        )

        // Should have re-read DG1 instead of using stale cache
        assertEquals(mockDg1, result.dg1)
        coVerify(exactly = 1) { reader.readDg1(secureTransceiver) }
    }
}
