package com.jochana.driverule.core.data.remote

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SchoolZoneDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BackendApiClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start(InetAddress.getLoopbackAddress(), 0) }
        client = BackendApiClient.create(server.url("/backend/").toString())
    }

    @After
    fun tearDown() { client.close(); server.shutdown() }

    @Test
    fun `relative file references resolve from manifest document not base directory`() = runBlocking {
        server.enqueue(MockResponse().setBody("fixture"))
        val result = client.downloadSchoolZoneFile("school-zones/abc.json") as BackendResult.Success
        assertEquals("fixture", result.value.decodeToString())
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/backend/api/v1/geo/school-zones/abc.json", request.path)
        assertEquals("GET", request.method)
        assertNull(request.getHeader("GeoReadLimit"))
    }

    @Test
    fun `download redirects are surfaced without following Location`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/geo.json"))
        val result = client.downloadSchoolZoneFile("/redirect") as BackendResult.HttpFailure
        assertEquals(302, result.statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalid URL is rejected before sending any request`() = runBlocking {
        listOf("http://example.invalid/", "file:///tmp/school.json", "/file.json#", "\\\\evil\\file.json",
            " /file.json", "/file\n.json").forEach { value ->
            assertNull(client.resolveSchoolZoneFileUrl(value))
            assertEquals(BackendResult.MalformedResponse(0), client.downloadSchoolZoneFile(value))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `success and error decompressed bodies are capped before Retrofit buffering`() = runBlocking {
        val oversized = ByteArray(2 * 1_024 * 1_024 + 1) { 'a'.code.toByte() }
        listOf(200, 500).forEach { status ->
            val compressed = Buffer()
            GzipSink(compressed).buffer().use { it.write(oversized) }
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Encoding", "gzip").setBody(compressed))
            assertEquals(BackendResult.MalformedResponse(status), client.downloadSchoolZoneFile("/bounded.json"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `manifest body has its own smaller bound`() = runBlocking {
        server.enqueue(MockResponse().setBody(" ".repeat(64 * 1_024 + 1)))
        assertTrue(client.schoolZoneManifest() is BackendResult.MalformedResponse)
        assertEquals(1, server.requestCount)
    }
}
