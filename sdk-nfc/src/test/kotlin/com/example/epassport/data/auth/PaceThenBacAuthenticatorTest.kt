package com.example.epassport.data.auth

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.BacKey
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PaceThenBacAuthenticatorTest {

    private lateinit var paceAuth: PaceAuthenticator
    private lateinit var bacAuth: BacAuthenticator
    private lateinit var transceiver: NfcTransceiver
    private lateinit var paceThenBacAuth: PaceThenBacAuthenticator

    @Before
    fun setUp() {
        paceAuth = mockk()
        bacAuth = mockk()
        transceiver = mockk(relaxed = true)
        paceThenBacAuth = PaceThenBacAuthenticator(paceAuth, bacAuth)
    }

    @Test
    fun authenticate_withBacKey_callsBacAuthDirectly() = runBlocking {
        val bacKey = mockk<BacKey>()
        val expectedTransceiver = mockk<NfcTransceiver>()

        coEvery { bacAuth.authenticate(transceiver, bacKey) } returns expectedTransceiver

        val result = paceThenBacAuth.authenticate(transceiver, bacKey)

        assertEquals(expectedTransceiver, result)
        coVerify { bacAuth.authenticate(transceiver, bacKey) }
        coVerify(exactly = 0) { paceAuth.authenticate(any(), any<MrzData>()) }
    }

    @Test
    fun authenticate_withMrzData_paceSuccess_returnsPaceTransceiver() = runBlocking {
        val mrzData = mockk<MrzData>()
        val expectedTransceiver = mockk<NfcTransceiver>()

        coEvery { paceAuth.authenticate(transceiver, mrzData) } returns expectedTransceiver

        val result = paceThenBacAuth.authenticate(transceiver, mrzData)

        assertEquals(expectedTransceiver, result)
        coVerify { paceAuth.authenticate(transceiver, mrzData) }
        coVerify(exactly = 0) { bacAuth.authenticate(any(), any<BacKey>()) }
    }

    @Test
    fun authenticate_withMrzData_paceFailure_fallsBackToBac() = runBlocking {
        val mrzData = mockk<MrzData>(relaxed = true)
        val expectedTransceiver = mockk<NfcTransceiver>()
        val mockBacKey = mockk<BacKey>(relaxed = true)

        coEvery { paceAuth.authenticate(transceiver, mrzData) } throws AuthenticationException("PACE failure")
        coEvery { mrzData.deriveBacKeys() } returns mockBacKey
        coEvery { bacAuth.authenticate(transceiver, mockBacKey) } returns expectedTransceiver

        val result = paceThenBacAuth.authenticate(transceiver, mrzData)

        assertEquals(expectedTransceiver, result)
        coVerify { paceAuth.authenticate(transceiver, mrzData) }
        coVerify { transceiver.selectApp() }
        coVerify { bacAuth.authenticate(transceiver, mockBacKey) }
        coVerify { mockBacKey.clear() }
    }
}
