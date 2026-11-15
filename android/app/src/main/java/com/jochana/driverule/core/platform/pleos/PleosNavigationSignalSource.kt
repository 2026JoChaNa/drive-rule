package com.jochana.driverule.core.platform.pleos

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import com.jochana.driverule.domain.vehicle.NavigationDestination
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.jochana.driverule.domain.vehicle.NavigationActivityStatus
import com.jochana.driverule.domain.vehicle.NavigationCamera
import com.jochana.driverule.domain.vehicle.NavigationCaution
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.NavigationSignalSource
import com.jochana.driverule.domain.vehicle.NavigationSignalUnavailable
import com.jochana.driverule.domain.vehicle.NavigationTurn
import com.jochana.driverule.domain.vehicle.expire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read-only NaviHelper 3.0.5 integration. All ownership transitions run on the main thread.
 * Connected means initialization succeeded, not a guaranteed live binder/route: public SDK
 * callbacks do not expose every disconnection. Each observation expires independently.
 */
class PleosNavigationSignalSource internal constructor(
    private val clientFactory: (NavigationClient.Listener) -> NavigationClient,
    private val permissionGranted: () -> Boolean,
    private val dispatcher: NavigationDispatcher,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val initializationTimeoutMs: Long = INITIALIZATION_TIMEOUT_MS,
) : NavigationSignalSource {
    constructor(context: Context) : this(
        clientFactory = sdkClientFactory(context.applicationContext),
        permissionGranted = permissionChecker(context.applicationContext),
        dispatcher = MainNavigationDispatcher(),
    )

    private val mutableState = MutableStateFlow(NavigationSignalSnapshot())
    override val state: StateFlow<NavigationSignalSnapshot> = mutableState.asStateFlow()
    private var started = false
    private var generation = 0L
    private var routeRevision = 0L
    private var client: NavigationClient? = null
    private var initializationTimeout: NavigationCancellation? = null
    private var freshnessTick: NavigationCancellation? = null
    private var lastRouteRefreshRequestMs: Long? = null
    private var callbackReceivedAtMs = 0L
    private var lastAcceptedCallbackMs = Long.MIN_VALUE

    init {
        require(maxAgeMs > 0)
        require(initializationTimeoutMs > 0)
    }

    override fun start() = dispatcher.execute start@ {
        if (started) return@start
        started = true
        lastAcceptedCallbackMs = Long.MIN_VALUE
        val token = ++generation
        mutableState.value = NavigationSignalSnapshot(connection = NavigationConnection.Connecting,
            routeRevision = ++routeRevision)
        guarded(token) {
            if (!permissionGranted()) {
                fail(NavigationConnection.PermissionDenied, NavigationSignalUnavailable.PermissionDenied)
                return@guarded
            }
            val instance = clientFactory(listener(token))
            client = instance
            initializationTimeout = dispatcher.schedule(initializationTimeoutMs) {
                if (accepts(token) && mutableState.value.connection == NavigationConnection.Connecting) {
                    fail(NavigationConnection.Unavailable, NavigationSignalUnavailable.SourceUnavailable)
                }
            }
            instance.initialize(
                onConnected = { dispatcher.execute { connected(token) } },
                onFailed = { error -> dispatcher.execute {
                    if (accepts(token)) {
                        if (error is SecurityException) {
                            fail(NavigationConnection.PermissionDenied, NavigationSignalUnavailable.PermissionDenied)
                        } else {
                            fail(NavigationConnection.Unavailable, NavigationSignalUnavailable.SourceUnavailable)
                        }
                    }
                } },
            )
        }
    }

    override fun stop() = dispatcher.execute {
        if (!started && mutableState.value.connection == NavigationConnection.Stopped) return@execute
        started = false
        generation++
        dispose()
        mutableState.value = unavailableSnapshot(NavigationConnection.Stopped, NavigationSignalUnavailable.SourceStopped)
    }

    private fun connected(token: Long) {
        if (!accepts(token) || mutableState.value.connection != NavigationConnection.Connecting) return
        initializationTimeout?.cancel()
        initializationTimeout = null
        guarded(token) {
            mutableState.value = mutableState.value.copy(connection = NavigationConnection.Connected)
            lastRouteRefreshRequestMs = dispatcher.elapsedRealtimeMs()
            client?.listenAndRequestSnapshot()
            if (accepts(token)) scheduleFreshness(token)
        }
    }

    private fun listener(token: Long) = object : NavigationClient.Listener {
        override fun onStatus(status: NavigationActivityStatus?) = update(token) {
            if (status == null) copy(navigationStatus = invalid()) else copy(
                navigationStatus = fresh(status),
                // Only the explicit SDK status establishes route-guidance activity.
                routeActive = when (status) {
                    NavigationActivityStatus.Inactive, NavigationActivityStatus.FreeDrive -> fresh(false)
                    NavigationActivityStatus.RouteDrive -> fresh(true)
                    NavigationActivityStatus.Initializing -> NavigationObservation.Unknown
                },
                turns = if (status == NavigationActivityStatus.RouteDrive) turns else NavigationObservation.Unknown,
                destination = if (status == NavigationActivityStatus.RouteDrive) destination else NavigationObservation.Unknown,
                routeRevision = if (status != NavigationActivityStatus.RouteDrive &&
                    (routeActive as? NavigationObservation.Fresh)?.value == true) nextRouteRevision() else routeRevision,
                cautions = if (status == NavigationActivityStatus.Inactive) NavigationObservation.Unknown else cautions,
                cameras = if (status == NavigationActivityStatus.Inactive) NavigationObservation.Unknown else cameras,
                rawReportedRoadSpeedLimit = if (status == NavigationActivityStatus.Inactive) NavigationObservation.Unknown else rawReportedRoadSpeedLimit,
            )
        }

        override fun onRouteActive(active: Boolean) = update(token) {
            copy(routeActive = fresh(active), turns = if (active) turns else NavigationObservation.Unknown,
                destination = if (active) destination else NavigationObservation.Unknown,
                routeRevision = if (!active && (routeActive as? NavigationObservation.Fresh)?.value == true)
                    nextRouteRevision() else routeRevision)
        }

        override fun onRouteInvalidated() = update(token) {
            // A new/recalculated/removed route invalidates old maneuver evidence immediately.
            copy(routeActive = NavigationObservation.Unknown, turns = NavigationObservation.Unknown,
                destination = NavigationObservation.Unknown,
                routeRevision = nextRouteRevision())
        }

        override fun onTurns(turns: List<NavigationTurn>?) = update(token) {
            copy(turns = turns?.let { fresh(it.toList()) } ?: invalid())
        }

        override fun onDestination(destination: NavigationDestination?) = update(token) {
            if (destination == null || !destination.latitude.isFinite() || destination.latitude !in -90.0..90.0 ||
                !destination.longitude.isFinite() || destination.longitude !in -180.0..180.0 ||
                !destination.remainingDistanceMeters.isFinite() || destination.remainingDistanceMeters < 0) {
                copy(destination = invalid())
            } else {
                val previous = (this.destination as? NavigationObservation.Fresh)?.value
                val replaced = previous != null &&
                    (previous.latitude != destination.latitude || previous.longitude != destination.longitude)
                copy(destination = fresh(destination),
                    turns = if (replaced) NavigationObservation.Unknown else turns,
                    routeRevision = if (replaced) nextRouteRevision() else routeRevision)
            }
        }

        override fun onCautions(cautions: List<NavigationCaution>?) = update(token) {
            copy(cautions = cautions?.let { fresh(it.toList()) } ?: invalid())
        }

        override fun onCameras(cameras: List<NavigationCamera>?) = update(token) {
            copy(cameras = cameras?.let { fresh(it.toList()) } ?: invalid())
        }

        override fun onSpeedLimit(rawSpeedLimit: Int) = update(token) {
            copy(rawReportedRoadSpeedLimit = if (rawSpeedLimit > 0) fresh(rawSpeedLimit) else invalid())
        }

        override fun onNavigationError() = update(token) {
            // Navigation request errors are not proof of a disconnected SDK service.
            copy(routeActive = NavigationObservation.Unknown, turns = NavigationObservation.Unknown,
                destination = NavigationObservation.Unknown, routeRevision = nextRouteRevision())
        }
    }

    private fun update(token: Long, change: NavigationSignalSnapshot.() -> NavigationSignalSnapshot) {
        // Capture at callback receipt, before a busy main queue can make old input look fresh.
        val receivedAt = dispatcher.elapsedRealtimeMs()
        dispatcher.execute {
            if (accepts(token) && mutableState.value.connection == NavigationConnection.Connected) {
                val now = dispatcher.elapsedRealtimeMs()
                if (now < receivedAt || now - receivedAt >= maxAgeMs || receivedAt < lastAcceptedCallbackMs) {
                    mutableState.value = mutableState.value.expire(now, maxAgeMs)
                    return@execute
                }
                callbackReceivedAtMs = receivedAt
                lastAcceptedCallbackMs = receivedAt
                mutableState.value = mutableState.value.change()
            }
        }
    }

    private fun <T> fresh(value: T): NavigationObservation<T> =
        NavigationObservation.Fresh(value, callbackReceivedAtMs)

    private fun invalid() = NavigationObservation.Unavailable(NavigationSignalUnavailable.InvalidData)

    private fun nextRouteRevision(): Long = ++routeRevision

    private fun scheduleFreshness(token: Long) {
        freshnessTick = dispatcher.schedule(FRESHNESS_CHECK_MS) {
            if (accepts(token) && mutableState.value.connection == NavigationConnection.Connected) {
                guarded(token) {
                    if (!permissionGranted()) {
                        fail(NavigationConnection.PermissionDenied, NavigationSignalUnavailable.PermissionDenied)
                    } else {
                        val now = dispatcher.elapsedRealtimeMs()
                        mutableState.value = mutableState.value.expire(now, maxAgeMs)
                        // Route status is on-change. Read it periodically so an unchanged active
                        // route can remain fresh; requesting alone never refreshes an observation.
                        val lastRequest = lastRouteRefreshRequestMs
                        if (lastRequest == null || now < lastRequest ||
                            now - lastRequest >= ROUTE_REFRESH_INTERVAL_MS) {
                            lastRouteRefreshRequestMs = now
                            client?.requestRouteState()
                            client?.requestDestination()
                        }
                        if (accepts(token)) scheduleFreshness(token)
                    }
                }
            }
        }
    }

    private fun accepts(token: Long) = started && generation == token

    private fun fail(connection: NavigationConnection, reason: NavigationSignalUnavailable) {
        generation++
        dispose()
        mutableState.value = unavailableSnapshot(connection, reason)
        // A failure is latched for this session; stop/start explicitly retries, without a loop.
    }

    private fun dispose() {
        initializationTimeout?.cancel()
        initializationTimeout = null
        freshnessTick?.cancel()
        freshnessTick = null
        lastRouteRefreshRequestMs = null
        val previous = client
        client = null
        try {
            previous?.release()
        } catch (_: RuntimeException) {
            // No raw SDK data, paths, coordinates, IDs, or exception strings are logged.
        } catch (_: LinkageError) {
            // A device without the matching SDK service remains unavailable.
        }
    }

    private fun guarded(token: Long, block: () -> Unit) {
        try {
            block()
        } catch (_: SecurityException) {
            if (accepts(token)) fail(NavigationConnection.PermissionDenied, NavigationSignalUnavailable.PermissionDenied)
        } catch (_: RuntimeException) {
            if (accepts(token)) fail(NavigationConnection.Failed, NavigationSignalUnavailable.Failed)
        } catch (_: LinkageError) {
            if (accepts(token)) fail(NavigationConnection.Unavailable, NavigationSignalUnavailable.SourceUnavailable)
        }
    }

    private fun unavailableSnapshot(connection: NavigationConnection, reason: NavigationSignalUnavailable): NavigationSignalSnapshot {
        val unavailable = NavigationObservation.Unavailable(reason)
        return NavigationSignalSnapshot(connection, unavailable, unavailable, unavailable, unavailable, unavailable, unavailable,
            routeRevision = nextRouteRevision(), destination = unavailable)
    }

    companion object {
        const val NAVI_ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE"
        // Conservative adapter defaults awaiting vehicle validation, not SDK delivery guarantees.
        private const val INITIALIZATION_TIMEOUT_MS = 15_000L
        private const val DEFAULT_MAX_AGE_MS = 10_000L
        private const val FRESHNESS_CHECK_MS = 1_000L
        private const val ROUTE_REFRESH_INTERVAL_MS = 5_000L

        private fun sdkClientFactory(applicationContext: Context): (NavigationClient.Listener) -> NavigationClient =
            { listener -> SdkNavigationClient(applicationContext, listener) }

        private fun permissionChecker(applicationContext: Context): () -> Boolean = {
            applicationContext.checkSelfPermission(NAVI_ROUTE_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }
    }
}

internal fun interface NavigationCancellation { fun cancel() }

internal interface NavigationDispatcher {
    fun execute(action: () -> Unit)
    fun schedule(delayMs: Long, action: () -> Unit): NavigationCancellation
    fun elapsedRealtimeMs(): Long
}

private class MainNavigationDispatcher : NavigationDispatcher {
    private val handler = Handler(Looper.getMainLooper())
    override fun execute(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
    override fun schedule(delayMs: Long, action: () -> Unit): NavigationCancellation {
        val task = Runnable(action)
        handler.postDelayed(task, delayMs)
        return NavigationCancellation { handler.removeCallbacks(task) }
    }
    override fun elapsedRealtimeMs() = SystemClock.elapsedRealtime()
}

internal interface NavigationClient {
    fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)
    fun listenAndRequestSnapshot()
    fun requestRouteState()
    fun requestDestination()
    fun release()

    interface Listener {
        fun onStatus(status: NavigationActivityStatus?)
        fun onRouteActive(active: Boolean)
        fun onRouteInvalidated()
        fun onTurns(turns: List<NavigationTurn>?)
        fun onDestination(destination: NavigationDestination?)
        fun onCautions(cautions: List<NavigationCaution>?)
        fun onCameras(cameras: List<NavigationCamera>?)
        fun onSpeedLimit(rawSpeedLimit: Int)
        fun onNavigationError()
    }
}

private class SdkNavigationClient(context: Context, private val listener: NavigationClient.Listener) : NavigationClient {
    private val sdk = NaviHelper(context)
    private var registered = false
    private val sdkListener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) = listener.onStatus(when (status) {
            NavigationStatus.INACTIVE -> NavigationActivityStatus.Inactive
            NavigationStatus.INITIALIZING -> NavigationActivityStatus.Initializing
            NavigationStatus.FREE_DRIVE -> NavigationActivityStatus.FreeDrive
            NavigationStatus.ROUTE_DRIVE -> NavigationActivityStatus.RouteDrive
            else -> null
        })
        override fun onRouteStateInfo(info: RouteStateInfo) = listener.onRouteActive(info.isRouting)
        override fun onRouteStarted(info: RouteStartInfo) = listener.onRouteInvalidated()
        override fun onRouteEnded() = listener.onRouteInvalidated()
        override fun onRouteCancelled() = listener.onRouteInvalidated()
        override fun onDestinationChanged() = listener.onRouteInvalidated()
        override fun onReRouteEnded() = listener.onRouteInvalidated()
        override fun onRoutingCompleted() = listener.onRouteInvalidated()
        override fun onError(errorCode: NaviErrorCode) = listener.onNavigationError()
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = listener.onSpeedLimit(speedLimit)
        override fun onTBTInfo(infos: List<TBTInfo>) = listener.onTurns(
            if (infos.all { it.distance.isFinite() && it.distance >= 0 }) {
                infos.map { NavigationTurn(it.type.name, it.distance) }
            } else null,
        )
        override fun onDestinationInfo(info: DestinationInfo) = listener.onDestination(
            NavigationDestination(info.latitude, info.longitude, info.remainDistance.toDouble()),
        )
        override fun onCautionPointInfo(infos: List<CautionPoint>) = listener.onCautions(
            if (infos.all { it.remainDistance >= 0 }) {
                infos.map { NavigationCaution(it.type.name, it.remainDistance) }
            } else null,
        )
        override fun onCameraAlert(infos: List<CameraAlert>) = listener.onCameras(
            if (infos.all { it.remainDistance >= 0 }) {
                infos.map {
                    NavigationCamera(
                        typeCode = it.type.name,
                        reportedActive = it.status,
                        remainingDistanceMeters = it.remainDistance,
                        speedLimitKph = it.speedLimit.takeIf { value -> value > 0 },
                        sectionAverageSpeedKph = it.sectionAvrSpeed.takeIf { value -> value >= 0 },
                        reportedOverSpeed = it.isOverSpeed,
                        reportedOverSectionAverageSpeed = it.isOverSectionAvrSpeed,
                    )
                }
            } else null,
        )
        // Names, addresses, POI/IDs, descriptions and trip records deliberately use SDK no-ops.
    }

    override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) = sdk.initialize(onConnected, onFailed)

    // The owning source checks NAVI_ROUTE before initialization and catches revocation at this call.
    @SuppressLint("MissingPermission")
    override fun listenAndRequestSnapshot() {
        registered = true
        sdk.addListener(sdkListener)
        requestRouteState()
        sdk.getTBTInfo()
        requestDestination()
    }

    @SuppressLint("MissingPermission")
    override fun requestRouteState() { sdk.getRouteStateInfo() }

    @SuppressLint("MissingPermission")
    override fun requestDestination() { sdk.getDestinationInfo() }

    override fun release() {
        try {
            if (registered) sdk.removeListener(sdkListener)
        } finally {
            registered = false
            sdk.release()
        }
    }
}
