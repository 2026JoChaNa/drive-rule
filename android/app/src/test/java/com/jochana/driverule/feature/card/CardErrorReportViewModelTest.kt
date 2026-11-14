package com.jochana.driverule.feature.card

import com.jochana.driverule.domain.repository.CardErrorReportRepository
import com.jochana.driverule.domain.repository.CardReportOutcome
import com.jochana.driverule.domain.repository.CardReportTarget
import com.jochana.driverule.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardErrorReportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val target = CardReportTarget(cardId = 12, cardVersion = 2)

    @Test
    fun `sample card without a target never calls the repository`() = runTest {
        var calls = 0
        val model = CardErrorReportViewModel(null, CardErrorReportRepository { calls++; CardReportOutcome.Sent })

        model.report()
        advanceUntilIdle()

        assertEquals(CardReportState.Unavailable, model.state.value)
        assertEquals(0, calls)
    }

    @Test
    fun `report sends once and ignores taps while sending or after sent`() = runTest {
        val response = CompletableDeferred<CardReportOutcome>()
        val sent = mutableListOf<CardReportTarget>()
        val model = CardErrorReportViewModel(target, CardErrorReportRepository { sent += it; response.await() })

        model.report()
        advanceUntilIdle()
        assertEquals(CardReportState.Sending, model.state.value)
        model.report()
        response.complete(CardReportOutcome.Sent)
        advanceUntilIdle()
        model.report()
        advanceUntilIdle()

        assertEquals(CardReportState.Sent, model.state.value)
        assertEquals(listOf(target), sent)
    }

    @Test
    fun `failure can be retried but rejection cannot`() = runTest {
        val outcomes = ArrayDeque(listOf(CardReportOutcome.RetryLater, CardReportOutcome.Rejected))
        var calls = 0
        val model = CardErrorReportViewModel(target, CardErrorReportRepository { calls++; outcomes.removeFirst() })

        model.report()
        advanceUntilIdle()
        assertEquals(CardReportState.Failed, model.state.value)

        model.report()
        advanceUntilIdle()
        assertEquals(CardReportState.Rejected, model.state.value)

        model.report()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `unexpected repository exception becomes a retryable failure`() = runTest {
        val model = CardErrorReportViewModel(target, CardErrorReportRepository { error("boom") })

        model.report()
        advanceUntilIdle()

        assertEquals(CardReportState.Failed, model.state.value)
    }
}
