package com.jochana.driverule.domain.vehicle

import com.jochana.driverule.core.model.VehicleUxState
import kotlinx.coroutines.flow.StateFlow

/** Owned by one main-thread host; stopping releases listeners and a later start is allowed. */
interface VehicleSignalSource {
    val state: StateFlow<VehicleSignalSnapshot>
    fun start()
    fun stop()
}

enum class SignalConnection { Stopped, Connecting, Connected, Unavailable, PermissionDenied, Failed }

enum class SignalStatus { Unknown, Available, Unavailable, PermissionDenied, Failed, Stale }

/** A retained stale value is diagnostic only. Consumers use [availableValue], never a fallback 0/false. */
data class VehicleSignal<T>(
    val status: SignalStatus = SignalStatus.Unknown,
    val value: T? = null,
    val observedAtElapsedMs: Long? = null,
    val sourceTimestampElapsedMs: Long? = null,
    /** Ordering metadata only; retained through invalidations until a new source connection. */
    val sourceTimestampWatermarkElapsedMs: Long? = sourceTimestampElapsedMs,
) {
    val availableValue: T? get() = value.takeIf { status == SignalStatus.Available }

    fun stale(): VehicleSignal<T> =
        if (status == SignalStatus.Available) copy(status = SignalStatus.Stale) else this
}

/** Selected gear, not the transmission's CURRENT_GEAR and not a UX permission. */
enum class SelectedGear {
    Neutral, Reverse, Park, Drive, First, Second, Third, Fourth, Fifth, Sixth, Seventh, Eighth, Ninth,
}

enum class IgnitionState { Lock, Off, Accessory, On, Start }

data class VehicleSignalSnapshot(
    val connection: SignalConnection = SignalConnection.Stopped,
    val ux: VehicleSignal<VehicleUxState> = VehicleSignal(),
    val gear: VehicleSignal<SelectedGear> = VehicleSignal(),
    /** Signed m/s: negative means reverse movement, independently of selected gear. */
    val speedMetersPerSecond: VehicleSignal<Float> = VehicleSignal(),
    val ignition: VehicleSignal<IgnitionState> = VehicleSignal(),
    val parkingBrake: VehicleSignal<Boolean> = VehicleSignal(),
) {
    val uxState: VehicleUxState
        get() = if (connection == SignalConnection.Connected) {
            ux.availableValue ?: VehicleUxState.Unknown
        } else {
            VehicleUxState.Unknown
        }

    fun disconnected(connection: SignalConnection): VehicleSignalSnapshot = copy(
        connection = connection,
        ux = ux.stale(),
        gear = gear.stale(),
        speedMetersPerSecond = speedMetersPerSecond.stale(),
        ignition = ignition.stale(),
        parkingBrake = parkingBrake.stale(),
    )

    /** Only continuous speed expires by age. On-change values stay valid while subscribed. */
    fun expireSpeed(nowElapsedMs: Long, maxAgeMs: Long): VehicleSignalSnapshot {
        require(maxAgeMs > 0)
        val speed = speedMetersPerSecond
        val receivedAt = speed.observedAtElapsedMs ?: return this
        val measuredAt = speed.sourceTimestampElapsedMs?.coerceAtMost(receivedAt) ?: receivedAt
        return if (speed.status == SignalStatus.Available &&
            (nowElapsedMs < measuredAt || nowElapsedMs - measuredAt >= maxAgeMs)
        ) {
            copy(speedMetersPerSecond = speed.stale())
        } else {
            this
        }
    }

    /** Permission revocation can happen without a property callback; it never changes actual UX. */
    internal fun withPermissions(speedGranted: Boolean, powertrainGranted: Boolean, nowElapsedMs: Long): VehicleSignalSnapshot =
        copy(
            speedMetersPerSecond = if (speedGranted) speedMetersPerSecond else speedMetersPerSecond.denied(nowElapsedMs),
            gear = if (powertrainGranted) gear else gear.denied(nowElapsedMs),
            ignition = if (powertrainGranted) ignition else ignition.denied(nowElapsedMs),
            parkingBrake = if (powertrainGranted) parkingBrake else parkingBrake.denied(nowElapsedMs),
        )
}

private fun <T> VehicleSignal<T>.denied(nowElapsedMs: Long): VehicleSignal<T> =
    if (status == SignalStatus.PermissionDenied) this
    else newerSignal(this, VehicleSignal(status = SignalStatus.PermissionDenied, observedAtElapsedMs = nowElapsedMs))

/** The current setup/detail UI is not distraction optimized, including baseline-only DO. */
internal fun normalizeUxRestriction(requiresDistractionOptimization: Boolean, activeFlags: Int): VehicleUxState =
    if (requiresDistractionOptimization || activeFlags != 0) VehicleUxState.Restricted
    else VehicleUxState.Unrestricted

/**
 * Errors invalidate the value, but must not erase the ordering watermark: an older queued value
 * cannot restore availability after an error or permission revocation. A new connection uses
 * a fresh snapshot, resetting this metadata along with all subscriptions.
 */
internal fun <T> newerSignal(previous: VehicleSignal<T>, incoming: VehicleSignal<T>): VehicleSignal<T> {
    val previousTime = maxTimestamp(previous.sourceTimestampWatermarkElapsedMs, previous.sourceTimestampElapsedMs)
    val incomingTime = incoming.sourceTimestampElapsedMs
    if (incoming.status == SignalStatus.Available && previousTime != null &&
        incomingTime != null && incomingTime < previousTime
    ) return previous
    return incoming.copy(sourceTimestampWatermarkElapsedMs = maxTimestamp(previousTime,
        maxTimestamp(incoming.sourceTimestampWatermarkElapsedMs, incomingTime)))
}

private fun maxTimestamp(first: Long?, second: Long?): Long? = when {
    first == null -> second
    second == null -> first
    else -> maxOf(first, second)
}
