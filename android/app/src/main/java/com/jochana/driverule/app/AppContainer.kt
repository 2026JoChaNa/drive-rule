package com.jochana.driverule.app

import android.content.Context
import com.jochana.driverule.core.platform.speech.PleosNameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.core.data.local.profile.DriverProfileDatabase
import com.jochana.driverule.core.data.repository.LocalThemePreferenceRepository
import com.jochana.driverule.core.data.repository.RoomDriverProfileRepository
import com.jochana.driverule.core.data.repository.UnconfiguredRuleCardRepository
import com.jochana.driverule.domain.repository.DriverProfileRepository
import com.jochana.driverule.domain.repository.RuleCardRepository
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    fun createNameSpeechEngine(): NameSpeechEngine = PleosNameSpeechEngine(appContext)
    val ruleCardRepository: RuleCardRepository = UnconfiguredRuleCardRepository()
    val driverProfileRepository: DriverProfileRepository =
        RoomDriverProfileRepository(DriverProfileDatabase.create(context))
    val themePreferenceRepository: ThemePreferenceRepository = LocalThemePreferenceRepository(context)
    val uxRestrictionProvider: UxRestrictionProvider = AppEnvironment.createUxProvider()
    val previewEntries = AppEnvironment.previewEntries()
}
