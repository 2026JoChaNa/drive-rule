package com.jochana.driverule.core.data.remote

/** HTTP and transport outcomes are not user-facing strings or legal-card approval states. */
sealed interface BackendResult<out T> {
    data class Success<T>(val value: T, val statusCode: Int, val etag: String? = null) : BackendResult<T>
    data class NotModified(val etag: String?) : BackendResult<Nothing>
    data class HttpFailure(val statusCode: Int, val problem: ProblemDetailsDto?) : BackendResult<Nothing>
    data object NetworkFailure : BackendResult<Nothing>
    data class MalformedResponse(val statusCode: Int) : BackendResult<Nothing>
}
