package com.jochana.driverule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jochana.driverule.app.DriveRuleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as DriveRuleApplication).appContainer
        setContent {
            DriveRuleApp(appContainer = appContainer)
        }
    }
}
