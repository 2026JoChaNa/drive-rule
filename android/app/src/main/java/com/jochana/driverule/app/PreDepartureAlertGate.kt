package com.jochana.driverule.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The pre-departure alert is shown at most once per app process start (user decision
 * 2026-11-14: app start stands in for ignition until a vehicle signal is verified).
 * Returning from another app or recreating the Activity does not show it again.
 */
class PreDepartureAlertGate {
    private val claimed = AtomicBoolean(false)

    /** Returns `true` exactly once for the lifetime of this gate. */
    fun claim(): Boolean = claimed.compareAndSet(false, true)
}
