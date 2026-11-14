package com.jochana.driverule.core.data.repository

import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.domain.repository.CardReportOutcome
import com.jochana.driverule.domain.repository.CardReportTarget
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Isolated loopback server only. These tests never reach the operating backend. */
class BackendCardErrorReportRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BackendApiClient
    private lateinit var repository: BackendCardErrorReportRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getLoopbackAddress(), 0)
        client = BackendApiClient.create(server.url("/").toString())
        repository = BackendCardErrorReportRepository { client }
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun `accepted report posts only the card version to the card path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202))

        val outcome = repository.report(CardReportTarget(cardId = 41, cardVersion = 3))

        assertEquals(CardReportOutcome.Sent, outcome)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/api/v1/cards/41/error-reports", request.path)
        assertEquals("""{"cardVersion":3}""", request.body.readUtf8())
    }

    @Test
    fun `contract errors are not retried`() = runBlocking {
        for (code in listOf(400, 404, 422)) {
            server.enqueue(problem(code))
            assertEquals("HTTP $code", CardReportOutcome.Rejected, repository.report(CardReportTarget(7, 1)))
        }
    }

    @Test
    fun `server errors and other success codes ask to retry later`() = runBlocking {
        server.enqueue(problem(503))
        assertEquals(CardReportOutcome.RetryLater, repository.report(CardReportTarget(7, 1)))
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(CardReportOutcome.RetryLater, repository.report(CardReportTarget(7, 1)))
    }

    @Test
    fun `transport failure asks to retry later`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(CardReportOutcome.RetryLater, repository.report(CardReportTarget(7, 1)))
    }

    private fun problem(code: Int) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"x","status":$code,"code":"X"}""")
}
