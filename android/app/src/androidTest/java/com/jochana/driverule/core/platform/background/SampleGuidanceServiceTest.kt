package com.jochana.driverule.core.platform.background

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jochana.driverule.MainActivity
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in with backgroundSmoke=true. Event receipt is not evidence of heads-up display. */
@RunWith(AndroidJUnit4::class)
class SampleGuidanceServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun samplesContinueBehindAnotherAppAndUserStopSurvivesReentry() {
        assumeTrue("Explicit opt-in required for the foreground-service device check",
            InstrumentationRegistry.getArguments().getString("backgroundSmoke") == "true")

        val context = instrumentation.targetContext
        val preferences = SampleGuidanceRuntime.preferences(context)
        val originalPreferences = listOf("stopped_by_user", "notification_permission_requested")
            .associateWith { key -> if (preferences.contains(key)) preferences.getBoolean(key, false) else null }
        val originalAutoStartAttempted = onMain { SampleGuidanceRuntime.autoStartAttempted }
        val notifications = context.getSystemService(NotificationManager::class.java)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            onMain { SampleGuidanceRuntime.stop(context) }
            await("Previous sample service did not stop") {
                SampleGuidanceRuntime.state.value.status == SampleStatus.Stopped &&
                    notifications.activeNotifications.none { it.id in SAMPLE_NOTIFICATION_IDS }
            }
            // Avoid a permission dialog during this test; never grant/revoke a permission.
            assertTrue(preferences.edit().putBoolean("stopped_by_user", false)
                .putBoolean("notification_permission_requested", true).commit())
            onMain { SampleGuidanceRuntime.autoStartAttempted = false }

            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Visible app did not start its sample session") {
                SampleGuidanceRuntime.state.value.status == SampleStatus.Running
            }
            scenario.onActivity { activity ->
                // A test-selected installed Activity allows OEM Settings failures to be isolated.
                // Opening this surface never starts a route or changes settings/permissions.
                val target = InstrumentationRegistry.getArguments().getString("backgroundActivity")
                val intent = if (target == null) Intent(Settings.ACTION_SETTINGS) else
                    Intent().setComponent(requireNotNull(ComponentName.unflattenFromString(target)))
                activity.startActivity(intent)
            }
            await("The other Activity did not hide the app; background receipt cannot be claimed") {
                onMain { !SampleGuidanceRuntime.appVisible }
            }
            // The fixture first emits after 10 s, then every 30 s. This waits for real callbacks.
            await("Two sample callbacks did not arrive while the app was hidden", timeoutMs = 50_000) {
                SampleGuidanceRuntime.state.value.backgroundEventCount >= 2
            }
            val received = SampleGuidanceRuntime.state.value
            assertEquals(SampleStatus.Running, received.status)
            assertTrue(received.receivedCount >= 2)
            assertTrue(received.backgroundEventCount >= 2)
            assertFalse(received.lastEventTitle.isNullOrBlank())
            // Posting depends on existing permissions; neither this count nor receipt proves HUN UI.
            assertTrue(received.postedCount <= received.receivedCount)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("backgroundEvidence", "received=${received.receivedCount}," +
                    "background=${received.backgroundEventCount},posted=${received.postedCount}," +
                    "notificationPermission=${context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)}")
            })

            onMain { SampleGuidanceRuntime.stop(context) }
            await("User stop left the sample session or its notifications active") {
                SampleGuidanceRuntime.state.value.status == SampleStatus.Stopped &&
                    notifications.activeNotifications.none { it.id in SAMPLE_NOTIFICATION_IDS }
            }
            val stopped = SampleGuidanceRuntime.state.value
            SystemClock.sleep(2_000)
            assertEquals("Callbacks changed after stop", stopped, SampleGuidanceRuntime.state.value)
            assertTrue(preferences.getBoolean("stopped_by_user", false))

            scenario.close()
            scenario = null
            // Exercise the persisted stop gate, not merely the process-local one-shot flag.
            onMain { SampleGuidanceRuntime.autoStartAttempted = false }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Reopened app was not visible") { onMain { SampleGuidanceRuntime.appVisible } }
            SystemClock.sleep(2_000)
            assertEquals("Reentry restarted a user-stopped session", stopped, SampleGuidanceRuntime.state.value)
            assertTrue(preferences.getBoolean("stopped_by_user", false))
            assertTrue(notifications.activeNotifications.none { it.id in SAMPLE_NOTIFICATION_IDS })
        } finally {
            try {
                onMain { SampleGuidanceRuntime.stop(context) }
                scenario?.close()
                await("Cleanup left sample notifications active") {
                    notifications.activeNotifications.none { it.id in SAMPLE_NOTIFICATION_IDS }
                }
            } finally {
                restorePreferences(preferences, originalPreferences)
                onMain { SampleGuidanceRuntime.autoStartAttempted = originalAutoStartAttempted }
            }
        }
    }

    private fun restorePreferences(preferences: SharedPreferences, original: Map<String, Boolean?>) {
        val editor = preferences.edit()
        original.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putBoolean(key, value)
        }
        assertTrue("Could not restore sample preferences", editor.commit())
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun await(message: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("$message; runtime=${SampleGuidanceRuntime.state.value}", condition())
    }

    private companion object {
        val SAMPLE_NOTIFICATION_IDS = setOf(SampleGuidanceNotifications.SESSION_ID, SampleGuidanceNotifications.CARD_ID)
    }
}
