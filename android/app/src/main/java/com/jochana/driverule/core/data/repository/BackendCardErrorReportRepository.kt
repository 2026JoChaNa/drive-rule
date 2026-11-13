package com.jochana.driverule.core.data.repository

import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.core.data.remote.BackendResult
import com.jochana.driverule.domain.repository.CardErrorReportRepository
import com.jochana.driverule.domain.repository.CardReportOutcome
import com.jochana.driverule.domain.repository.CardReportTarget

/** Maps `POST /api/v1/cards/{cardId}/error-reports` results to the app API v1 handling table. */
class BackendCardErrorReportRepository(
    private val api: () -> BackendApiClient,
) : CardErrorReportRepository {

    override suspend fun report(target: CardReportTarget): CardReportOutcome =
        when (val result = api().reportCardError(target.cardId, target.cardVersion)) {
            is BackendResult.Success -> if (result.statusCode == ACCEPTED) {
                CardReportOutcome.Sent
            } else {
                CardReportOutcome.RetryLater
            }
            is BackendResult.HttpFailure -> if (result.statusCode in NOT_RETRYABLE) {
                CardReportOutcome.Rejected
            } else {
                CardReportOutcome.RetryLater
            }
            BackendResult.NetworkFailure,
            is BackendResult.MalformedResponse,
            is BackendResult.NotModified,
            -> CardReportOutcome.RetryLater
        }

    private companion object {
        const val ACCEPTED = 202
        val NOT_RETRYABLE = setOf(400, 404, 422)
    }
}
