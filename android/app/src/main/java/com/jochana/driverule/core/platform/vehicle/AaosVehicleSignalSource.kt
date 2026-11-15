package com.jochana.driverule.core.platform.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehicleIgnitionState
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.vehicle.IgnitionState
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.SignalStatus
import com.jochana.driverule.domain.vehicle.VehicleSignal
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import com.jochana.driverule.domain.vehicle.VehicleSignalSource
import com.jochana.driverule.domain.vehicle.newerSignal
import com.jochana.driverule.domain.vehicle.normalizeUxRestriction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Initial integration freshness policy; vehicle/device validation is still required. */
private const val SPEED_MAX_AGE_MS = 5_000L

/**
 * Read-only AAOS bridge. No Car type appears in this facade's fields or public signatures.
 * The optional android.car library is checked before loading [AaosSignalSession].
 */
class AaosVehicleSignalSource(context: Context) : VehicleSignalSource {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val controller = VehicleSignalController(
        isSupported = {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
                Class.forName("android.car.Car", false, appContext.classLoader) != null
        },
        createSession = { observer -> AaosSignalSession(appContext, handler, observer) },
    )
    override val state: StateFlow<VehicleSignalSnapshot> = controller.state

    private val freshnessCheck = object : Runnable {
        override fun run() {
            if (!controller.isRunning) return
            controller.expireSpeed(SystemClock.elapsedRealtime())
            handler.postDelayed(this, FRESHNESS_CHECK_INTERVAL_MS)
        }
    }

    override fun start() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Vehicle signals are main-thread owned" }
        controller.start()
        handler.removeCallbacks(freshnessCheck)
        if (controller.isRunning) handler.postDelayed(freshnessCheck, FRESHNESS_CHECK_INTERVAL_MS)
    }

    override fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Vehicle signals are main-thread owned" }
        handler.removeCallbacks(freshnessCheck)
        controller.stop()
    }

    private companion object {
        const val FRESHNESS_CHECK_INTERVAL_MS = 1_000L
    }
}

/** Platform-free lifetime gate: callbacks from an old source OR old Car connection are rejected. */
internal class VehicleSignalController(
    private val isSupported: () -> Boolean,
    private val speedMaxAgeMs: Long = SPEED_MAX_AGE_MS,
    private val createSession: (VehiclePlatformObserver) -> VehiclePlatformSession,
) : VehicleSignalSource {
    private val mutableState = MutableStateFlow(VehicleSignalSnapshot())
    override val state: StateFlow<VehicleSignalSnapshot> = mutableState.asStateFlow()
    var isRunning: Boolean = false
        private set
    private var generation = 0L
    private var connectionEpoch = 0L
    private var session: VehiclePlatformSession? = null

    override fun start() {
        if (isRunning) return
        isRunning = true
        val run = ++generation
        mutableState.value = mutableState.value.disconnected(SignalConnection.Connecting)
        try {
            if (!isSupported()) {
                fail(SignalConnection.Unavailable)
                return
            }
            val created = createSession(object : VehiclePlatformObserver {
                override fun onReady(): Long? {
                    if (!active(run)) return null
                    val epoch = ++connectionEpoch
                    mutableState.value = VehicleSignalSnapshot(connection = SignalConnection.Connected)
                    return epoch
                }

                override fun onLost() {
                    if (!active(run)) return
                    connectionEpoch++
                    mutableState.value = mutableState.value.disconnected(SignalConnection.Connecting)
                }

                override fun onFailure(connection: SignalConnection) {
                    if (active(run)) fail(connection)
                }

                override fun update(epoch: Long, transform: (VehicleSignalSnapshot) -> VehicleSignalSnapshot) {
                    if (active(run) && epoch == connectionEpoch &&
                        mutableState.value.connection == SignalConnection.Connected
                    ) mutableState.value = transform(mutableState.value)
                }
            })
            session = created
            created.open()
        } catch (_: ClassNotFoundException) {
            fail(SignalConnection.Unavailable)
        } catch (_: LinkageError) {
            fail(SignalConnection.Unavailable)
        } catch (_: SecurityException) {
            fail(SignalConnection.PermissionDenied)
        } catch (_: RuntimeException) {
            fail(SignalConnection.Failed)
        }
    }

    override fun stop() {
        isRunning = false
        generation++
        connectionEpoch++
        mutableState.value = mutableState.value.disconnected(SignalConnection.Stopped)
        val old = session
        session = null
        old?.close()
    }

    fun expireSpeed(nowElapsedMs: Long) {
        if (!isRunning) return
        session?.refreshPermissions()
        mutableState.value = mutableState.value.expireSpeed(nowElapsedMs, speedMaxAgeMs)
    }

    private fun active(run: Long): Boolean = isRunning && generation == run

    private fun fail(connection: SignalConnection) {
        isRunning = false
        generation++
        connectionEpoch++
        mutableState.value = mutableState.value.disconnected(connection)
        val old = session
        session = null
        old?.close()
    }
}

