package com.jochana.driverule.core.data.local.law

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.data.remote.ConditionDefinitionDto
import com.jochana.driverule.core.data.remote.ConditionOptionDto
import com.jochana.driverule.core.data.remote.SyncedCardDto
import com.jochana.driverule.core.model.LawCacheSnapshot
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLawCardCacheTest {
    private lateinit var context: Context
    private lateinit var database: LawCardDatabase
    private lateinit var cache: RoomLawCardCache

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LawCardDatabase::class.java).build()
        cache = RoomLawCardCache(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshAndSuccessfullyEmptyCacheAreDistinguishable() = runBlocking {
        assertEquals(LawCacheSnapshot(), cache.read())
        assertEquals(cache.read(), cache.observe().first())
        cache.apply(response("empty"), 100)
        assertEquals(LawCacheSnapshot("empty", 100), cache.read())
        assertEquals(cache.read(), cache.observe().first())
    }

    @Test
    fun allFieldsAndSupportMarkersSurviveDatabaseReopening() = runBlocking {
        val name = "law-cache-test-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, LawCardDatabase::class.java, name).build()
        try {
            var stored = RoomLawCardCache(disk)
            stored.apply(response("first", listOf(card(1), card(2))), 100)
            stored.apply(response("second", listOf(card(1, 2).copy(cardType = "FUTURE"))), 200)
            val expected = stored.read()
            disk.close()
            disk = Room.databaseBuilder(context, LawCardDatabase::class.java, name).build()
            stored = RoomLawCardCache(disk)
            assertEquals(expected, stored.read())
            val supported = disk.lawCardDao().read().single().cards.first { it.id == 2L }
            assertEquals(expected.cards.single().verifiedAtMillis, supported.verifiedAtMillis)
            stored.apply(response("third", listOf(card(1, 1))), 300)
            assertEquals(listOf(2L), stored.read().cards.map { it.id })
            assertFalse(disk.lawCardDao().read().single().cards.first { it.id == 1L }.isRetired)
        } finally {
            disk.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun metadataWriteFailureRollsBackCardsRetirementsConditionsTokenAndTime() = runBlocking {
        cache.apply(response("before", listOf(card(1)), listOf(condition("before"))), 100)
        val before = cache.read()
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_law_state_update BEFORE UPDATE ON law_cache_state
                BEGIN SELECT RAISE(ABORT, 'Injected law cache storage failure'); END""".trimIndent(),
        )
        val failure = runCatching {
            cache.apply(response("after", listOf(card(2)), listOf(condition("after"))).copy(retiredCardIds = listOf(1)), 200)
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(before, cache.read())
        assertEquals(before, cache.observe().first())
        assertEquals(1, database.lawCardDao().read().single().cards.size)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_law_state_update")
        cache.apply(response("retry", listOf(card(2))), 300)
        assertEquals(listOf(1L, 2L), cache.read().cards.map { it.id })
    }

    @Test
    fun invalidConditionDictionaryPreservesCommittedSnapshot() = runBlocking {
        cache.apply(response("before", listOf(card(1))), 100)
        val before = cache.read()
        val result = runCatching {
            cache.apply(response("invalid", listOf(card(2)), listOf(condition("a"), condition("b"))), 200)
        }
        assertTrue(result.exceptionOrNull() is InvalidCardSyncException)
        assertEquals(before, cache.read())
    }

    @Test
    fun retirementKeepsTheOldTopicAndAllowsAnotherCardWithTheSameTopic() = runBlocking {
        cache.apply(response("first", listOf(card(1).copy(topic = "shared"))), 100)
        cache.apply(response("next", listOf(card(2).copy(topic = "shared"))).copy(retiredCardIds = listOf(1)), 200)
        assertEquals(listOf(2L), cache.read().cards.map { it.id })
        val records = database.lawCardDao().read().single().cards
        assertEquals(2, records.count { it.topic == "shared" })
        assertTrue(records.first { it.id == 1L }.isRetired)
        cache.apply(response("stale", listOf(card(1, 9))), 300)
        assertEquals(listOf(2L), cache.read().cards.map { it.id })
    }

    @Test
    fun observersSeeCardsConditionsAndTokenFromTheSameCommittedGeneration() = runBlocking {
        cache.apply(response("before", listOf(card(1)), listOf(condition("before"))), 100)
        val snapshots = Channel<LawCacheSnapshot>(Channel.UNLIMITED)
        val collector = launch { cache.observe().collect { snapshots.send(it) } }
        try {
            val first = withTimeout(5_000) { snapshots.receive() }
            assertEquals("before", first.syncToken)
            cache.apply(
                response("after", listOf(card(2)), listOf(condition("after"))).copy(retiredCardIds = listOf(1)),
                200,
            )
            val second = withTimeout(5_000) { snapshots.receive() }
            assertEquals("after", second.syncToken)
            assertEquals(200L, second.lastSuccessfulSyncAtMillis)
            assertEquals(listOf(2L), second.cards.map { it.id })
            assertEquals("Test-only after", second.conditions.single().question)
            assertEquals(cache.read(), second)
        } finally {
            collector.cancel()
            snapshots.close()
        }
    }

    private fun response(
        token: String,
        cards: List<SyncedCardDto> = emptyList(),
        conditions: List<ConditionDefinitionDto> = emptyList(),
    ) = CardSyncDto(token, false, cards, emptyList(), conditions)

    private fun condition(label: String) = ConditionDefinitionDto(
        "test-condition", "Test-only $label", listOf(ConditionOptionDto("yes", "Test-only yes")),
    )

    private fun card(id: Long, version: Int = 1) = SyncedCardDto(
        cardId = id, version = version, cardType = "SHORT", changeKind = "LAW", audience = "GENERAL",
        title = "Test-only title", ttsText = "Test-only speech", legalBasis = "Test-only basis",
        lawExcerpt = "Test-only excerpt", effectiveOn = "2027-01-01", verifiedAt = "2026-11-13T09:00:00+09:00",
    )
}
