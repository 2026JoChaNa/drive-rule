package com.jochana.driverule.core.platform.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.jochana.driverule.domain.vehicle.LocationConnection
import com.jochana.driverule.domain.vehicle.LocationObservation
import com.jochana.driverule.domain.vehicle.LocationSignalSnapshot
import com.jochana.driverule.domain.vehicle.LocationSignalSource
import com.jochana.driverule.domain.vehicle.LocationUnavailable
import com.jochana.driverule.domain.vehicle.VehicleLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FusedLocation 1.1.3 continuous updates. SDK callbacks may run on Binder threads; all state
 * and lifecycle transitions are serialized by the dispatcher. No last-known fix is promoted
 * to fresh. The source timestamp is used only for ordering within one source session.
 */
class PleosLocationSignalSource internal constructor(
    private val clientFactory: (LocationClient.Listener) -> LocationClient,
    private val permissionGranted: () -> Boolean,
    private val dispatcher: LocationDispatcher,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val firstFixTimeoutMs: Long = FIRST_FIX_TIMEOUT_MS,
) : LocationSignalSource {
    constructor(context: Context) : this(
        clientFactory = { SdkLocationClient(context.applicationContext, it) },
        permissionGranted = {
            context.applicationContext.checkSelfPermission(FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
        },
        dispatcher = MainLocationDispatcher(),
    )

    private val mutableState = MutableStateFlow(LocationSignalSnapshot())
    override val state: StateFlow<LocationSignalSnapshot> = mutableState.asStateFlow()
    private var started = false
    private var generation = 0L
    private var sourceRevision = 0L
    private var client: LocationClient? = null
    private var timeout: LocationCancellation? = null
    private var freshnessTick: LocationCancellation? = null
    private var watermark: Long? = null
    private var receiptWatermarkElapsedMs: Long? = null
    private var lastProgressElapsedMs: Long? = null
    private var baselineEstablished = false

    init {
        require(maxAgeMs > 0 && firstFixTimeoutMs > 0)
    }

    override fun start() = dispatcher.execute start@ {
        if (started) return@start
        started = true
        val token = ++generation
        sourceRevision++
        watermark = null
        receiptWatermarkElapsedMs = null
        lastProgressElapsedMs = null
        baselineEstablished = false
        mutableState.value = LocationSignalSnapshot(LocationConnection.Connecting, sourceRevision = sourceRevision)
        guarded(token) {
            if (!permissionGranted()) {
                fail(LocationConnection.PermissionDenied, LocationUnavailable.PermissionDenied)
                return@guarded
            }
            val instance = clientFactory(listener(token))
            client = instance
            if (!instance.isSupported()) {
                fail(LocationConnection.Unavailable, LocationUnavailable.SourceUnavailable)
                return@guarded
            }
            timeout = dispatcher.schedule(firstFixTimeoutMs) {
                if (accepts(token) && mutableState.value.connection == LocationConnection.Connecting) {
                    fail(LocationConnection.Unavailable, LocationUnavailable.SourceUnavailable)
                }
            }
            instance.startContinuousUpdates()
            if (accepts(token)) scheduleFreshness(token)
        }
    }

    override fun stop() = dispatcher.execute {
        if (!started && mutableState.value.connection == LocationConnection.Stopped) return@execute
        started = false
        generation++
        dispose()
        mutableState.value = unavailable(LocationConnection.Stopped, LocationUnavailable.SourceStopped)
    }

    private fun listener(token: Long) = object : LocationClient.Listener {
        override fun onLocation(location: VehicleLocation?) {
            // Capture on the receiving thread: a queued Binder callback must not acquire
            // a newer observation time merely because the main thread was busy.
            val receivedElapsedMs = dispatcher.elapsedRealtimeMs()
            dispatcher.execute {
                if (!accepts(token)) return@execute
                guarded(token) {
                    if (!permissionGranted()) {
                        fail(LocationConnection.PermissionDenied, LocationUnavailable.PermissionDenied)
                        return@guarded
                    }
                    if (location == null || !location.isValid()) {
                        // Privacy failures may provide null; continuous SDK 1.1.3 can also swallow
                        // null, so this branch never substitutes for the independent stale timer.
                        breakContinuity(LocationObservation.Unavailable(LocationUnavailable.InvalidData))
                        return@guarded
                    }
                    val previous = watermark
                    if (previous != null && location.rawSourceTimestampMillis <= previous) return@guarded
                    val now = dispatcher.elapsedRealtimeMs()
                    watermark = location.rawSourceTimestampMillis
                    val priorReceipt = receiptWatermarkElapsedMs
                    receiptWatermarkElapsedMs = maxOf(priorReceipt ?: receivedElapsedMs, receivedElapsedMs)
                    if (receivedElapsedMs < 0 || now < receivedElapsedMs ||
                        (priorReceipt != null && receivedElapsedMs < priorReceipt) ||
                        now - receivedElapsedMs >= maxAgeMs) {
                        // Consume the newer source token even when its delivery is too late or
                        // out of order, so replaying that same sample cannot make it fresh.
                        breakContinuity(lastProgressElapsedMs?.let { LocationObservation.Stale(it) } ?: LocationObservation.Unknown)
                        return@guarded
                    }
                    val last = lastProgressElapsedMs
                    if (last != null && (now < last || now - last >= maxAgeMs)) {
                        breakContinuity(LocationObservation.Stale(last))
                    }
                    val hadBaseline = baselineEstablished
                    baselineEstablished = true
                    lastProgressElapsedMs = receivedElapsedMs
                    timeout?.cancel()
                    timeout = null
                    mutableState.value = mutableState.value.copy(
                        connection = LocationConnection.Connected,
                        position = if (!hadBaseline) LocationObservation.Unknown
                            else LocationObservation.Fresh(location, receivedElapsedMs),
                    )
                }
            }
        }

        override fun onError(error: Exception) = dispatcher.execute {
            if (!accepts(token)) return@execute
            if (error is SecurityException) fail(LocationConnection.PermissionDenied, LocationUnavailable.PermissionDenied)
            else fail(LocationConnection.Unavailable, LocationUnavailable.SourceUnavailable)
        }
    }

    private fun scheduleFreshness(token: Long) {
        freshnessTick = dispatcher.schedule(FRESHNESS_CHECK_MS) {
            if (accepts(token)) guarded(token) {
                if (!permissionGranted()) {
                    fail(LocationConnection.PermissionDenied, LocationUnavailable.PermissionDenied)
                } else {
                    val last = lastProgressElapsedMs
                    val now = dispatcher.elapsedRealtimeMs()
                    if (last != null && (now < last || now - last >= maxAgeMs) &&
                        (mutableState.value.position is LocationObservation.Fresh ||
                            mutableState.value.position == LocationObservation.Unknown)) {
                        breakContinuity(LocationObservation.Stale(last))
                    }
                    if (accepts(token)) scheduleFreshness(token)
                }
            }
        }
    }

    private fun accepts(token: Long) = started && token == generation

    private fun breakContinuity(position: LocationObservation) {
        if (baselineEstablished) sourceRevision++
        baselineEstablished = false
        mutableState.value = mutableState.value.copy(position = position, sourceRevision = sourceRevision)
    }

    private fun fail(connection: LocationConnection, reason: LocationUnavailable) {
        generation++
        dispose()
        mutableState.value = unavailable(connection, reason)
        // Latched until explicit stop/start. Do not turn a permission/privacy failure into a loop.
    }

    private fun unavailable(connection: LocationConnection, reason: LocationUnavailable) =
        LocationSignalSnapshot(connection, LocationObservation.Unavailable(reason), sourceRevision)

    private fun dispose() {
        timeout?.cancel()
        timeout = null
        freshnessTick?.cancel()
        freshnessTick = null
        watermark = null
        receiptWatermarkElapsedMs = null
        lastProgressElapsedMs = null
        baselineEstablished = false
        val previous = client
        client = null
        try {
            previous?.release()
        } catch (_: RuntimeException) {
            // Raw SDK errors can contain location data; do not log or publish their messages.
        } catch (_: LinkageError) {
            // Optional vehicle service/library failure is represented by the source state.
        }
    }

    private fun guarded(token: Long, action: () -> Unit) {
        try {
            action()
        } catch (_: SecurityException) {
            if (accepts(token)) fail(LocationConnection.PermissionDenied, LocationUnavailable.PermissionDenied)
        } catch (_: RuntimeException) {
            if (accepts(token)) fail(LocationConnection.Failed, LocationUnavailable.Failed)
        } catch (_: LinkageError) {
            if (accepts(token)) fail(LocationConnection.Unavailable, LocationUnavailable.SourceUnavailable)
        }
    }

    companion object {
        const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        // Provisional receipt-age limits, not documented SDK update periods or GPS accuracy.
        private const val DEFAULT_MAX_AGE_MS = 5_000L
        private const val FIRST_FIX_TIMEOUT_MS = 15_000L
        private const val FRESHNESS_CHECK_MS = 1_000L
    }
}

private fun VehicleLocation.isValid(): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
    longitude.isFinite() && longitude in -180.0..180.0 && rawConfidence.isFinite() && rawSourceTimestampMillis >= 0

internal fun interface LocationCancellation { fun cancel() }

internal interface LocationDispatcher {
    fun execute(action: () -> Unit)
    fun schedule(delayMs: Long, action: () -> Unit): LocationCancellation
    fun elapsedRealtimeMs(): Long
}

private class MainLocationDispatcher : LocationDispatcher {
    private val handler = Handler(Looper.getMainLooper())
    override fun execute(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
    override fun schedule(delayMs: Long, action: () -> Unit): LocationCancellation {
        val task = Runnable(action)
        handler.postDelayed(task, delayMs)
        return LocationCancellation { handler.removeCallbacks(task) }
    }
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}

internal interface LocationClient {
    fun isSupported(): Boolean
    fun startContinuousUpdates()
    fun release()
    interface Listener {
        fun onLocation(location: VehicleLocation?)
        fun onError(error: Exception)
    }
}

private class SdkLocationClient(context: Context, listener: LocationClient.Listener) : LocationClient {
    private val bindingContext = LocationBindingContext(context)
    private val sdk = FusedLocation(bindingContext)
    private var registered = false
    private var released = false
    private val sdkListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = listener.onLocation(location?.let {
            VehicleLocation(it.latitude, it.longitude, it.confidence, it.timestampMillis)
        })
        override fun onError(exception: Exception) = listener.onError(exception)
    }

    override fun isSupported(): Boolean = sdk.isSupported()

    @SuppressLint("MissingPermission") // The source checks and handles custom permission revocation.
    override fun startContinuousUpdates() {
        check(!released)
        sdk.initialize()
        registered = true
        sdk.registerFusedLocationCallback(sdkListener)
    }

    @SuppressLint("MissingPermission")
    override fun release() {
        if (released) return
        released = true
        // SDK 1.1.3 does not cancel its private bind retry coroutine on release. Prevent any
        // new bind or late connection callback before calling SDK cleanup; always unbind leases.
        bindingContext.close()
        try {
            if (registered) sdk.unregisterFusedLocationCallback(sdkListener)
        } finally {
            registered = false
            sdk.release()
        }
    }
}

/** Small lifecycle gate also testable without Android. Calls and closing are serialized. */
internal class LocationBindingLifetime {
    private var open = true
    @Synchronized fun <T> whileOpen(action: () -> T): T? = if (open) action() else null
    @Synchronized fun close(action: () -> Unit) {
        if (!open) return
        open = false
        action()
    }
}

/** The SDK uses this three-argument bind overload. Never forwards a callback after close. */
private class LocationBindingContext(context: Context) : ContextWrapper(context) {
    private val lifetime = LocationBindingLifetime()
    private val connections = mutableMapOf<ServiceConnection, ServiceConnection>()

    override fun getApplicationContext(): Context = this

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean =
        lifetime.whileOpen {
            val proxy = connections.getOrPut(conn) {
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        lifetime.whileOpen { conn.onServiceConnected(name, service) }
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        lifetime.whileOpen { conn.onServiceDisconnected(name) }
                    }
                    override fun onBindingDied(name: ComponentName) {
                        lifetime.whileOpen { conn.onBindingDied(name) }
                    }
                    override fun onNullBinding(name: ComponentName) {
                        lifetime.whileOpen { conn.onNullBinding(name) }
                    }
                }
            }
            try {
                super.bindService(service, proxy, flags).also { bound ->
                    if (!bound) connections.remove(conn)
                }
            } catch (failure: RuntimeException) {
                connections.remove(conn)
                throw failure
            }
        } ?: false

    override fun unbindService(conn: ServiceConnection) {
        lifetime.whileOpen {
            connections.remove(conn)?.let { super.unbindService(it) }
        }
    }

    fun close() = lifetime.close {
        val active = connections.values.toList()
        connections.clear()
        active.forEach { proxy ->
            try {
                super.unbindService(proxy)
            } catch (_: RuntimeException) {
                // A failed/removed framework binding has nothing left to unbind.
            }
        }
    }
}
