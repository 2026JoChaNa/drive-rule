package com.jochana.driverule.core.platform.carprobe

import android.app.Activity
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 확인용 코드(debug 전용). 제품 기능·안내 구조(결정 008)와 무관하다.
 *
 * AAOS 차량 속성(기어 · 시동 · 주차 브레이크 · 차속)을 일반 앱 권한으로 읽을 수 있는지
 * 한 번 읽고, 기어 변경 콜백을 등록해 결과를 logcat 태그 [TAG]로만 남긴다.
 * 화면 내용은 없으며 [PROBE_DURATION_MS] 뒤 스스로 닫힌다.
 *
 * 실행: adb shell am start --user <현재 사용자> -n com.jochana.driverule/.core.platform.carprobe.CarProbeActivity
 * 관찰: adb logcat -s DriveRuleCarProbe
 */
class CarProbeActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var gearCallbackRegistered = false

    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>?) {
            if (value == null) {
                Log.w(TAG, "GEAR callback: null value")
                return
            }
            Log.i(TAG, "GEAR callback: value=${value.value} (${gearName(value.value)}) status=${value.status} timestampNs=${value.timestamp}")
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "GEAR callback error: property=${hex(propertyId)} area=$areaId")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "probe start: sdk=${Build.VERSION.SDK_INT} automotive=${packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)}")
        PERMISSIONS.forEach(::logPermission)

        val missing = PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            runProbe()
        } else {
            Log.i(TAG, "runtime request: $missing")
            requestPermissions(missing.toTypedArray(), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) return
        permissions.forEachIndexed { index, permission ->
            val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "runtime result: $permission granted=$granted")
        }
        runProbe()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (gearCallbackRegistered) {
            propertyManager?.unregisterCallback(gearCallback)
        }
        car?.disconnect()
        Log.i(TAG, "probe end")
        super.onDestroy()
    }

    private fun logPermission(permission: String) {
        val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val level = try {
            when (packageManager.getPermissionInfo(permission, 0).protection) {
                PermissionInfo.PROTECTION_NORMAL -> "normal"
                PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
                PermissionInfo.PROTECTION_SIGNATURE -> "signature"
                else -> "other"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            "not defined on device"
        }
        Log.i(TAG, "permission: $permission level=$level granted=$granted")
    }

    private fun runProbe() {
        val connected = try {
            Car.createCar(applicationContext)
        } catch (e: Throwable) {
            // NoClassDefFoundError when the android.car library is absent (non-automotive image).
            Log.e(TAG, "Car.createCar failed: ${e.javaClass.name}: ${e.message}")
            null
        }
        if (connected == null) {
            Log.e(TAG, "Car unavailable; probe stops")
            finish()
            return
        }
        car = connected
        val manager = try {
            connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (e: Throwable) {
            Log.e(TAG, "getCarManager(PROPERTY_SERVICE) failed: ${e.javaClass.name}: ${e.message}")
            null
        }
        if (manager == null) {
            Log.e(TAG, "CarPropertyManager unavailable; probe stops")
            finish()
            return
        }
        propertyManager = manager

        PROPERTIES.forEach { (id, name) -> readOnce(manager, id, name) }
        registerGearCallback(manager)
        handler.postDelayed({ finish() }, PROBE_DURATION_MS)
    }

    private fun readOnce(manager: CarPropertyManager, id: Int, name: String) {
        // Since API 30 a missing config can mean "unsupported" or "no permission"; log both read paths.
        val config = try {
            manager.getCarPropertyConfig(id)
        } catch (e: Throwable) {
            Log.w(TAG, "$name(${hex(id)}) config: ${e.javaClass.name}: ${e.message}")
            null
        }
        Log.i(TAG, "$name(${hex(id)}) config=${if (config == null) "null" else "present access=${config.access} changeMode=${config.changeMode}"}")

        val outcome = try {
            val value: CarPropertyValue<Any>? = manager.getProperty(id, 0)
            if (value == null) "READ_NULL" else "SUCCESS value=${value.value}${if (id == VehiclePropertyIds.GEAR_SELECTION) " (${gearName(value.value)})" else ""} status=${value.status}"
        } catch (e: SecurityException) {
            "PERMISSION_DENIED ${e.message}"
        } catch (e: IllegalArgumentException) {
            "UNSUPPORTED ${e.message}"
        } catch (e: Throwable) {
            "ERROR ${e.javaClass.name}: ${e.message}"
        }
        Log.i(TAG, "$name(${hex(id)}) read: $outcome")
    }

    private fun registerGearCallback(manager: CarPropertyManager) {
        val outcome = try {
            gearCallbackRegistered = manager.registerCallback(
                gearCallback,
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
            )
            "registered=$gearCallbackRegistered"
        } catch (e: SecurityException) {
            "PERMISSION_DENIED ${e.message}"
        } catch (e: Throwable) {
            "ERROR ${e.javaClass.name}: ${e.message}"
        }
        Log.i(TAG, "GEAR callback register: $outcome")
    }

    private companion object {
        const val TAG = "DriveRuleCarProbe"
        const val REQUEST_CODE = 0x11
        const val PROBE_DURATION_MS = 180_000L

        val PERMISSIONS = listOf(Car.PERMISSION_POWERTRAIN, Car.PERMISSION_SPEED)

        val PROPERTIES = listOf(
            VehiclePropertyIds.GEAR_SELECTION to "GEAR_SELECTION",
            VehiclePropertyIds.IGNITION_STATE to "IGNITION_STATE",
            VehiclePropertyIds.PARKING_BRAKE_ON to "PARKING_BRAKE_ON",
            VehiclePropertyIds.PERF_VEHICLE_SPEED to "PERF_VEHICLE_SPEED",
        )

        fun hex(id: Int) = "0x" + Integer.toHexString(id)

        fun gearName(value: Any?): String = when (value) {
            1 -> "N"
            2 -> "R"
            4 -> "P"
            8 -> "D"
            else -> "?"
        }
    }
}
