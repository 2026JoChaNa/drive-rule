package com.jochana.driverule

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jochana.driverule.app.DriveRuleApp
import com.jochana.driverule.app.AppEnvironment
import com.jochana.driverule.app.BackgroundGuidanceHost

class MainActivity : ComponentActivity() {
    private lateinit var backgroundGuidanceHost: BackgroundGuidanceHost
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundGuidanceHost = AppEnvironment.createBackgroundHost(this)
        enableEdgeToEdge()

        val appContainer = (application as DriveRuleApplication).appContainer
        setContent {
            DriveRuleApp(appContainer = appContainer, onThemeChanged = { dark ->
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }, backgroundControls = { backgroundGuidanceHost.Controls() })
        }
    }

    override fun onStart() {
        super.onStart()
        backgroundGuidanceHost.onVisible()
    }

    override fun onStop() {
        backgroundGuidanceHost.onHidden()
        super.onStop()
    }
}
