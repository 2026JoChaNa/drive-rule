package com.jochana.driverule.core.data.local.guidance

import com.jochana.driverule.domain.guidance.GuidanceAttemptPolicy
import com.jochana.driverule.domain.guidance.GuidanceAttemptRecord
import com.jochana.driverule.domain.guidance.GuidanceAttemptRequest
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileGuidanceAttemptLedgerTest {
    @Test fun restartRetainsDuplicateStageAndProfileWideBudgetWithoutAnyPlaybackConfirmation() = runTest {
        val storage = MemoryStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        assertTrue(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request()))
        assertNotNull(storage.bytes) // Durability precedes success; there is no output/confirmation callback.
        val restarted = StoredGuidanceAttemptLedger(storage, dispatcher)
        assertFalse(restarted.reserve(request(at = 60_000)))
        assertTrue(restarted.reserve(request(at = 60_000, card = 2)))
        assertFalse(restarted.reserve(request(at = 120_000, card = 3)))
        assertEquals(2, storage.writes)
    }

    @Test fun stageChangeIsASeparateAttemptButStillUsesTheSameProfileCooldownAndBudget() = runTest {
        val storage = MemoryStorage()
        val ledger = StoredGuidanceAttemptLedger(storage, StandardTestDispatcher(testScheduler))
        assertTrue(ledger.reserve(request(stage = "Upcoming")))
        assertFalse(ledger.reserve(request(at = 59_999, stage = "Current")))
        assertTrue(ledger.reserve(request(at = 60_000, stage = "Current")))
        assertFalse(ledger.reserve(request(at = 120_000, stage = "Unspecified")))
    }

    @Test fun rollingWindowExpiresAtExactly24HoursAndDoesNotRetainOldEntries() = runTest {
        val storage = MemoryStorage()
        val ledger = StoredGuidanceAttemptLedger(storage, StandardTestDispatcher(testScheduler))
        assertTrue(ledger.reserve(request()))
        assertTrue(ledger.reserve(request(at = 60_000, card = 2)))
        assertFalse(ledger.reserve(request(at = DAY - 1, card = 3)))
        assertTrue(ledger.reserve(request(at = DAY)))
        assertFalse(ledger.reserve(request(at = DAY + 60_000, card = 1)))
        assertTrue(ledger.reserve(request(at = DAY + 60_000, card = 2)))
        assertFalse(ledger.reserve(request(at = DAY + 120_000, card = 3)))
    }

    @Test fun concurrentReservationsSerializeReadDecideAndCommit() = runTest {
        val storage = MemoryStorage()
        val ledger = StoredGuidanceAttemptLedger(storage, StandardTestDispatcher(testScheduler))
        val results = List(32) { async { ledger.reserve(request()) } }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(1, storage.writes)
    }

    @Test fun differentProfilesHaveIndependentCooldownAndBudgets() = runTest {
        val storage = MemoryStorage()
        val ledger = StoredGuidanceAttemptLedger(storage, StandardTestDispatcher(testScheduler))
        assertTrue(ledger.reserve(request(profile = 1)))
        assertTrue(ledger.reserve(request(profile = 2)))
        assertFalse(ledger.reserve(request(at = 59_999, card = 2, profile = 1)))
        assertTrue(ledger.reserve(request(at = 60_000, card = 2, profile = 1)))
        assertTrue(ledger.reserve(request(at = 60_000, card = 2, profile = 2)))
    }

    @Test fun sameBootElapsedTimePreventsForwardWallJumpFromExpiringAnAttempt() = runTest {
        val ledger = StoredGuidanceAttemptLedger(MemoryStorage(), StandardTestDispatcher(testScheduler))
        assertTrue(ledger.reserve(request()))
        assertFalse(ledger.reserve(request(at = 60_000).copy(nowEpochMs = EPOCH + 20 * DAY)))
        // A rolled-back wall value is unsafe to persist even when elapsed already passed 24 hours.
        assertFalse(ledger.reserve(request(at = DAY).copy(nowEpochMs = EPOCH - 1)))
        assertTrue(ledger.reserve(request(at = DAY)))
    }

    @Test fun elapsedRollbackWithinBootAndWallRollbackAcrossBootBothFailClosed() = runTest {
        val storage = MemoryStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        assertTrue(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request()))
        val restarted = StoredGuidanceAttemptLedger(storage, dispatcher)
        assertFalse(restarted.reserve(request(card = 2).copy(nowElapsedMs = ELAPSED - 1)))
        assertFalse(restarted.reserve(request(card = 2).copy(bootId = "boot-b", nowElapsedMs = 0,
            nowEpochMs = EPOCH - 1)))
        assertEquals(1, storage.writes)
    }

    @Test fun newBootUsesWallAgeAndPreservesDailyBudget() = runTest {
        val storage = MemoryStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        assertTrue(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request()))
        val restarted = StoredGuidanceAttemptLedger(storage, dispatcher)
        assertFalse(restarted.reserve(request(at = 60_000).copy(bootId = "boot-b", nowElapsedMs = 0)))
        assertTrue(restarted.reserve(request(at = 60_000, card = 2).copy(bootId = "boot-b", nowElapsedMs = 0)))
        assertFalse(restarted.reserve(request(at = 120_000, card = 3).copy(bootId = "boot-b", nowElapsedMs = 60_000)))
    }

    @Test fun corruptEmptyUnknownSchemaAndOversizedFilesAreNotTreatedAsFreshState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val corrupt = listOf(
            byteArrayOf(),
            "not json".encodeToByteArray(),
            "{\"schemaVersion\":2,\"records\":[]}".encodeToByteArray(),
            "{\"schemaVersion\":1,\"records\":[],\"unknown\":true}".encodeToByteArray(),
            byteArrayOf(0xc3.toByte()),
            ByteArray(64 * 1_024 + 1),
        )
        for (bytes in corrupt) {
            val storage = MemoryStorage().apply { this.bytes = bytes }
            assertFalse(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request()))
            assertEquals(0, storage.writes)
        }
    }

    @Test fun validJsonWithInvalidRecordFailsClosedWithoutOverwritingEvidence() = runTest {
        val storage = MemoryStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        assertTrue(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request()))
        storage.bytes = requireNotNull(storage.bytes).decodeToString().replace("Current", "UNRECOGNIZED").encodeToByteArray()
        assertFalse(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request(at = DAY)))
        assertEquals(1, storage.writes)
    }

    @Test fun readOrWriteIoFailureNeverAuthorizesOutput() = runTest {
        val storage = MemoryStorage()
        val ledger = StoredGuidanceAttemptLedger(storage, StandardTestDispatcher(testScheduler))
        storage.failRead = true
        assertFalse(ledger.reserve(request()))
        storage.failRead = false
        storage.failWrite = true
        assertFalse(ledger.reserve(request()))
        assertNull(storage.bytes)
        storage.failWrite = false
        assertTrue(ledger.reserve(request()))
    }

    @Test fun uncertainCommittedWriteIsReReadAndCannotBeRetriedAsANewAttempt() = runTest {
        val storage = MemoryStorage().apply { failAfterCommit = true }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ledger = StoredGuidanceAttemptLedger(storage, dispatcher)
        assertFalse(ledger.reserve(request()))
        storage.failAfterCommit = false
        assertFalse(ledger.reserve(request(at = 60_000)))
        assertTrue(ledger.reserve(request(at = 60_000, card = 2)))
        assertFalse(ledger.reserve(request(at = 120_000, card = 3)))
    }

    @Test fun cancellationAfterCommitPropagatesButRetainsTheAttemptForTheNextProcess() = runTest {
        val storage = MemoryStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ledger = StoredGuidanceAttemptLedger(storage, dispatcher)
        val reservation = launch(start = CoroutineStart.LAZY) { ledger.reserve(request()) }
        storage.afterWrite = { reservation.cancel() }
        reservation.start()
        reservation.join()
        assertTrue(reservation.isCancelled)
        assertNotNull(storage.bytes)
        storage.afterWrite = null
        assertFalse(StoredGuidanceAttemptLedger(storage, dispatcher).reserve(request(at = 60_000)))
    }

    @Test fun capacityDoesNotEvictUnexpiredAttemptsAndExpiredEntriesCanBePruned() {
        val records = (1..128).map { profile -> record(request(profile = profile.toLong())) }
        assertNull(GuidanceAttemptPolicy.reserve(records, request(at = 60_000, profile = 129)))
        val afterExpiry = GuidanceAttemptPolicy.reserve(records, request(at = DAY, profile = 129))
        assertEquals(1, afterExpiry?.size)
        assertNull(GuidanceAttemptPolicy.reserve(records + record(request(profile = 129)), request(at = DAY)))
    }

    @Test fun invalidIdentityStageClockOrBootMarkerCannotCreateARecord() {
        val invalid = listOf(request().copy(profileId = 0), request().copy(cardId = -1),
            request(stage = "OTHER"), request().copy(nowEpochMs = -1), request().copy(nowElapsedMs = -1),
            request().copy(bootId = ""), request().copy(bootId = "x".repeat(129)), request().copy(bootId = "boot\n"))
        invalid.forEach { assertNull(GuidanceAttemptPolicy.reserve(emptyList(), it)) }
    }

    private class MemoryStorage : GuidanceAttemptStorage {
        var bytes: ByteArray? = null
        var writes = 0
        var failRead = false
        var failWrite = false
        var failAfterCommit = false
        var afterWrite: (() -> Unit)? = null
        override fun read(): ByteArray? {
            if (failRead) throw IOException("unavailable")
            return bytes?.copyOf()
        }
        override fun write(bytes: ByteArray) {
            if (failWrite) throw IOException("unavailable")
            this.bytes = bytes.copyOf()
            writes++
            afterWrite?.invoke()
            if (failAfterCommit) throw IOException("uncertain commit")
        }
    }

    private fun request(at: Long = 0, profile: Long = 1, card: Long = 1, stage: String = "Current") =
        GuidanceAttemptRequest(profile, card, stage, EPOCH + at, ELAPSED + at, "boot-a")

    private fun record(request: GuidanceAttemptRequest) = GuidanceAttemptRecord(request.profileId, request.cardId,
        request.stage, request.nowEpochMs, request.nowElapsedMs, request.bootId)

    private companion object {
        const val EPOCH = 1_800_000_000_000L
        const val ELAPSED = 1_000L
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
