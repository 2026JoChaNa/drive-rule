package com.jochana.driverule.feature.predeparture

import androidx.lifecycle.ViewModelStore
import com.jochana.driverule.core.model.CardCatalog
import com.jochana.driverule.core.model.RuleCardSummary
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.repository.RuleCardRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreDepartureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unknown vehicle state hides available cards`() = runTest {
        val fixture = Fixture(CardCatalog.Available(cards), VehicleUxState.Unknown)
        try {
            observe(fixture.viewModel)
            runCurrent()

            assertEquals(
                PreDepartureUiState.VehicleStatusUnavailable,
                fixture.viewModel.uiState.value,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `restricted vehicle state hides available cards`() = runTest {
        val fixture = Fixture(CardCatalog.Available(cards), VehicleUxState.Restricted)
        try {
            observe(fixture.viewModel)
            runCurrent()

            assertEquals(
                PreDepartureUiState.DrivingRestricted,
                fixture.viewModel.uiState.value,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `unavailable catalog is distinct from successful empty catalog`() = runTest {
        val fixture = Fixture(CardCatalog.Unavailable, VehicleUxState.Unrestricted)
        try {
            observe(fixture.viewModel)
            runCurrent()
            assertEquals(
                PreDepartureUiState.CatalogUnavailable,
                fixture.viewModel.uiState.value,
            )

            fixture.repository.catalog.value = CardCatalog.Available(emptyList())
            runCurrent()
            assertEquals(PreDepartureUiState.Empty, fixture.viewModel.uiState.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `new restrictions remove previously visible cards`() = runTest {
        val fixture = Fixture(CardCatalog.Available(cards), VehicleUxState.Unrestricted)
        try {
            observe(fixture.viewModel)
            runCurrent()
            assertEquals(PreDepartureUiState.Ready(cards), fixture.viewModel.uiState.value)

            fixture.uxProvider.uxState.value = VehicleUxState.Restricted
            runCurrent()
            assertEquals(
                PreDepartureUiState.DrivingRestricted,
                fixture.viewModel.uiState.value,
            )

            fixture.uxProvider.uxState.value = VehicleUxState.Unrestricted
            runCurrent()
            assertEquals(PreDepartureUiState.Ready(cards), fixture.viewModel.uiState.value)

            fixture.uxProvider.uxState.value = VehicleUxState.Unknown
            runCurrent()
            assertEquals(
                PreDepartureUiState.VehicleStatusUnavailable,
                fixture.viewModel.uiState.value,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `stopped collection drops cached cards before resuming`() = runTest {
        val fixture = Fixture(CardCatalog.Available(cards), VehicleUxState.Unrestricted)
        try {
            val subscription = observe(fixture.viewModel)
            runCurrent()
            assertEquals(PreDepartureUiState.Ready(cards), fixture.viewModel.uiState.value)

            subscription.cancel()
            runCurrent()
            advanceTimeBy(5_001)
            runCurrent()
            assertEquals(PreDepartureUiState.Loading, fixture.viewModel.uiState.value)

            fixture.uxProvider.uxState.value = VehicleUxState.Unknown
            observe(fixture.viewModel)
            runCurrent()
            assertEquals(
                PreDepartureUiState.VehicleStatusUnavailable,
                fixture.viewModel.uiState.value,
            )
        } finally {
            fixture.close()
        }
    }

    private fun TestScope.observe(viewModel: PreDepartureViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

    private class Fixture(catalog: CardCatalog, uxState: VehicleUxState) {
        val repository = TestRuleCardRepository(catalog)
        val uxProvider = TestUxRestrictionProvider(uxState)
        val viewModel = PreDepartureViewModel(repository, uxProvider)
        private val store = ViewModelStore().apply { put("predeparture", viewModel) }

        fun close() {
            store.clear()
        }
    }

    private class TestRuleCardRepository(initialCatalog: CardCatalog) : RuleCardRepository {
        val catalog = MutableStateFlow(initialCatalog)

        override fun observeCatalog(): Flow<CardCatalog> = catalog.asStateFlow()
    }

    private class TestUxRestrictionProvider(initialState: VehicleUxState) : UxRestrictionProvider {
        val uxState = MutableStateFlow(initialState)
        override val state: StateFlow<VehicleUxState> = uxState.asStateFlow()
    }

    private companion object {
        val cards = listOf(RuleCardSummary(id = 1L, version = 1, title = "검증용 카드"))
    }
}
