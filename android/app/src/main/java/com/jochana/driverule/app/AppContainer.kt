package com.jochana.driverule.app

import com.jochana.driverule.core.data.repository.UnconfiguredRuleCardRepository
import com.jochana.driverule.core.platform.vehicle.UnavailableUxRestrictionProvider
import com.jochana.driverule.domain.repository.RuleCardRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider

/** Owns application-scoped dependencies until concrete integrations are available. */
class AppContainer {
    val ruleCardRepository: RuleCardRepository = UnconfiguredRuleCardRepository()
    val uxRestrictionProvider: UxRestrictionProvider = UnavailableUxRestrictionProvider()
}
