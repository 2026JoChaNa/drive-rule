package com.jochana.driverule.app

import android.content.Context
import com.jochana.driverule.BuildConfig
import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.core.platform.speech.PleosNameSpeechEngine
import com.jochana.driverule.core.platform.speech.GuidanceAwareNameSpeechEngine
import com.jochana.driverule.core.platform.audio.PleosGuidanceSpeechOutput
import com.jochana.driverule.app.guidance.VehicleMonitoringRuntime
import com.jochana.driverule.core.platform.vehicle.createVehicleSignalSource
import com.jochana.driverule.core.platform.pleos.PleosNavigationSignalSource
import com.jochana.driverule.core.platform.pleos.PleosLocationSignalSource
import com.jochana.driverule.core.data.repository.SchoolZoneRepository
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.core.data.local.profile.DriverProfileDatabase
import com.jochana.driverule.core.data.repository.BackendCardErrorReportRepository
import com.jochana.driverule.core.data.repository.LocalThemePreferenceRepository
import com.jochana.driverule.domain.repository.CardErrorReportRepository
import com.jochana.driverule.core.data.repository.RoomDriverProfileRepository
import com.jochana.driverule.core.data.repository.CachedRuleCardRepository
import com.jochana.driverule.core.data.local.law.LawCardDatabase
import com.jochana.driverule.core.data.local.law.RoomLawCardCache
import com.jochana.driverule.domain.repository.DriverProfileRepository
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import kotlinx.coroutines.Dispatchers
import com.jochana.driverule.core.data.local.guidance.FileGuidanceAttemptLedger
import com.jochana.driverule.domain.guidance.GuidanceAttemptLedger

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    // Constructing the client performs no request. Profiles stay in the local repository.
    val backendApi: BackendApiClient by lazy { BackendApiClient.create(BuildConfig.API_BASE_URL) }
    fun createNameSpeechEngine(): NameSpeechEngine = GuidanceAwareNameSpeechEngine(
        dispatcher = Dispatchers.Main.immediate,
        awaitQuiet = { PleosGuidanceSpeechOutput.awaitQuiescence() },
        createDelegate = { PleosNameSpeechEngine(appContext) },
    )
    val ruleCardRepository: CachedRuleCardRepository by lazy {
        CachedRuleCardRepository(
            cache = RoomLawCardCache(LawCardDatabase.create(appContext)),
            fetch = { token -> backendApi.syncCards(token) },
        )
    }
    val schoolZoneRepository: SchoolZoneRepository by lazy { SchoolZoneRepository(appContext, backendApi) }
    val driverProfileRepository: DriverProfileRepository =
        RoomDriverProfileRepository(DriverProfileDatabase.create(context))
    val guidanceAttemptLedger: GuidanceAttemptLedger by lazy { FileGuidanceAttemptLedger(appContext) }
    val themePreferenceRepository: ThemePreferenceRepository = LocalThemePreferenceRepository(context)
    val vehicleMonitoring: VehicleMonitoringRuntime by lazy {
        VehicleMonitoringRuntime(createVehicleSignalSource(appContext), PleosNavigationSignalSource(appContext),
            PleosLocationSignalSource(appContext))
    }
    val uxRestrictionProvider: UxRestrictionProvider = AppEnvironment.createUxProvider(vehicleMonitoring.uxProvider)
    val previewEntries = AppEnvironment.previewEntries()
    val cardDetails = AppEnvironment.cardDetails()
    val ruleChanges = AppEnvironment.ruleChanges()
    // One gate per process: the pre-departure alert appears at most once per app start.
    val preDepartureAlertGate = PreDepartureAlertGate()
    // Resolving the client lazily keeps app start free of network setup.
    val cardErrorReportRepository: CardErrorReportRepository = BackendCardErrorReportRepository { backendApi }
}
