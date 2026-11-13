package com.jochana.driverule.app

import androidx.compose.runtime.Composable

/** Activity starts a host; collecting UI state never starts event processing. */
interface BackgroundGuidanceHost {
    fun onVisible()
    fun onHidden()
    @Composable fun Controls()
}
