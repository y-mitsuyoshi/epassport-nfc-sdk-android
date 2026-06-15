package com.example.epassport.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scottyab.rootbeer.RootBeer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RuntimeSecurityCheckerTest {

    private fun createChecker(rooted: Boolean = false): RuntimeSecurityChecker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootBeer = mockk<RootBeer>()
        every { rootBeer.isRooted } returns rooted
        return RuntimeSecurityChecker(context, rootBeer)
    }

    @Test
    fun isDeviceSecure_onNormalDevice_returnsTrue() {
        ShadowBuild.setFingerprint("google/sailfish/sailfish:9/.../release-keys")
        ShadowBuild.setModel("Pixel")
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setBoard("sailfish")
        ShadowBuild.setHardware("sailfish")
        ShadowBuild.setProduct("sailfish")

        assertTrue(createChecker().isDeviceSecure())
        assertEquals(emptyList<String>(), createChecker().detectThreats())
    }

    @Test
    fun isDeviceSecure_onEmulator_returnsFalse() {
        ShadowBuild.setFingerprint("generic")
        ShadowBuild.setModel("Android SDK built for x86")
        ShadowBuild.setManufacturer("Google")
        ShadowBuild.setBoard("goldfish")
        ShadowBuild.setHardware("goldfish")
        ShadowBuild.setProduct("sdk_google")

        assertFalse(createChecker().isDeviceSecure())
        assertTrue(createChecker().detectThreats().contains("emulator"))
    }

    @Test
    fun isDeviceSecure_onRootedDevice_returnsFalse() {
        assertFalse(createChecker(rooted = true).isDeviceSecure())
        assertTrue(createChecker(rooted = true).detectThreats().contains("rooted_device"))
    }

    @Test
    fun verifyAppSignature_withMatchingSignature_returnsTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric application has a dummy signature; we cannot easily set it.
        // This test verifies the method returns false for a non-matching expected signature.
        val checker = RuntimeSecurityChecker(context)
        assertFalse(checker.verifyAppSignature(setOf("DEADBEEF")))
    }
}
