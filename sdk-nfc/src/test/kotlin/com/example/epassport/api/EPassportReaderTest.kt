package com.example.epassport.api

import android.content.Context
import android.nfc.Tag
import androidx.test.core.app.ApplicationProvider
import com.example.epassport.domain.model.MrzData
import io.mockk.every
import io.mockk.mockk
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
}
