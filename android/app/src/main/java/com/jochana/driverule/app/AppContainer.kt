package com.jochana.driverule.app

import android.content.Context
import com.jochana.driverule.BuildConfig
import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.core.platform.speech.PleosNameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.core.data.local.profile.DriverProfileDatabase
import com.jochana.driverule.core.data.repository.BackendCardErrorReportRepository
import com.jochana.driverule.core.data.repository.LocalThemePreferenceRepository
import com.jochana.driverule.domain.repository.CardErrorReportRepository
import com.jochana.driverule.core.data.repository.RoomDriverProfileRepository
import com.jochana.driverule.core.data.repository.UnconfiguredRuleCardRepository
import com.jochana.driverule.domain.repository.DriverProfileRepository
import com.jochana.driverule.domain.repository.RuleCardRepository
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    // Constructing the client performs no request. Profiles stay in the local repository.
    val backendApi: BackendApiClient by lazy { BackendApiClient.create(BuildConfig.API_BASE_URL) }
    fun createNameSpeechEngine(): NameSpeechEngine = PleosNameSpeechEngine(appContext)
    val ruleCardRepository: RuleCardRepository = UnconfiguredRuleCardRepository()
    val driverProfileRepository: DriverProfileRepository =
        RoomDriverProfileRepository(DriverProfileDatabase.create(context))
    val themePreferenceRepository: ThemePreferenceRepository = LocalThemePreferenceRepository(context)
    val uxRestrictionProvider: UxRestrictionProvider = AppEnvironment.createUxProvider()
    val previewEntries = AppEnvironment.previewEntries()
    val cardDetails = AppEnvironment.cardDetails()
    val ruleChanges = AppEnvironment.ruleChanges()
    // Resolving the client lazily keeps app start free of network setup.
    val cardErrorReportRepository: CardErrorReportRepository = BackendCardErrorReportRepository { backendApi }
}
