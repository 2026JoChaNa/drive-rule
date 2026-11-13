package com.jochana.driverule.feature.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jochana.driverule.domain.repository.CardErrorReportRepository
import com.jochana.driverule.domain.repository.CardReportOutcome
import com.jochana.driverule.domain.repository.CardReportTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CardReportState { Unavailable, Idle, Sending, Sent, Rejected, Failed }

/**
 * One report per opened card. Only an explicit tap sends; the auto-close timer, back
 * navigation and recomposition never do.
 */
class CardErrorReportViewModel(
    private val target: CardReportTarget?,
    private val repository: CardErrorReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(if (target == null) CardReportState.Unavailable else CardReportState.Idle)
    val state: StateFlow<CardReportState> = _state.asStateFlow()

    fun report() {
        val card = target ?: return
        if (_state.value !in RETRYABLE_STATES) return
        _state.value = CardReportState.Sending
        viewModelScope.launch {
            _state.value = try {
                when (repository.report(card)) {
                    CardReportOutcome.Sent -> CardReportState.Sent
                    CardReportOutcome.Rejected -> CardReportState.Rejected
                    CardReportOutcome.RetryLater -> CardReportState.Failed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CardReportState.Failed
            }
        }
    }

    private companion object {
        val RETRYABLE_STATES = setOf(CardReportState.Idle, CardReportState.Failed)
    }
}
