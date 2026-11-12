package com.jochana.driverule.domain.repository

import com.jochana.driverule.core.model.CardCatalog
import kotlinx.coroutines.flow.Flow

interface RuleCardRepository {
    fun observeCatalog(): Flow<CardCatalog>
}
