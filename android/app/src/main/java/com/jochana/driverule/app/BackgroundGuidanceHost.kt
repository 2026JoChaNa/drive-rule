package com.jochana.driverule.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Activity starts a host; collecting UI state never starts event processing. */
interface BackgroundGuidanceHost {
    fun onVisible()
    fun onHidden()
    /** Known setup readiness, retained across Activity disposal; never starts/replays speech. */
    fun onSpeechAllowed(allowed: Boolean) = Unit
    /** Completion means the request finished, including denial; call back on the main thread. */
    fun requestPermissions(onComplete: () -> Unit) { onComplete() }
    @Composable fun Controls()
    @Composable fun Banner(modifier: Modifier = Modifier) = Unit
}
