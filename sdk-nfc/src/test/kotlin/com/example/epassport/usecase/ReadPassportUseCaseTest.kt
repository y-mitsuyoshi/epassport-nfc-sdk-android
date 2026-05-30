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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        val mrzData = MrzData("L898902C<", "690806", "940623")
        val secureTransceiver = mockk<NfcTransceiver>()
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()

        coEvery { authenticator.authenticate(transceiver, any()) } returns secureTransceiver
        coEvery { reader.readDg1(secureTransceiver) } returns mockDg1
        coEvery { reader.readDg2(secureTransceiver) } returns mockDg2

        val progresses = mutableListOf<ReadProgress>()

        val result = useCase.execute(transceiver, mrzData) { prog ->
            progresses.add(prog)
        }

        assertEquals(mockDg1, result.dg1)
        assertEquals(mockDg2, result.dg2)

        coVerify { transceiver.selectApp() }
        coVerify { authenticator.authenticate(transceiver, any()) }
        coVerify { reader.readDg1(secureTransceiver) }
        coVerify { reader.readDg2(secureTransceiver) }

        assertEquals(5, progresses.size)
        assertEquals(ReadProgress.CONNECTING, progresses[0])
        assertEquals(ReadProgress.AUTHENTICATING, progresses[1])
        assertEquals(ReadProgress.READING_DG1, progresses[2])
        assertEquals(ReadProgress.READING_DG2, progresses[3])
        assertEquals(ReadProgress.SUCCESS, progresses[4])
    } }

    @Test(expected = AuthenticationException::class)
    fun execute_authFailure_throwsException() { runBlocking {
        val mrzData = MrzData("L898902C<", "690806", "940623")
        
        coEvery { authenticator.authenticate(transceiver, any()) } throws AuthenticationException("Fail")

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
        val mrzData = MrzData("L898902C<", "690806", "940623")
        coEvery { authenticator.authenticate(transceiver, any()) } throws AuthenticationException("Fail")
        useCase.execute(transceiver, mrzData)
    } }

    @Test
    fun execute_genericException_isWrappedInEPassportException() { runBlocking {
        val mrzData = MrzData("L898902C<", "690806", "940623")
        val secureTransceiver = mockk<NfcTransceiver>()
        coEvery { authenticator.authenticate(transceiver, any()) } returns secureTransceiver
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
}
