package com.example.epassport.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.epassport.domain.exception.EPassportException
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.OnFailureListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayIntegrityCheckerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(IntegrityManagerFactory::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun requestToken_success_returnsToken() = runBlocking {
        val integrityManager = mockk<IntegrityManager>()
        val mockResponse = mockk<IntegrityTokenResponse>()
        val mockTask = mockk<Task<IntegrityTokenResponse>>()

        every { IntegrityManagerFactory.create(any()) } returns integrityManager
        every { mockResponse.token() } returns "mock_token"
        
        // Mock success listener registration to invoke asynchronously
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = arg<OnSuccessListener<IntegrityTokenResponse>>(0)
            Thread {
                listener.onSuccess(mockResponse)
            }.start()
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        every { integrityManager.requestIntegrityToken(any()) } returns mockTask

        val token = PlayIntegrityChecker.requestToken(context, 123456L, byteArrayOf(0x01, 0x02))

        assertEquals("mock_token", token)
    }

    @Test
    fun requestToken_failure_throwsEPassportException() = runBlocking {
        val integrityManager = mockk<IntegrityManager>()
        val mockTask = mockk<Task<IntegrityTokenResponse>>()
        val exception = Exception("Play Services Error")

        every { IntegrityManagerFactory.create(any()) } returns integrityManager
        
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        // Mock failure listener registration to invoke asynchronously
        every { mockTask.addOnFailureListener(any()) } answers {
            val listener = arg<OnFailureListener>(0)
            Thread {
                listener.onFailure(exception)
            }.start()
            mockTask
        }

        every { integrityManager.requestIntegrityToken(any()) } returns mockTask

        try {
            PlayIntegrityChecker.requestToken(context, 123456L, byteArrayOf(0x01, 0x02))
            fail("Expected EPassportException to be thrown")
        } catch (e: EPassportException) {
            assertEquals("Google Play Integrity API request failed", e.message)
            // Kotlin coroutines の resumeWithException による二重ラッピングに対応するため、
            // cause が EPassportException の場合はさらにその cause を取得する
            val rootCause = if (e.cause is EPassportException) e.cause?.cause else e.cause
            assertEquals(exception, rootCause)
        }
    }
}
