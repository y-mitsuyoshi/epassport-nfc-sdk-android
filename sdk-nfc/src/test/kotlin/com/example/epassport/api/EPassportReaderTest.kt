package com.example.epassport.api

import android.content.Context
import android.nfc.Tag
import androidx.test.core.app.ApplicationProvider
import com.example.epassport.data.security.PlayIntegrityChecker
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.model.PassportData
import com.example.epassport.usecase.CachedPassportData
import com.example.epassport.usecase.ReadPassportUseCase
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EPassportReaderTest {

    @Test
    fun read_onEmulator_returnsUnsafeEnvironmentError() { runBlocking {
        // Simulate emulator environment
        ShadowBuild.setFingerprint("generic")
        ShadowBuild.setModel("Android SDK built for x86")
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setBoard("goldfish")
        ShadowBuild.setHardware("goldfish")
        ShadowBuild.setProduct("sdk_google")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val tag = mockk<Tag>()
        every { tag.id } returns byteArrayOf()
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())

        val result = EPassportReader.read(context, tag, mrzData)

        assertTrue(result is ReadResult.Error)
        val error = (result as ReadResult.Error).exception
        assertTrue(error.message?.contains("Unsafe execution environment") == true)

        mrzData.clear()
    } }

    @Test
    fun read_withPlayIntegrity_callsChecker() = runBlocking {
        // Setup secure environment
        ShadowBuild.setFingerprint("google/sailfish/sailfish:9/.../release-keys")
        ShadowBuild.setModel("Pixel")
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setBoard("sailfish")
        ShadowBuild.setHardware("sailfish")
        ShadowBuild.setProduct("sailfish")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val tag = mockk<Tag>()
        every { tag.id } returns byteArrayOf()

        // Mock IsoDep
        mockkStatic(android.nfc.tech.IsoDep::class)
        val isoDep = mockk<android.nfc.tech.IsoDep>(relaxed = true)
        every { android.nfc.tech.IsoDep.get(tag) } returns isoDep
        every { isoDep.isConnected } returns true

        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())

        // Mock PlayIntegrityChecker
        mockkObject(PlayIntegrityChecker)
        coEvery { PlayIntegrityChecker.requestToken(any(), 123456L, any()) } returns "test_integrity_token"

        // Mock RuntimeSecurityChecker to bypass RASP checks
        mockkConstructor(com.example.epassport.data.security.RuntimeSecurityChecker::class)
        every { anyConstructed<com.example.epassport.data.security.RuntimeSecurityChecker>().isDeviceSecure() } returns true

        // Mock ReadPassportUseCase
        mockkConstructor(ReadPassportUseCase::class)
        val mockDg1 = mockk<com.example.epassport.domain.model.Dg1Data>()
        val mockDg2 = mockk<com.example.epassport.domain.model.Dg2Data>()
        val realPassportData = PassportData(dg1 = mockDg1, dg2 = mockDg2)
        coEvery { anyConstructed<ReadPassportUseCase>().execute(any(), any(), any(), any(), any(), any(), any()) } returns realPassportData

        val result = EPassportReader.read(
            context = context,
            tag = tag,
            mrzData = mrzData,
            challenge = byteArrayOf(1, 2, 3),
            googleCloudProjectNumber = 123456L,
            allowDebug = true
        )

        if (result is ReadResult.Error) {
            println("result error: ${result.exception}")
            result.exception.printStackTrace()
        }
        assertTrue(result is ReadResult.Success)
        val successToken = (result as ReadResult.Success).data.playIntegrityToken
        org.junit.Assert.assertEquals("test_integrity_token", successToken)
        coVerify { PlayIntegrityChecker.requestToken(any(), 123456L, any()) }
        
        mrzData.clear()
        unmockkAll()
    }

    @Test
    fun read_resumeCaching_skipsSuccessDGs() = runBlocking {
        // Setup secure environment
        ShadowBuild.setFingerprint("google/sailfish/sailfish:9/.../release-keys")
        ShadowBuild.setModel("Pixel")
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setBoard("sailfish")
        ShadowBuild.setHardware("shadow")
        ShadowBuild.setProduct("shadow")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val tag = mockk<Tag>()
        every { tag.id } returns byteArrayOf()

        // Mock IsoDep
        mockkStatic(android.nfc.tech.IsoDep::class)
        val isoDep = mockk<android.nfc.tech.IsoDep>(relaxed = true)
        every { android.nfc.tech.IsoDep.get(tag) } returns isoDep
        every { isoDep.isConnected } returns true

        // Mock RuntimeSecurityChecker to bypass RASP checks
        mockkConstructor(com.example.epassport.data.security.RuntimeSecurityChecker::class)
        every { anyConstructed<com.example.epassport.data.security.RuntimeSecurityChecker>().isDeviceSecure() } returns true

        // Use same/copies of MRZ data for caching
        val mrzData1 = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        val mrzData2 = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())

        // Mock ReadPassportUseCase
        mockkConstructor(ReadPassportUseCase::class)
        val mockDg1 = mockk<com.example.epassport.domain.model.Dg1Data>()
        val mockDg2 = mockk<com.example.epassport.domain.model.Dg2Data>()

        coEvery { anyConstructed<ReadPassportUseCase>().execute(any(), any(), any(), any(), any(), any(), any()) } answers {
            val cachedData = args[4] as? CachedPassportData
            val onCacheUpdate = args[5] as (CachedPassportData) -> Unit

            if (cachedData == null) {
                // First run: successfully read DG1, then fail
                onCacheUpdate(CachedPassportData(dg1 = mockDg1))
                throw com.example.epassport.domain.exception.EPassportException("Simulated connection loss")
            } else {
                // Second run: use cached DG1, and finish successfully
                com.example.epassport.domain.model.PassportData(
                    dg1 = cachedData.dg1!!,
                    dg2 = mockDg2
                )
            }
        }

        // 1st attempt: should fail
        val result1 = EPassportReader.read(context, tag, mrzData1)
        if (result1 is ReadResult.Error) {
            println("result1 error: ${result1.exception}")
            result1.exception.printStackTrace()
        }
        assertTrue(result1 is ReadResult.Error)

        // 2nd attempt: should succeed and resume using cached data
        val result2 = EPassportReader.read(context, tag, mrzData2)
        if (result2 is ReadResult.Error) {
            println("result2 error: ${result2.exception}")
            result2.exception.printStackTrace()
        }
        assertTrue(result2 is ReadResult.Success)

        val successData = (result2 as ReadResult.Success).data
        assertTrue(successData.dg1 === mockDg1)
        assertTrue(successData.dg2 === mockDg2)

        mrzData1.clear()
        mrzData2.clear()
        unmockkAll()
    }
}