internal interface VehiclePlatformSession {
    fun open()
    fun close()
    fun refreshPermissions() = Unit
}

internal interface VehiclePlatformObserver {
    fun onReady(): Long?
    fun onLost()
    fun onFailure(connection: SignalConnection)
    fun update(epoch: Long, transform: (VehicleSignalSnapshot) -> VehicleSignalSnapshot)
}

/** Loaded only on AAOS. Each ready callback creates a new set of listeners with a new epoch. */
private class AaosSignalSession(
    private val context: Context,
    private val handler: Handler,
    private val observer: VehiclePlatformObserver,
) : VehiclePlatformSession {
    private var car: Car? = null
    private var closed = false
    private var propertyManager: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private val propertyCallbacks = mutableListOf<CarPropertyManager.CarPropertyEventCallback>()
    private var uxRevision = 0L
    private var activeEpoch: Long? = null
    private var initialUxRead: Job? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun open() {
        // Car may invoke ready synchronously *inside* createCar when already connected.
        val created = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
            if (closed) {
                cleanup { connectedCar.disconnect() }
            } else {
                car = connectedCar
                observer.onLost()
                clearListeners()
                if (ready) {
                    val epoch = observer.onReady()
                    if (epoch != null) {
                        activeEpoch = epoch
                        subscribe(connectedCar, epoch)
                    }
                }
            }
        }
        if (closed) cleanup { created?.disconnect() } else car = created
        if (created == null && !closed) observer.onFailure(SignalConnection.Unavailable)
    }

    private fun subscribe(connectedCar: Car, epoch: Long) {
        subscribeUx(connectedCar, epoch)
        try {
            val manager = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            propertyManager = manager
            if (manager == null) {
                allPropertiesUnavailable(epoch, SignalStatus.Unavailable)
                return
            }
            subscribeProperty(manager, VehiclePropertyIds.GEAR_SELECTION, Car.PERMISSION_POWERTRAIN, epoch)
            subscribeProperty(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED, Car.PERMISSION_SPEED, epoch)
            subscribeProperty(manager, VehiclePropertyIds.IGNITION_STATE, Car.PERMISSION_POWERTRAIN, epoch)
            subscribeProperty(manager, VehiclePropertyIds.PARKING_BRAKE_ON, Car.PERMISSION_POWERTRAIN, epoch)
        } catch (_: SecurityException) {
            allPropertiesUnavailable(epoch, SignalStatus.PermissionDenied)
        } catch (_: RuntimeException) {
            allPropertiesUnavailable(epoch, SignalStatus.Failed)
        } catch (_: LinkageError) {
            allPropertiesUnavailable(epoch, SignalStatus.Unavailable)
        }
    }

    private fun subscribeUx(connectedCar: Car, epoch: Long) {
        try {
            val manager = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            uxManager = manager
            if (manager == null) {
                uxFailure(epoch, SignalStatus.Unavailable)
                return
            }
            manager.registerListener { restrictions ->
                if (closed || activeEpoch != epoch) return@registerListener
                uxRevision++
                publishUx(epoch, restrictions)
            }
            val initialRevision = uxRevision
            // The initial binder read cannot block the UI, and must not overwrite a newer callback.
            initialUxRead = ioScope.launch {
                val initial = runCatching { manager.currentCarUxRestrictions }
                withContext(Dispatchers.Main.immediate) {
                    if (!closed && activeEpoch == epoch && uxRevision == initialRevision) {
                        initial.fold(
                            onSuccess = { publishUx(epoch, it) },
                            onFailure = {
                                uxFailure(epoch, when (it) {
                                    is SecurityException -> SignalStatus.PermissionDenied
                                    is LinkageError -> SignalStatus.Unavailable
                                    else -> SignalStatus.Failed
                                })
                            },
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            uxFailure(epoch, SignalStatus.PermissionDenied)
        } catch (_: RuntimeException) {
            uxFailure(epoch, SignalStatus.Failed)
        } catch (_: LinkageError) {
            uxFailure(epoch, SignalStatus.Unavailable)
        }
    }

    private fun publishUx(epoch: Long, restrictions: CarUxRestrictions?) {
        val now = SystemClock.elapsedRealtime()
        val signal = if (restrictions == null) {
            VehicleSignal<VehicleUxState>(SignalStatus.Unavailable, observedAtElapsedMs = now)
        } else {
            VehicleSignal(
                status = SignalStatus.Available,
                value = normalizeUxRestriction(restrictions.isRequiresDistractionOptimization, restrictions.activeRestrictions),
                observedAtElapsedMs = now,
            )
        }
        observer.update(epoch) { it.copy(ux = signal) }
    }

    private fun uxFailure(epoch: Long, status: SignalStatus) {
        val now = SystemClock.elapsedRealtime()
        observer.update(epoch) { it.copy(ux = VehicleSignal(status = status, observedAtElapsedMs = now)) }
    }

    private fun subscribeProperty(manager: CarPropertyManager, propertyId: Int, permission: String, epoch: Long) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            propertyFailure(epoch, propertyId, SignalStatus.PermissionDenied)
            return
        }
        try {
            if (manager.getCarPropertyConfig(propertyId) == null) {
                propertyFailure(epoch, propertyId, SignalStatus.Unavailable)
                return
            }
            var accepting = true
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    if (!accepting || closed || activeEpoch != epoch || value.propertyId != propertyId ||
                        value.areaId != GLOBAL_AREA) return
                    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                        propertyFailure(epoch, propertyId, SignalStatus.PermissionDenied)
                        return
                    }
                    val status = when (value.status) {
                        CarPropertyValue.STATUS_AVAILABLE -> SignalStatus.Available
                        CarPropertyValue.STATUS_UNAVAILABLE -> SignalStatus.Unavailable
                        else -> SignalStatus.Failed
                    }
                    // The SDK explicitly forbids using the value when status is not AVAILABLE.
                    publishProperty(epoch, propertyId, status,
                        if (status == SignalStatus.Available) value.value else null, value.timestamp)
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    if (accepting && !closed && activeEpoch == epoch && areaId == GLOBAL_AREA) {
                        propertyFailure(epoch, propertyId, SignalStatus.Failed)
                    }
                }
            }
            // Registration delivers initial values (including unavailable/error states) for target 34.
            // This avoids getProperty(), which the API warns may block for several seconds.
            val rate = if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                CarPropertyManager.SENSOR_RATE_NORMAL
            } else CarPropertyManager.SENSOR_RATE_ONCHANGE
            propertyCallbacks += callback
            var registered = false
            try {
                registered = manager.registerCallback(callback, propertyId, rate)
                if (!registered) {
                    propertyFailure(epoch, propertyId, if (context.checkSelfPermission(permission) !=
                        PackageManager.PERMISSION_GRANTED) SignalStatus.PermissionDenied else SignalStatus.Failed)
                }
            } finally {
                if (!registered) {
                    accepting = false
                    propertyCallbacks.remove(callback)
                    cleanup { manager.unregisterCallback(callback) }
                }
            }
        } catch (_: SecurityException) {
            propertyFailure(epoch, propertyId, SignalStatus.PermissionDenied)
        } catch (_: IllegalArgumentException) {
            propertyFailure(epoch, propertyId, SignalStatus.Unavailable)
        } catch (_: RuntimeException) {
            propertyFailure(epoch, propertyId, SignalStatus.Failed)
        }
    }

    private fun publishProperty(epoch: Long, propertyId: Int, status: SignalStatus, raw: Any?, timestampNanos: Long) {
        val now = SystemClock.elapsedRealtime()
        observer.update(epoch) { snapshot ->
            when (propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> snapshot.copy(gear = newerSignal(snapshot.gear,
                    AaosSignalValues.gear(raw, status, now, timestampNanos)))
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> snapshot.copy(speedMetersPerSecond = newerSignal(
                    snapshot.speedMetersPerSecond, AaosSignalValues.speed(raw, status, now, timestampNanos)))
                VehiclePropertyIds.IGNITION_STATE -> snapshot.copy(ignition = newerSignal(snapshot.ignition,
                    AaosSignalValues.ignition(raw, status, now, timestampNanos)))
                VehiclePropertyIds.PARKING_BRAKE_ON -> snapshot.copy(parkingBrake = newerSignal(snapshot.parkingBrake,
                    AaosSignalValues.parkingBrake(raw, status, now, timestampNanos)))
                else -> snapshot
            }.expireSpeed(now, SPEED_MAX_AGE_MS)
        }
    }

    private fun propertyFailure(epoch: Long, propertyId: Int, status: SignalStatus) {
        publishProperty(epoch, propertyId, status, null, -1L)
    }

    private fun allPropertiesUnavailable(epoch: Long, status: SignalStatus) {
        val now = SystemClock.elapsedRealtime()
        observer.update(epoch) {
            it.copy(gear = newerSignal(it.gear, VehicleSignal(status, observedAtElapsedMs = now)),
                speedMetersPerSecond = newerSignal(it.speedMetersPerSecond, VehicleSignal(status, observedAtElapsedMs = now)),
                ignition = newerSignal(it.ignition, VehicleSignal(status, observedAtElapsedMs = now)),
                parkingBrake = newerSignal(it.parkingBrake, VehicleSignal(status, observedAtElapsedMs = now)))
        }
    }

    override fun refreshPermissions() {
        val epoch = activeEpoch ?: return
        if (closed) return
        val speedGranted = context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED
        val powertrainGranted = context.checkSelfPermission(Car.PERMISSION_POWERTRAIN) == PackageManager.PERMISSION_GRANTED
        val now = SystemClock.elapsedRealtime()
        observer.update(epoch) { it.withPermissions(speedGranted, powertrainGranted, now) }
    }

    override fun close() {
        if (closed) return
        closed = true
        ioScope.cancel()
        clearListeners()
        val old = car
        car = null
        cleanup { old?.disconnect() }
    }

    private fun clearListeners() {
        activeEpoch = null
        initialUxRead?.cancel()
        initialUxRead = null
        uxRevision++
        val oldUx = uxManager
        uxManager = null
        if (oldUx != null) cleanup { oldUx.unregisterListener() }
        val oldProperties = propertyManager
        propertyManager = null
        val callbacks = propertyCallbacks.toList()
        propertyCallbacks.clear()
        if (oldProperties != null) callbacks.forEach { callback -> cleanup { oldProperties.unregisterCallback(callback) } }
    }

    private fun cleanup(action: () -> Unit) {
        try {
            action()
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Vehicle listener cleanup failed: ${failure.javaClass.simpleName}")
        } catch (failure: LinkageError) {
            Log.w(TAG, "Vehicle library cleanup unavailable: ${failure.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "VehicleSignals"
        const val GLOBAL_AREA = 0
    }
}

/** Pure normalization; only SDK compile-time constants are referenced, not SDK objects. */
internal object AaosSignalValues {
    fun gear(raw: Any?, status: SignalStatus, now: Long, timestampNanos: Long): VehicleSignal<SelectedGear> =
        normalize(raw, status, now, timestampNanos) {
            when (it) {
                VehicleGear.GEAR_NEUTRAL -> SelectedGear.Neutral
                VehicleGear.GEAR_REVERSE -> SelectedGear.Reverse
                VehicleGear.GEAR_PARK -> SelectedGear.Park
                VehicleGear.GEAR_DRIVE -> SelectedGear.Drive
                VehicleGear.GEAR_FIRST -> SelectedGear.First
                VehicleGear.GEAR_SECOND -> SelectedGear.Second
                VehicleGear.GEAR_THIRD -> SelectedGear.Third
                VehicleGear.GEAR_FOURTH -> SelectedGear.Fourth
                VehicleGear.GEAR_FIFTH -> SelectedGear.Fifth
                VehicleGear.GEAR_SIXTH -> SelectedGear.Sixth
                VehicleGear.GEAR_SEVENTH -> SelectedGear.Seventh
                VehicleGear.GEAR_EIGHTH -> SelectedGear.Eighth
                VehicleGear.GEAR_NINTH -> SelectedGear.Ninth
                else -> null
            }
        }

    fun ignition(raw: Any?, status: SignalStatus, now: Long, timestampNanos: Long): VehicleSignal<IgnitionState> =
        normalize(raw, status, now, timestampNanos) {
            when (it) {
                VehicleIgnitionState.LOCK -> IgnitionState.Lock
                VehicleIgnitionState.OFF -> IgnitionState.Off
                VehicleIgnitionState.ACC -> IgnitionState.Accessory
                VehicleIgnitionState.ON -> IgnitionState.On
                VehicleIgnitionState.START -> IgnitionState.Start
                else -> null
            }
        }

    fun speed(raw: Any?, status: SignalStatus, now: Long, timestampNanos: Long): VehicleSignal<Float> =
        normalize(raw, status, now, timestampNanos) { (it as? Float)?.takeIf(Float::isFinite) }

    fun parkingBrake(raw: Any?, status: SignalStatus, now: Long, timestampNanos: Long): VehicleSignal<Boolean> =
        normalize(raw, status, now, timestampNanos) { it as? Boolean }

    private fun <T> normalize(raw: Any?, status: SignalStatus, now: Long, timestampNanos: Long,
        convert: (Any?) -> T?): VehicleSignal<T> {
        // Invalid timestamps must not poison the watermark, including unavailable/error events.
        val sourceMs = timestampNanos.takeIf { it >= 0 }?.div(1_000_000L)?.takeIf { it <= now + 1_000L }
        if (status != SignalStatus.Available) return VehicleSignal(status = status, observedAtElapsedMs = now,
            sourceTimestampElapsedMs = sourceMs)
        if (sourceMs == null) {
            return VehicleSignal(status = SignalStatus.Failed, observedAtElapsedMs = now)
        }
        val value = convert(raw) ?: return VehicleSignal(status = SignalStatus.Unknown,
            observedAtElapsedMs = now, sourceTimestampElapsedMs = sourceMs)
        return VehicleSignal(SignalStatus.Available, value, now, sourceMs)
    }
}
