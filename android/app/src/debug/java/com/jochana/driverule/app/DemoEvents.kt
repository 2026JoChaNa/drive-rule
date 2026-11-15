package com.jochana.driverule.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.jochana.driverule.BuildConfig
import com.jochana.driverule.core.platform.audio.PleosGuidanceSpeechOutput
import com.jochana.driverule.core.platform.background.SampleGuidanceRuntime
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.feature.card.CARD_AUTO_CLOSE_MILLIS
import com.jochana.driverule.feature.card.CardDetailPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug demo build only (`-PdriveRuleDemoMode=true`): stand-ins for vehicle events, used to record
 * the demo video. `scripts/demo-event.ps1` starts MainActivity from the adb shell with
 * [EXTRA_EVENT]; the matching Figma A/B/H/I sample card opens and its sample message is spoken.
 *
 * The app never launches itself from the background here: the shell brings it forward. Cards and
 * speech are the existing design-copy fixtures, not reviewed law, and nothing is recorded as heard.
 */
internal object DemoEvents {
    const val EXTRA_EVENT = "demo_event"
    private const val TAG = "DriveRuleDemo"
    private const val SPEECH_READY_TIMEOUT_MS = 5_000L

    /** Event name → [AppEnvironment.cardDetails] id. Names match docs/decisions/009 event tags. */
    private val cardIds = mapOf(
        "school" to "sample-crosswalk",   // H · SCHOOL_ZONE_ENTRY
        "parking" to "sample-parking",    // B · PARKING_DESTINATION
        "turn" to "sample-turn",          // I · RIGHT_TURN_CROSSWALK
        "highway" to "sample-camera",     // A · PARKING_HIGHWAY
    )

    // Conflated: a request sent before the home composition collects is kept, not dropped.
    private val requests = Channel<String>(Channel.CONFLATED)
    val cardOpenRequests: Flow<String> = requests.receiveAsFlow()

    private val scope = MainScope()
    private var speech: PleosGuidanceSpeechOutput? = null
    private var speakJob: Job? = null

    fun handle(context: Context, intent: Intent?) {
        if (!BuildConfig.DEMO_MODE) return
        val name = intent?.getStringExtra(EXTRA_EVENT) ?: return
        intent.removeExtra(EXTRA_EVENT) // Recreation must not replay the event.
        val card = cardIds[name]?.let { AppEnvironment.cardDetails()[it] }
        if (card == null) {
            Log.w(TAG, "Unknown demo event '$name'. Use one of ${cardIds.keys}")
            return
        }
        Log.i(TAG, "source=demo event=$name card=${card.id}")
        requests.trySend(card.id)
        speak(context.applicationContext, card)
    }

    private fun speak(context: Context, card: CardDetailPreview) {
        val output = speech ?: PleosGuidanceSpeechOutput(context).also { speech = it }
        speakJob?.cancel()
        output.stop()
        val text = card.message.replace('\n', ' ')
        speakJob = scope.launch {
            // Name entry owns the microphone and audio: wait briefly for setup, never queue past it.
            withTimeoutOrNull(SPEECH_READY_TIMEOUT_MS) {
                SampleGuidanceRuntime.state.first { it.speechAllowed }
            } ?: return@launch
            val now = SystemClock.elapsedRealtime()
            val result = output.speak(GuidanceEvent("demo-${card.id}-$now", text, text,
                now + CARD_AUTO_CLOSE_MILLIS, speechText = text))
            Log.i(TAG, "speech card=${card.id} result=$result")
        }
    }
}
