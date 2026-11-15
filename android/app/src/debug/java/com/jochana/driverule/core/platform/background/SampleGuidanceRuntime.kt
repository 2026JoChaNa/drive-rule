package com.jochana.driverule.core.platform.background

import android.content.Context
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import com.jochana.driverule.domain.guidance.GuidanceSpeechState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SampleStatus { Idle, Starting, Running, Stopped, Expired, Failed }
internal data class SampleGuidanceState(val status: SampleStatus = SampleStatus.Idle, val postedCount: Int = 0,
    val receivedCount: Int = 0, val backgroundEventCount: Int = 0, val lastEventTitle: String? = null,
    val banner: GuidanceEvent? = null, val speechAllowed: Boolean = false,
    val speechCompletedCount: Int = 0, val speechState: GuidanceSpeechState = GuidanceSpeechState())

/** Process-local observation, never a history of what a driver saw or heard. Main-thread only. */
internal object SampleGuidanceRuntime {
    private val mutable = MutableStateFlow(SampleGuidanceState())
    val state = mutable.asStateFlow()
    var autoStartAttempted = false
    var appVisible = false
        set(value) {
            if (field == value) return
            field = value
            hostListener?.invoke()
        }
    private var hostListener: (() -> Unit)? = null
    fun attach(listener: () -> Unit) { hostListener = listener }
    fun detach(listener: () -> Unit) { if (hostListener === listener) hostListener = null }
    fun speechAllowed(allowed: Boolean) {
        if (mutable.value.speechAllowed == allowed) return
        mutable.value = mutable.value.copy(speechAllowed = allowed)
        hostListener?.invoke()
    }
    fun status(status: SampleStatus) {
        val ending = status in setOf(SampleStatus.Stopped, SampleStatus.Expired, SampleStatus.Failed)
        mutable.value = mutable.value.copy(status = status,
            banner = if (ending) null else mutable.value.banner,
            speechState = if (ending && mutable.value.speechState.inProgress)
                mutable.value.speechState.copy(inProgress = false, result = GuidanceSpeechResult.Interrupted)
            else mutable.value.speechState)
    }
    fun started() {
        mutable.value = SampleGuidanceState(SampleStatus.Running, speechAllowed = mutable.value.speechAllowed)
    }
    fun received(title: String) {
        mutable.value = mutable.value.copy(receivedCount = mutable.value.receivedCount + 1,
            backgroundEventCount = mutable.value.backgroundEventCount + if (appVisible) 0 else 1,
            lastEventTitle = title)
    }
    fun posted() { mutable.value = mutable.value.copy(postedCount = mutable.value.postedCount + 1) }
    fun banner(event: GuidanceEvent?) { mutable.value = mutable.value.copy(banner = event) }
    fun speechState(state: GuidanceSpeechState) { mutable.value = mutable.value.copy(speechState = state) }
    fun speechCompleted() {
        mutable.value = mutable.value.copy(speechCompletedCount = mutable.value.speechCompletedCount + 1)
    }
    fun preferences(context: Context) = context.getSharedPreferences("background_sample", Context.MODE_PRIVATE)
    fun stop(context: Context) {
        autoStartAttempted = true
        preferences(context).edit().putBoolean("stopped_by_user", true).apply()
        status(SampleStatus.Stopped)
        hostListener?.invoke() // stopService is asynchronous; silence/dismiss owned output now.
        context.stopService(android.content.Intent(context, SampleGuidanceService::class.java))
    }
}
