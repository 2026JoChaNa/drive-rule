package com.jochana.driverule.core.data.repository

import com.jochana.driverule.core.data.local.geo.SchoolZoneStorage
import com.jochana.driverule.core.data.local.geo.SchoolZoneValidator
import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.core.data.remote.SchoolZoneManifestDto
import com.jochana.driverule.core.model.SchoolZoneLoadState
import com.jochana.driverule.core.model.SchoolZoneSyncFailure
import com.jochana.driverule.core.model.SchoolZoneSyncResult
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Loopback only. Fixtures contain public example points and never a device location. */
class SchoolZoneRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: BackendApiClient
    private lateinit var storage: MemoryStorage
    private lateinit var repository: SchoolZoneRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start(InetAddress.getLoopbackAddress(), 0) }
        backend = BackendApiClient.create(server.url("/").toString())
        storage = MemoryStorage()
        repository = SchoolZoneRepository(backend, storage)
    }

    @After
    fun tearDown() { backend.close(); server.shutdown() }

    @Test
    fun `first sync commits snapshot and restart validates bytes before conditional 304`() = runBlocking {
        assertSame(SchoolZoneLoadState.Loading, repository.state.value)
        val bytes = file()
        enqueueDataset(bytes)
        assertTrue(repository.sync() is SchoolZoneSyncResult.Updated)
        val initial = repository.read()
        assertNotNull(initial)
        assertEquals(SchoolZoneLoadState.Ready(requireNotNull(initial)), repository.state.value)
        takeRequest().also { assertEquals("/api/v1/geo/school-zones", it.path); assertNull(it.getHeader("If-None-Match")) }
        takeRequest().also { assertEquals("/api/v1/geo/school-zones/${SchoolZoneValidator.sha256(bytes)}.json", it.path) }

        val restored = SchoolZoneRepository(backend, storage)
        assertEquals(initial, restored.read())
        server.enqueue(MockResponse().setResponseCode(304))
        assertSame(SchoolZoneSyncResult.Unchanged, restored.sync())
        assertEquals("\"manifest-v1\"", takeRequest().getHeader("If-None-Match"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `same hash skips file but updates etag and validates metadata`() = runBlocking {
        val bytes = file()
        enqueueDataset(bytes)
        repository.sync()
        server.enqueue(manifestResponse(manifest(bytes).copy(url = "school-zones/relocated.json"), "\"v2\""))
        assertSame(SchoolZoneSyncResult.Unchanged, repository.sync())
        assertEquals(3, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(304))
        assertSame(SchoolZoneSyncResult.Unchanged, SchoolZoneRepository(backend, storage).sync())
        repeat(3) { takeRequest() }
        assertEquals("\"v2\"", takeRequest().getHeader("If-None-Match"))
        val saved = repository.read()
        server.enqueue(manifestResponse(manifest(bytes).copy(count = 8)))
        assertEquals(SchoolZoneSyncFailure.InvalidData, (repository.sync() as SchoolZoneSyncResult.Failure).reason)
        assertEquals(saved, repository.read())
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `gzip hash is calculated from exact decompressed bytes`() = runBlocking {
        val bytes = file()
        server.enqueue(manifestResponse(manifest(bytes).copy(url = "school-zones/current.json")))
        server.enqueue(gzip(bytes))
        assertTrue(repository.sync() is SchoolZoneSyncResult.Updated)
        assertEquals(SchoolZoneValidator.sha256(bytes), repository.read()?.sha256)
        takeRequest()
        assertEquals("/api/v1/geo/school-zones/current.json", takeRequest().path)
    }

    @Test
    fun `invalid hash malformed file and oversized decompressed response preserve old snapshot`() = runBlocking {
        enqueueDataset(file())
        repository.sync()
        val previous = repository.read()
        val next = file("2026-10-28")
        server.enqueue(manifestResponse(manifest(next, "2026-10-28")))
        server.enqueue(MockResponse().setBody("wrong bytes"))
        assertFalse((repository.sync() as SchoolZoneSyncResult.Failure).retryable)
        assertEquals(previous, repository.read())

        server.enqueue(manifestResponse(manifest(next, "2026-10-28")))
        server.enqueue(gzip(ByteArray(SchoolZoneValidator.MAX_FILE_BYTES + 1) { 'x'.code.toByte() }))
        assertEquals(SchoolZoneSyncFailure.InvalidData, (repository.sync() as SchoolZoneSyncResult.Failure).reason)
        assertEquals(previous, SchoolZoneRepository(backend, storage).read())
    }

    @Test
    fun `invalid download origins credentials fragments schemes and oversized manifest never fetch a file`() = runBlocking {
        val valid = manifest(file())
        val origin = server.url("/")
        listOf("https://example.invalid/file.json", "//example.invalid/file.json", "file:///tmp/geo.json",
            "$origin#fragment", origin.newBuilder().username("user").password("secret").build().toString(),
            origin.newBuilder().port(origin.port + 1).build().toString(), "javascript:alert(1)").forEach { url ->
            server.enqueue(manifestResponse(valid.copy(url = url)))
            assertEquals(SchoolZoneSyncFailure.InvalidData, (repository.sync() as SchoolZoneSyncResult.Failure).reason)
            assertEquals("/api/v1/geo/school-zones", takeRequest().path)
        }
        server.enqueue(manifestResponse(valid.copy(bytes = SchoolZoneValidator.MAX_FILE_BYTES + 1L)))
        assertTrue(repository.sync() is SchoolZoneSyncResult.Failure)
        assertEquals(8, server.requestCount)
        assertNull(repository.read())
    }

    @Test
    fun `file 404 refetches manifest unconditionally once and installs the current file`() = runBlocking {
        val stale = file()
        val current = file("2026-10-28")
        server.enqueue(manifestResponse(manifest(stale)))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"DATASET_VERSION_NOT_FOUND"}"""))
        server.enqueue(manifestResponse(manifest(current, "2026-10-28"), "\"current\""))
        server.enqueue(MockResponse().setBody(Buffer().write(current)))
        assertTrue(repository.sync() is SchoolZoneSyncResult.Updated)
        assertEquals("2026-10-28", repository.read()?.datasetVersion)
        takeRequest(); takeRequest()
        assertNull(takeRequest().getHeader("If-None-Match"))
        takeRequest()
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `repeated file 404 and cacheless 304 recovery are bounded`() = runBlocking {
        repeat(2) {
            server.enqueue(manifestResponse(manifest(file())))
            server.enqueue(MockResponse().setResponseCode(404))
        }
        assertEquals(SchoolZoneSyncFailure.Http, (repository.sync() as SchoolZoneSyncResult.Failure).reason)
        assertEquals(4, server.requestCount)
        repeat(2) { server.enqueue(MockResponse().setResponseCode(304)) }
        assertEquals(SchoolZoneSyncFailure.InvalidData, (repository.sync() as SchoolZoneSyncResult.Failure).reason)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `corrupt persisted envelope cannot establish readiness and valid sync repairs it`() = runBlocking {
        storage.bytes = "{broken".encodeToByteArray()
        assertNull(repository.read())
        assertSame(SchoolZoneLoadState.Unavailable, repository.state.value)
        enqueueDataset(file())
        assertTrue(repository.sync() is SchoolZoneSyncResult.Updated)
        assertNotNull(SchoolZoneRepository(backend, storage).read())
        storage.bytes = requireNotNull(storage.bytes).decodeToString()
            .replace("\"sha256\":\"", "\"sha256\":\"f").encodeToByteArray()
        assertNull(SchoolZoneRepository(backend, storage).read())
    }

    @Test
    fun `failed atomic commit never publishes new data or loses prior snapshot`() = runBlocking {
        enqueueDataset(file())
        repository.sync()
        val previous = repository.read()
        val saved = storage.bytes?.copyOf()
        storage.beforeWrite = { assertEquals(previous, (repository.state.value as SchoolZoneLoadState.Ready).snapshot) }
        storage.failWrite = true
        enqueueDataset(file("2026-10-28"), "2026-10-28")
        assertEquals(SchoolZoneSyncFailure.Storage, (repository.sync() as SchoolZoneSyncResult.Failure).reason)
        assertEquals(previous, repository.read())
        assertTrue(requireNotNull(saved).contentEquals(storage.bytes))
        assertEquals(previous, SchoolZoneRepository(backend, storage).read())
    }

    @Test
    fun `concurrent syncs serialize and second request uses committed etag`() = runBlocking {
        enqueueDataset(file())
        server.enqueue(MockResponse().setResponseCode(304))
        val results = listOf(async(Dispatchers.IO) { repository.sync() }, async(Dispatchers.IO) { repository.sync() })
            .map { it.await() }
        assertEquals(1, results.count { it is SchoolZoneSyncResult.Updated })
        assertEquals(1, results.count { it == SchoolZoneSyncResult.Unchanged })
        takeRequest(); takeRequest()
        assertEquals("\"manifest-v1\"", takeRequest().getHeader("If-None-Match"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `server failures preserve cache and cancellation propagates without clearing readiness`() = runBlocking {
        enqueueDataset(file())
        repository.sync()
        val previous = repository.read()
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue((repository.sync() as SchoolZoneSyncResult.Failure).retryable)
        assertEquals(previous, repository.read())
        repeat(3) { takeRequest() }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { repository.sync() }
        takeRequest()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertEquals(previous, repository.read())
    }

    @Test
    fun `pending download never blocks reads of an already committed snapshot`() = runBlocking {
        enqueueDataset(file())
        repository.sync()
        val previous = repository.read()
        takeRequest(); takeRequest()
        val next = file("2026-10-28")
        server.enqueue(manifestResponse(manifest(next, "2026-10-28")))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { repository.sync() }
        takeRequest(); takeRequest()
        try {
            assertEquals(previous, withTimeout(1_000) { repository.read() })
            assertEquals(previous, (repository.state.value as SchoolZoneLoadState.Ready).snapshot)
        } finally { pending.cancelAndJoin() }
    }

    private fun file(date: String = "2026-10-27") =
        """{"schema":1,"dataset":"school-zones","source":"public fixture","referenceDate":"$date","count":2,"zones":[[37.5,127.0],[33.2,126.2]]}""".encodeToByteArray()

    private fun manifest(bytes: ByteArray, date: String = "2026-10-27") = SchoolZoneManifestDto(
        "school-zones", 1, date, 2, SchoolZoneValidator.sha256(bytes), bytes.size.toLong(),
        "/api/v1/geo/school-zones/${SchoolZoneValidator.sha256(bytes)}.json",
    )

    private fun manifestResponse(value: SchoolZoneManifestDto, etag: String = "\"manifest-v1\"") =
        MockResponse().setHeader("Content-Type", "application/json").setHeader("ETag", etag).setBody(Json.encodeToString(value))

    private fun enqueueDataset(bytes: ByteArray, date: String = "2026-10-27") {
        server.enqueue(manifestResponse(manifest(bytes, date)))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
    }

    private fun gzip(bytes: ByteArray): MockResponse {
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { it.write(bytes) }
        return MockResponse().setHeader("Content-Encoding", "gzip").setBody(compressed)
    }

    private fun takeRequest() = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).also {
        assertEquals("GET", it.method)
        assertEquals(0L, it.bodySize)
        assertNull(it.getHeader("Authorization"))
    }

    private class MemoryStorage : SchoolZoneStorage {
        var bytes: ByteArray? = null
        var failWrite = false
        var beforeWrite: (() -> Unit)? = null
        override fun read(): ByteArray? = bytes?.copyOf()
        override fun write(bytes: ByteArray) {
            beforeWrite?.invoke()
            if (failWrite) throw IOException("Simulated atomic write failure")
            this.bytes = bytes.copyOf()
        }
    }
}
