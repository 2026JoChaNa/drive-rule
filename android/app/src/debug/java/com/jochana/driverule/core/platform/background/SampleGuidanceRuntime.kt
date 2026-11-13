package com.jochana.driverule.core.platform.background

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SampleStatus { Idle, Starting, Running, Stopped, Expired, Failed }
internal data class SampleGuidanceState(val status: SampleStatus = SampleStatus.Idle, val postedCount: Int = 0,
    val receivedCount: Int = 0, val backgroundEventCount: Int = 0, val lastEventTitle: String? = null)

/** Process-local observation, never a history of what a driver saw or heard. Main-thread only. */
internal object SampleGuidanceRuntime {
    private val mutable = MutableStateFlow(SampleGuidanceState())
    val state = mutable.asStateFlow()
    var autoStartAttempted = false
    var appVisible = false
    fun status(status: SampleStatus) { mutable.value = mutable.value.copy(status = status) }
    fun started() { mutable.value = SampleGuidanceState(SampleStatus.Running) }
    fun received(title: String) {
        mutable.value = mutable.value.copy(receivedCount = mutable.value.receivedCount + 1,
            backgroundEventCount = mutable.value.backgroundEventCount + if (appVisible) 0 else 1,
            lastEventTitle = title)
    }
    fun posted() { mutable.value = mutable.value.copy(postedCount = mutable.value.postedCount + 1) }
    fun preferences(context: Context) = context.getSharedPreferences("background_sample", Context.MODE_PRIVATE)
    fun stop(context: Context) {
        autoStartAttempted = true
        preferences(context).edit().putBoolean("stopped_by_user", true).apply()
        status(SampleStatus.Stopped)
        context.stopService(android.content.Intent(context, SampleGuidanceService::class.java))
    }
}
