package com.jochana.driverule.core.data.remote

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Every request uses an isolated loopback server. Never send reports to a real backend. */
class BackendApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BackendApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getLoopbackAddress(), 0)
        client = BackendApiClient.create(server.url("/").toString())
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun `construction does not make a request and full sync omits the token`() = runBlocking {
        assertEquals(0, server.requestCount)
        server.enqueue(jsonResponse(emptySync))
        val result = client.syncCards()

        assertTrue(result is BackendResult.Success)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/api/v1/cards/sync", request.path)
        assertEquals("gzip", request.getHeader("Accept-Encoding"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `opaque sync token is encoded once and response token is preserved`() = runBlocking {
        val token = "opaque+/= %&?#한글"
        server.enqueue(jsonResponse(emptySync))
        val result = client.syncCards(token) as BackendResult.Success
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        assertEquals("/api/v1/cards/sync", request.requestUrl?.encodedPath)
        assertEquals(token, request.requestUrl?.queryParameter("syncToken"))
        assertEquals(setOf("syncToken"), request.requestUrl?.queryParameterNames)
        assertEquals("next+/=", result.value.syncToken)
        assertEquals(200, result.statusCode)
    }

    @Test
    fun `unknown fields enums and future decision shapes do not discard the sync response`() = runBlocking {
        server.enqueue(jsonResponse("""
            {
              "syncToken":"next","fullSync":false,"futureEnvelope":true,
              "cards":[{
                "cardId":8,"version":2,"cardType":"FUTURE_CARD","changeKind":"FUTURE_CHANGE",
                "audience":"FUTURE_AUDIENCE","eventTag":"FUTURE_EVENT","title":"transport fixture",
                "ttsText":"fixture only","legalBasis":"fixture","lawExcerpt":"fixture",
                "effectiveOn":null,"verifiedAt":"2026-11-13T09:00:00+09:00",
                "decision":{"schema":99,"newShape":[]},"futureCardField":"ignored"
              }],
              "retiredCardIds":[3],
              "conditions":[{"key":"new_condition","question":"fixture?",
                "options":[{"key":"new_option","label":"fixture","futureOption":true}]}]
            }
        """.trimIndent()))
        val result = client.syncCards() as BackendResult.Success
        val card = result.value.cards.single()

        assertEquals("FUTURE_CARD", card.cardType)
        assertEquals("FUTURE_EVENT", card.eventTag)
        assertEquals("99", card.decision?.get("schema")?.jsonPrimitive?.content)
        assertNull(card.effectiveOn)
        assertEquals(listOf(3L), result.value.retiredCardIds)
        assertEquals("new_condition", result.value.conditions.single().key)
        assertEquals("new_option", result.value.conditions.single().options.single().key)
    }

    @Test
    fun `manifest preserves etag and url without downloading the dataset`() = runBlocking {
        server.enqueue(jsonResponse(manifest).setHeader("ETag", "\"dataset-v1\""))
        val result = client.schoolZoneManifest() as BackendResult.Success
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        assertEquals("/api/v1/geo/school-zones", request.path)
        assertNull(request.getHeader("If-None-Match"))
        assertEquals("\"dataset-v1\"", result.etag)
        assertEquals("/api/v1/geo/school-zones/fixture.json", result.value.url)
        assertEquals(313438L, result.value.bytes)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `manifest 304 preserves conditional request and does not need a body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"dataset-v1\""))
        val result = client.schoolZoneManifest("\"dataset-v1\"")

        assertEquals(BackendResult.NotModified("\"dataset-v1\""), result)
        assertEquals("\"dataset-v1\"", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("If-None-Match"))
    }

    @Test
    fun `card report sends only card version and accepts empty 202`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202))
        val result = client.reportCardError(87, 4)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        assertEquals(BackendResult.Success(Unit, 202), result)
        assertEquals("POST", request.method)
        assertEquals("/api/v1/cards/87/error-reports", request.path)
        assertTrue(request.getHeader("Content-Type")?.startsWith("application/json") == true)
        assertEquals(Json.parseToJsonElement("""{"cardVersion":4}"""), Json.parseToJsonElement(request.body.readUtf8()))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `answer hold serializes only topic and known conditions and accepts empty 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = client.reportAnswerHold("right_turn", mapOf("crosswalk_light" to "red"))
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        assertEquals(BackendResult.Success(Unit, 204), result)
        assertEquals("/api/v1/answer-holds", request.path)
        assertEquals(setOf("topic", "conditions"), body.keys)
        assertEquals("right_turn", body["topic"]?.jsonPrimitive?.content)
        assertEquals("red", body["conditions"]?.jsonObject?.get("crosswalk_light")?.jsonPrimitive?.content)
    }

    @Test
    fun `unanswered conditions are omitted rather than sent as fabricated values`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202))
        assertEquals(BackendResult.Success(Unit, 202), client.reportAnswerHold("other"))
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(setOf("topic"), Json.parseToJsonElement(request.body.readUtf8()).jsonObject.keys)
    }

    @Test
    fun `problem details retain actual status and machine code without automatically resetting token`() = runBlocking {
        server.enqueue(jsonResponse("""
            {"title":"Gone","status":410,"code":"SYNC_TOKEN_EXPIRED","detail":"fixture",
             "instance":"/api/v1/cards/sync","errors":[],"futureProblemField":true}
        """.trimIndent(), 410).setHeader("Content-Type", "application/problem+json"))
        val result = client.syncCards("expired") as BackendResult.HttpFailure

        assertEquals(410, result.statusCode)
        assertEquals("SYNC_TOKEN_EXPIRED", result.problem?.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `report problem conditions are parsed and unknown problem codes remain usable`() = runBlocking {
        server.enqueue(jsonResponse("""
            {"status":422,"code":"FUTURE_PROBLEM","errors":["conditions.fixture: unknown"]}
        """.trimIndent(), 422))
        val result = client.reportAnswerHold("other") as BackendResult.HttpFailure

        assertEquals(422, result.statusCode)
        assertEquals("FUTURE_PROBLEM", result.problem?.code)
        assertEquals(listOf("conditions.fixture: unknown"), result.problem?.errors)
    }

    @Test
    fun `invalid success JSON missing required fields and missing GET body are malformed`() = runBlocking {
        server.enqueue(jsonResponse("{invalid"))
        assertEquals(BackendResult.MalformedResponse(200), client.syncCards())
        server.enqueue(jsonResponse("{}"))
        assertEquals(BackendResult.MalformedResponse(200), client.syncCards())
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(BackendResult.MalformedResponse(204), client.schoolZoneManifest())
    }

    @Test
    fun `non JSON error still retains the HTTP status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>upstream failure</html>"))
        assertEquals(BackendResult.HttpFailure(502, null), client.syncCards())
    }

    @Test
    fun `disconnect is a network failure with no client retry loop`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertSame(BackendResult.NetworkFailure, client.syncCards())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cancelling an in flight request propagates coroutine cancellation`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async(Dispatchers.Default) { client.syncCards() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        withTimeout(3_000) { request.cancelAndJoin() }
        assertTrue(request.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `POST redirect is surfaced without following it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/must-not-run")))
        server.enqueue(MockResponse().setResponseCode(202))
        assertEquals(BackendResult.HttpFailure(307, null), client.reportCardError(1, 1))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `POST 503 retry after zero cannot silently duplicate a report`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(202))
        assertEquals(BackendResult.HttpFailure(503, null), client.reportAnswerHold("other"))
        assertEquals(1, server.requestCount)
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    companion object {
        private const val emptySync = """{"syncToken":"next+/=","fullSync":true,"cards":[],"retiredCardIds":[],"conditions":[]}"""
        private const val manifest = """{"dataset":"school-zones","schema":1,"referenceDate":"2026-10-27","count":12152,"sha256":"fixture","bytes":313438,"url":"/api/v1/geo/school-zones/fixture.json"}"""
    }
}
