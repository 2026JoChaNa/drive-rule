package com.jochana.driverule.core.data.repository

import com.jochana.driverule.core.model.CardCatalog
import com.jochana.driverule.domain.repository.RuleCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Keeps missing integration distinct from a successfully loaded empty catalog. */
class UnconfiguredRuleCardRepository : RuleCardRepository {
    override fun observeCatalog(): Flow<CardCatalog> = flowOf(CardCatalog.Unavailable)
}
