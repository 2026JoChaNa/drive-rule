package com.jochana.driverule.core.model

/** Unknown never implies that detailed interaction is allowed. */
enum class VehicleUxState {
    Unknown,
    Restricted,
    Unrestricted,
}
