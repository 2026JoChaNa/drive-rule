package com.jochana.driverule

import android.app.Application
import com.jochana.driverule.app.AppContainer

class DriveRuleApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
