package com.jochana.driverule.core.platform.speech

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pleos-only smoke check: enter microphone-ready state and immediately release it. */
@RunWith(AndroidJUnit4::class)
class PleosSpeechServiceTest {
    @Test
    fun onDeviceSpeechServiceBecomesReadyWithoutInternetPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires a Pleos image with STT_SERVICE installed", context.checkSelfPermission(
            "pleos.car.permission.STT_SERVICE") == PackageManager.PERMISSION_GRANTED)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val completed = CountDownLatch(1)
        val error = AtomicReference<NameSpeechError?>(null)
        val engine = PleosNameSpeechEngine(context)
        try {
            instrumentation.runOnMainSync {
                engine.start(object : NameSpeechEngine.Listener {
                    override fun onReady() { engine.close(); completed.countDown() }
                    override fun onPartial(text: String) = Unit
                    override fun onResult(text: String) = Unit
                    override fun onEnded() = Unit
                    override fun onError(errorType: NameSpeechError) {
                        error.set(errorType)
                        engine.close()
                        completed.countDown()
                    }
                })
            }
            assertTrue("Pleos STT did not become ready within 45 seconds", completed.await(45, TimeUnit.SECONDS))
            assertNull("Pleos STT returned ${error.get()}", error.get())
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }
}
