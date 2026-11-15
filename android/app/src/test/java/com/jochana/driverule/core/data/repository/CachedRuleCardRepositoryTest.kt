package com.jochana.driverule.core.data.repository

import com.jochana.driverule.core.data.local.law.CacheApplyResult
import com.jochana.driverule.core.data.local.law.InvalidCardSyncException
import com.jochana.driverule.core.data.local.law.LawCardCache
import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.core.data.remote.BackendResult
import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.data.remote.ProblemDetailsDto
import com.jochana.driverule.core.model.CardCatalog
import com.jochana.driverule.core.model.CachedAudience
import com.jochana.driverule.core.model.CachedCardType
import com.jochana.driverule.core.model.CachedChangeKind
import com.jochana.driverule.core.model.CachedLawCard
import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.core.model.LawSyncFailure
import com.jochana.driverule.core.model.LawSyncOutcome
import com.jochana.driverule.core.model.LawSyncState
import com.jochana.driverule.core.model.RuleCardSummary
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachedRuleCardRepositoryTest {
    @Test fun `limited audience cards stay cached but do not enter the general departure catalog`() = runTest {
        val cache = FakeCache("baseline")
        cache.value.value = cache.value.value.copy(cards = listOf(
            card(1, CachedAudience.GENERAL), card(2, CachedAudience.LIMITED),
        ))
        val repository = CachedRuleCardRepository(cache, { error("A local read must not fetch") })
        assertEquals(CardCatalog.Available(listOf(RuleCardSummary(1, 1, "test fixture"))),
            repository.observeCatalog().first())
        assertEquals(2, repository.observeCache().first().cards.size)
    }

    @Test fun `first successful empty response differs from no cache`() = runTest {
        val cache = FakeCache()
        val repository = CachedRuleCardRepository(cache, { success("first", full = true) }, { 1234 })
        assertEquals(CardCatalog.Unavailable, repository.observeCatalog().first())
        assertEquals(LawSyncOutcome.Success(0, 0, 0), repository.synchronize())
        assertEquals(CardCatalog.Available(emptyList()), repository.observeCatalog().first())
        assertEquals(1234L, cache.value.value.lastSuccessfulSyncAtMillis)
    }

    @Test fun `expired cursor retries without token and only commits recovered response`() = runTest {
        val cache = FakeCache("expired")
        val tokens = mutableListOf<String?>()
        val repository = CachedRuleCardRepository(cache, { token ->
            tokens += token
            if (token != null) expired() else success("recovered", full = true)
        }, { 2345 })
        assertTrue(repository.synchronize() is LawSyncOutcome.Success)
        assertEquals(listOf("expired", null), tokens)
        assertEquals(listOf("recovered"), cache.committed)
    }

    @Test fun `failed recovery and repeated 410 preserve cursor and cache`() = runTest {
        for (failure in listOf(BackendResult.NetworkFailure, expired())) {
            val cache = FakeCache("old")
            var calls = 0
            val repository = CachedRuleCardRepository(cache, {
                calls++
                if (calls == 1) expired() else failure
            })
            assertTrue(repository.synchronize() is LawSyncOutcome.Failure)
            assertEquals(2, calls)
            assertEquals("old", cache.value.value.syncToken)
            assertTrue(cache.committed.isEmpty())
        }
    }

    @Test fun `other 410 codes do not cause tokenless retries`() = runTest {
        var calls = 0
        val repository = CachedRuleCardRepository(FakeCache("old"), {
            calls++
            BackendResult.HttpFailure(410, ProblemDetailsDto(code = "OTHER_REASON"))
        })
        assertEquals(LawSyncOutcome.Failure(LawSyncFailure.Server, false), repository.synchronize())
        assertEquals(1, calls)
    }

    @Test fun `delta without a baseline is rejected without committing`() = runTest {
        val cache = FakeCache()
        val repository = CachedRuleCardRepository(cache, { success("unexpected", full = false) })
        assertEquals(LawSyncOutcome.Failure(LawSyncFailure.InvalidResponse, false), repository.synchronize())
        assertTrue(cache.committed.isEmpty())
    }

    @Test fun `offline and malformed responses leave a successful local baseline readable`() = runTest {
        for (failure in listOf(BackendResult.NetworkFailure, BackendResult.MalformedResponse(200), BackendResult.NotModified(null))) {
            val cache = FakeCache("baseline")
            val repository = CachedRuleCardRepository(cache, { failure })
            assertTrue(repository.synchronize() is LawSyncOutcome.Failure)
            assertEquals(CardCatalog.Available(emptyList()), repository.observeCatalog().first())
            assertEquals("baseline", cache.value.value.syncToken)
            assertEquals(1L, cache.value.value.lastSuccessfulSyncAtMillis)
        }
    }

    @Test fun `storage failure preserves token and allows a later retry`() = runTest {
        val cache = FakeCache("old")
        cache.applyError = IOException("synthetic storage failure")
        val repository = CachedRuleCardRepository(cache, { success("next", false) })
        assertEquals(LawSyncOutcome.Failure(LawSyncFailure.Storage, true), repository.synchronize())
        assertEquals("old", cache.value.value.syncToken)
        cache.applyError = null
        assertTrue(repository.synchronize() is LawSyncOutcome.Success)
        assertEquals("next", cache.value.value.syncToken)
    }

    @Test fun `invalid sync is not retried as a database error`() = runTest {
        val cache = FakeCache("old")
        cache.applyError = InvalidCardSyncException("synthetic invalid response")
        val repository = CachedRuleCardRepository(cache, { success("next", false) })
        assertEquals(LawSyncOutcome.Failure(LawSyncFailure.InvalidResponse, false), repository.synchronize())
        assertEquals("old", cache.value.value.syncToken)
    }

    @Test fun `overlapping commands use the cursor from the preceding commit`() = runTest {
        val cache = FakeCache("one")
        val releaseFirst = CompletableDeferred<Unit>()
        val tokens = mutableListOf<String?>()
        val repository = CachedRuleCardRepository(cache, { token ->
            tokens += token
            if (token == "one") { releaseFirst.await(); success("two", false) }
            else success("three", false)
        })
        val first = async { repository.synchronize() }
        runCurrent()
        val second = async { repository.synchronize() }
        runCurrent()
        assertEquals(listOf("one"), tokens)
        releaseFirst.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("one", "two"), tokens)
        assertEquals("three", cache.value.value.syncToken)
    }

    @Test fun `cancellation releases the lock and does not leave running state`() = runTest {
        val cache = FakeCache("old")
        var cancelFirst = true
        val repository = CachedRuleCardRepository(cache, {
            if (cancelFirst) awaitCancellation() else success("next", false)
        })
        val running = launch { repository.synchronize() }
        runCurrent()
        assertEquals(LawSyncState.Running, repository.syncState.value)
        running.cancelAndJoin()
        assertEquals(LawSyncState.Idle, repository.syncState.value)
        assertEquals("old", cache.value.value.syncToken)
        cancelFirst = false
        assertTrue(repository.synchronize() is LawSyncOutcome.Success)
    }

    @Test fun `retry classification is bounded to transient transport and server failures`() = runTest {
        for ((status, retry) in listOf(400 to false, 401 to false, 404 to false, 408 to false, 429 to false, 500 to true, 503 to true)) {
            val repository = CachedRuleCardRepository(FakeCache(), { BackendResult.HttpFailure(status, null) })
            assertEquals(LawSyncOutcome.Failure(LawSyncFailure.Server, retry), repository.synchronize())
        }
    }

    @Test fun `http integration sends only an opaque cursor and no driver identifiers`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/problem+json")
                .setResponseCode(410).setBody("""{"code":"SYNC_TOKEN_EXPIRED"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"syncToken":"next","fullSync":true,"cards":[],"retiredCardIds":[],"conditions":[]}"""))
            BackendApiClient.create(server.url("/").toString()).use { client ->
                val repository = CachedRuleCardRepository(FakeCache("opaque+/="), client::syncCards)
                assertTrue(repository.synchronize() is LawSyncOutcome.Success)
                val delta = server.takeRequest()
                assertEquals("GET", delta.method)
                assertEquals("/api/v1/cards/sync", delta.requestUrl!!.encodedPath)
                assertEquals(setOf("syncToken"), delta.requestUrl!!.queryParameterNames)
                assertEquals("opaque+/=", delta.requestUrl!!.queryParameter("syncToken"))
                assertEquals(0L, delta.bodySize)
                val full = server.takeRequest()
                assertNull(full.requestUrl!!.query)
                assertEquals(0L, full.bodySize)
            }
        }
    }

    private class FakeCache(token: String? = null) : LawCardCache {
        val value = MutableStateFlow(LawCacheSnapshot(syncToken = token,
            lastSuccessfulSyncAtMillis = token?.let { 1L }))
        val committed = mutableListOf<String>()
        var applyError: Exception? = null
        override fun observe() = value
        override suspend fun read() = value.value
        override suspend fun apply(response: CardSyncDto, syncedAtEpochMs: Long): CacheApplyResult {
            applyError?.let { throw it }
            value.value = value.value.copy(syncToken = response.syncToken, lastSuccessfulSyncAtMillis = syncedAtEpochMs)
            committed += response.syncToken
            return CacheApplyResult(0, 0, 0)
        }
    }

    private fun success(token: String, full: Boolean) = BackendResult.Success(
        CardSyncDto(token, full, emptyList(), emptyList(), emptyList()), 200,
    )
    private fun expired() = BackendResult.HttpFailure(410, ProblemDetailsDto(code = "SYNC_TOKEN_EXPIRED"))

    private fun card(id: Long, audience: CachedAudience) = CachedLawCard(
        id = id, version = 1, cardType = CachedCardType.SHORT, changeKind = CachedChangeKind.LAW,
        audience = audience, eventTag = null, sourceOrg = null, topic = null, title = "test fixture",
        dontText = null, doText = null, sceneKey = null, ttsText = "synthetic test speech",
        legalBasis = "synthetic basis", lawExcerpt = "synthetic excerpt", penaltyText = null,
        lawName = null, lawNo = null, promulgatedOn = null, effectiveOn = null,
        exceptions = null, decisionJson = null, verifiedAtMillis = 1,
    )
}
