package com.jochana.driverule.core.data.local.law

import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.data.remote.ConditionDefinitionDto
import com.jochana.driverule.core.data.remote.ConditionOptionDto
import com.jochana.driverule.core.data.remote.SyncedCardDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LawCacheMergePolicyTest {
    @Test
    fun fullSyncDoesNotDeleteAnOmittedPublishedCopy() {
        val first = merge(cards = listOf(card(1), card(2)))
        val next = merge(first.records, cards = listOf(card(2, version = 2)), fullSync = true)
        assertEquals(listOf(1L, 2L), visible(next).map { it.id })
        assertEquals(listOf(1, 2), visible(next).map { it.version })
        assertEquals(2, merge(next.records, fullSync = true).records.size)
    }

    @Test
    fun sameVersionTopicChangeAppliesAndLowerVersionCannotOverwriteIt() {
        val first = merge(cards = listOf(card(1, 2, topic = "old")))
        val reassigned = merge(first.records, cards = listOf(card(1, 2, topic = "new")))
        assertEquals("new", visible(reassigned).single().topic)
        val stale = merge(reassigned.records, cards = listOf(card(1, 1, topic = "old")))
        assertEquals(reassigned.records, stale.records)
        assertEquals(CacheApplyResult(0, 1, 0), stale.result)
    }

    @Test
    fun retirementPreservesCopyAndIsTerminalEvenForAHigherVersion() {
        val first = merge(cards = listOf(card(1, topic = "reused")))
        val retired = merge(first.records, retirements = listOf(1, 999))
        assertTrue(visible(retired).isEmpty())
        assertEquals("reused", retired.records.single().topic)
        assertTrue(retired.records.single().isRetired)
        assertEquals(1, retired.result.retiredCardCount)
        val attemptedRevival = merge(retired.records, cards = listOf(card(1, 9), card(2, topic = "reused")))
        assertEquals(listOf(2L), visible(attemptedRevival).map { it.id })
        assertEquals(retired.records.single(), attemptedRevival.records.first { it.id == 1L })
        assertEquals(2, attemptedRevival.records.size)
    }

    @Test
    fun retirementWinsWhenTheSameResponseContainsTheCard() {
        val merged = merge(cards = listOf(card(1)), retirements = listOf(1, 1))
        assertTrue(visible(merged).isEmpty())
        assertTrue(merged.records.single().isRetired)
        assertEquals(CacheApplyResult(0, 1, 1), merged.result)
    }

    @Test
    fun higherUnsupportedVersionBlocksOldSupportedCopyWithoutPretendingItIsRetired() {
        val first = merge(cards = listOf(card(1), card(2)))
        val unsupported = merge(first.records, cards = listOf(card(1, 3).copy(cardType = "FUTURE")))
        val blocked = unsupported.records.first { it.id == 1L }
        assertEquals(3, blocked.version)
        assertFalse(blocked.isSupported)
        assertFalse(blocked.isRetired)
        assertEquals(listOf(2L), visible(unsupported).map { it.id })
        val staleSupported = merge(unsupported.records, cards = listOf(card(1, 2)))
        assertEquals(unsupported.records, staleSupported.records)
        val supportedUpdate = merge(staleSupported.records, cards = listOf(card(1, 4)))
        assertEquals(listOf(1L, 2L), visible(supportedUpdate).map { it.id })
    }

    @Test
    fun oneInvalidCardDoesNotDropTheOthersButItsNewVersionAlsoBlocksFallback() {
        val first = merge(cards = listOf(card(1)))
        val next = merge(first.records, cards = listOf(card(1, 2).copy(effectiveOn = "invalid"), card(2)))
        assertEquals(listOf(2L), visible(next).map { it.id })
        assertEquals(CacheApplyResult(1, 1, 0), next.result)
        assertFalse(next.records.first { it.id == 1L }.isSupported)
    }

    @Test
    fun replacementConditionsRevalidateOmittedConditionalCopiesAndCanRestoreValidReferences() {
        val initialConditions = listOf(condition())
        val first = merge(cards = listOf(conditionalCard()), conditions = initialConditions)
        assertEquals(1, visible(first).size)
        val noLongerKnown = merge(first.records, conditions = emptyList())
        assertTrue(visible(noLongerKnown).isEmpty())
        assertFalse(noLongerKnown.records.single().isRetired)
        assertFalse(noLongerKnown.records.single().isSupported)
        val restored = merge(noLongerKnown.records, conditions = listOf(condition().copy(question = "Test-only new question")))
        assertEquals(1, visible(restored).size)
        assertEquals("Test-only new question", restored.conditions.single().question)
    }

    @Test
    fun replacedOptionDictionaryCanInvalidateCoverageOfAnOmittedCard() {
        val initial = condition().copy(options = listOf(ConditionOptionDto("yes", "Test-only yes")))
        val decision = Json.parseToJsonElement(
            """{"schema":2,"tables":[{"key":"test","title":"Test-only","conditions":["signal"],"rows":[{"when":{"signal":"yes"},"text":"Test-only","tts":"Test-only"}]}]}""",
        ).jsonObject
        val first = merge(cards = listOf(conditionalCard().copy(decision = decision)), conditions = listOf(initial))
        assertEquals(1, visible(first).size)
        val expanded = merge(first.records, conditions = listOf(condition()))
        assertTrue(visible(expanded).isEmpty())
    }

    @Test
    fun invalidSharedConditionsAndEnvelopeFailWithoutMutatingTheInputCopies() {
        val first = merge(cards = listOf(card(1)))
        val before = first.records.toList()
        val invalidResponses = listOf(
            response(cards = listOf(card(2)), conditions = listOf(condition(), condition())),
            response(cards = listOf(card(2))).copy(syncToken = " "),
            response(cards = listOf(card(2))).copy(retiredCardIds = listOf(0)),
        )
        invalidResponses.forEach { invalid ->
            val result = runCatching { LawCacheMergePolicy.merge(first.records, invalid, 1_000) }
            assertTrue(result.exceptionOrNull() is InvalidCardSyncException)
            assertEquals(before, first.records)
        }
    }

    @Test
    fun duplicateAndOutOfOrderEntriesKeepTheHighestVersion() {
        val result = merge(cards = listOf(card(1, 2), card(1, 1), card(1, 2, topic = "last")))
        assertEquals(1, visible(result).size)
        assertEquals(2, visible(result).single().version)
        assertEquals("last", visible(result).single().topic)
        assertEquals(1, result.result.ignoredCardCount)
    }

    private fun visible(result: MergedLawCache) = LawCacheCodec.visibleCards(result.records, result.conditions)

    private fun merge(
        existing: List<LawCardRecord> = emptyList(),
        cards: List<SyncedCardDto> = emptyList(),
        retirements: List<Long> = emptyList(),
        fullSync: Boolean = false,
        conditions: List<ConditionDefinitionDto> = emptyList(),
    ) = LawCacheMergePolicy.merge(existing, response(cards, retirements, fullSync, conditions), 1_000)

    private fun response(
        cards: List<SyncedCardDto> = emptyList(),
        retirements: List<Long> = emptyList(),
        fullSync: Boolean = false,
        conditions: List<ConditionDefinitionDto> = emptyList(),
    ) = CardSyncDto("opaque-token", fullSync, cards, retirements, conditions)

    private fun card(id: Long, version: Int = 1, topic: String? = null) = SyncedCardDto(
        cardId = id, version = version, cardType = "SHORT", changeKind = "LAW", audience = "GENERAL",
        topic = topic, title = "Test-only title", ttsText = "Test-only speech", legalBasis = "Test-only basis",
        lawExcerpt = "Test-only excerpt", verifiedAt = "2026-11-13T09:00:00+09:00",
    )

    private fun condition() = ConditionDefinitionDto(
        "signal", "Test-only question",
        listOf(ConditionOptionDto("yes", "Test-only yes"), ConditionOptionDto("no", "Test-only no")),
    )

    private fun conditionalCard() = card(1).copy(
        cardType = "CONDITIONAL",
        decision = Json.parseToJsonElement(
            """{"schema":2,"tables":[{"key":"test","title":"Test-only","conditions":["signal"],"rows":[{"text":"Test-only","tts":"Test-only"}]}]}""",
        ).jsonObject,
    )
}
