package com.jochana.driverule.core.data.local.law

import com.jochana.driverule.core.data.remote.ConditionDefinitionDto
import com.jochana.driverule.core.data.remote.ConditionOptionDto
import com.jochana.driverule.core.data.remote.SyncedCardDto
import com.jochana.driverule.core.model.CachedEventTag
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Test-only content: none of these strings represents a reviewed law or a real driving rule. */
class LawCardValidatorTest {
    @Test
    fun `condition catalog preserves server order and accepts newly defined keys`() {
        val definitions = listOf(
            condition("test_future_condition", listOf("later", "earlier")),
            condition("test_second_condition"),
        )

        val cached = LawCardValidator.conditions(definitions)

        assertEquals(definitions.map { it.key }, cached.map { it.key })
        assertEquals(definitions.map { it.question }, cached.map { it.question })
        assertEquals(listOf("later", "earlier"), cached.first().options.map { it.key })
        assertEquals(definitions.first().options.map { it.label }, cached.first().options.map { it.label })
    }

    @Test
    fun `duplicate or incomplete condition definitions invalidate the catalog`() {
        val valid = condition()
        val invalidCatalogs = listOf(
            listOf(valid, valid.copy(question = "Test-only duplicate")),
            listOf(valid.copy(key = " ")),
            listOf(valid.copy(question = " ")),
            listOf(valid.copy(options = emptyList())),
            listOf(valid.copy(options = listOf(valid.options.first(), valid.options.first()))),
            listOf(valid.copy(options = listOf(ConditionOptionDto(" ", "Test-only label")))),
            listOf(valid.copy(options = listOf(ConditionOptionDto("yes", " ")))),
        )

        invalidCatalogs.forEach { definitions ->
            assertThrows(InvalidCardSyncException::class.java) {
                LawCardValidator.conditions(definitions)
            }
        }
    }

    @Test
    fun `schema two preserves table order row order and reviewed text without reordering`() {
        val decision = json("""
            {"schema":2,"tables":[
              {"key":"z_last_alphabetically","title":"Test-only first table",
               "conditions":["test_condition"],"rows":[
                 {"when":{"test_condition":"no"},"text":"Test-only no first","tts":"Test-only no voice"},
                 {"when":{"test_condition":"yes"},"text":"Test-only yes second","tts":"Test-only yes voice"}]},
              {"key":"a_first_alphabetically","title":"Test-only second table",
               "conditions":["test_condition"],"rows":[
                 {"text":"Test-only wildcard","tts":"Test-only wildcard voice"}]}
            ]}
        """)

        val cached = requireNotNull(LawCardValidator.card(conditional(decision), catalog()))
        val restored = Json.parseToJsonElement(requireNotNull(cached.decisionJson)).jsonObject
        val tables = restored.getValue("tables").jsonArray

        assertEquals(decision, restored)
        assertEquals(
            listOf("z_last_alphabetically", "a_first_alphabetically"),
            tables.map { it.jsonObject.getValue("key").jsonPrimitive.content },
        )
        assertEquals(
            listOf("Test-only no first", "Test-only yes second"),
            tables.first().jsonObject.getValue("rows").jsonArray.map {
                it.jsonObject.getValue("text").jsonPrimitive.content
            },
        )
    }

    @Test
    fun `missing null and empty when objects are each valid wildcard rows`() {
        listOf("", "\"when\":null,", "\"when\":{},").forEach { whenField ->
            val decision = singleTable("""
                [{${whenField}"text":"Test-only all options","tts":"Test-only all options voice"}]
            """)

            assertNotNull(LawCardValidator.card(conditional(decision), catalog()))
        }
    }

    @Test
    fun `dynamic server conditions can be used by a new decision table`() {
        val definitions = LawCardValidator.conditions(listOf(condition("test_future_key", listOf("new_a", "new_b"))))
        val decision = singleTable(
            rows = """[{"text":"Test-only dynamic rule","tts":"Test-only dynamic voice"}]""",
            conditions = """["test_future_key"]""",
        )

        assertNotNull(LawCardValidator.card(conditional(decision), definitions))
    }

    @Test
    fun `uncovered option combination rejects a card`() {
        val decision = singleTable("""
            [{"when":{"test_condition":"yes"},"text":"Test-only one option","tts":"Test-only voice"}]
        """)

        assertNull(LawCardValidator.card(conditional(decision), catalog()))
    }

    @Test
    fun `overlap between a wildcard and a specific row rejects a card`() {
        val decision = singleTable("""
            [{"text":"Test-only all options","tts":"Test-only all voice"},
             {"when":{"test_condition":"yes"},"text":"Test-only overlap","tts":"Test-only overlap voice"}]
        """)

        assertNull(LawCardValidator.card(conditional(decision), catalog()))
    }

    @Test
    fun `repeated rows are rejected even when every combination is covered`() {
        val row = """{"text":"Test-only duplicate row","tts":"Test-only duplicate voice"}"""

        assertNull(LawCardValidator.card(conditional(singleTable("[$row,$row]")), catalog()))
    }

    @Test
    fun `unsupported or malformed schemas do not enter the cache`() {
        listOf("1", "3", "99", "null", "\"2\"", "2.5").forEach { schema ->
            val decision = json(singleTable(wildcardRows).toString().replace("\"schema\":2", "\"schema\":$schema"))

            assertNull("schema=$schema", LawCardValidator.card(conditional(decision), catalog()))
        }
        assertNull(LawCardValidator.card(conditional(json("""{"tables":[]}""")), catalog()))
    }

    @Test
    fun `unknown condition keys and unknown option values reject only the card`() {
        val invalidDecisions = listOf(
            singleTable(wildcardRows, """["test_missing_condition"]"""),
            singleTable("""[{"when":{"test_condition":"maybe"},"text":"Test-only text","tts":"Test-only voice"}]"""),
            singleTable("""[{"when":{"test_missing_condition":"yes"},"text":"Test-only text","tts":"Test-only voice"}]"""),
        )

        invalidDecisions.forEach { decision ->
            assertNull(LawCardValidator.card(conditional(decision), catalog()))
        }
        assertNotNull(LawCardValidator.card(card(), catalog()))
    }

    @Test
    fun `when cannot refer to a catalog condition omitted from its own table`() {
        val definitions = LawCardValidator.conditions(listOf(condition(), condition("test_other_condition")))
        val decision = singleTable("""
            [{"when":{"test_other_condition":"yes"},"text":"Test-only text","tts":"Test-only voice"}]
        """)

        assertNull(LawCardValidator.card(conditional(decision), definitions))
    }

    @Test
    fun `empty duplicate or too many tables are rejected`() {
        val table = singleTable(wildcardRows).getValue("tables").jsonArray.single()
        val invalidDecisions = listOf(
            json("""{"schema":2,"tables":[]}"""),
            json("""{"schema":2,"tables":[$table,$table]}"""),
            json("""{"schema":2,"tables":[$table,${table.toString().replace("test_table", "test_table_2")},${table.toString().replace("test_table", "test_table_3")}]}"""),
        )

        invalidDecisions.forEach { decision ->
            assertNull(LawCardValidator.card(conditional(decision), catalog()))
        }
    }

    @Test
    fun `duplicate conditions and more than three table conditions are rejected`() {
        val definitions = LawCardValidator.conditions((1..4).map { condition("test_condition_$it", listOf("only")) })
        val repeated = singleTable(wildcardRows, """["test_condition_1","test_condition_1"]""")
        val tooMany = singleTable(wildcardRows, """["test_condition_1","test_condition_2","test_condition_3","test_condition_4"]""")

        assertNull(LawCardValidator.card(conditional(repeated), definitions))
        assertNull(LawCardValidator.card(conditional(tooMany), definitions))
    }

    @Test
    fun `more than eight option combinations are rejected despite wildcard coverage`() {
        val definitions = LawCardValidator.conditions(listOf(
            condition("test_first", listOf("one", "two", "three")),
            condition("test_second", listOf("one", "two", "three")),
        ))
        val decision = singleTable(wildcardRows, """["test_first","test_second"]""")

        assertNull(LawCardValidator.card(conditional(decision), definitions))
    }

    @Test
    fun `malformed table and row shapes are rejected without throwing`() {
        val invalidDecisions = listOf(
            json("""{"schema":2,"tables":[null]}"""),
            json("""{"schema":2,"tables":{}}"""),
            singleTable("[]"),
            singleTable("[null]"),
            singleTable("""[{"text":" ","tts":"Test-only voice"}]"""),
            singleTable("""[{"text":"Test-only text","tts":" "}]"""),
            singleTable("""[{"when":[],"text":"Test-only text","tts":"Test-only voice"}]"""),
            singleTable("""[{"when":{"test_condition":null},"text":"Test-only text","tts":"Test-only voice"}]"""),
            singleTable(wildcardRows, "[]"),
        )

        invalidDecisions.forEach { decision ->
            assertNull(LawCardValidator.card(conditional(decision), catalog()))
        }
    }

    @Test
    fun `unknown event tag keeps the valid card but disables event matching`() {
        val cached = requireNotNull(LawCardValidator.card(card().copy(eventTag = "FUTURE_EVENT"), catalog()))

        assertNull(cached.eventTag)
        assertNotNull(LawCardValidator.card(card().copy(eventTag = null), catalog()))
        assertEquals(
            CachedEventTag.RIGHT_TURN_CROSSWALK,
            requireNotNull(LawCardValidator.card(card().copy(eventTag = "RIGHT_TURN_CROSSWALK"), catalog())).eventTag,
        )
    }

    @Test
    fun `unknown card type change kind or audience excludes only that card`() {
        val invalidCards = listOf(
            card().copy(cardType = "FUTURE_CARD"),
            card().copy(changeKind = "FUTURE_CHANGE"),
            card().copy(audience = "FUTURE_AUDIENCE"),
        )

        assertEquals(listOf(false, false, false, true), (invalidCards + card()).map {
            LawCardValidator.card(it, catalog()) != null
        })
    }

    @Test
    fun `verification instant preserves its offset and law dates remain local dates`() {
        val dto = card().copy(
            verifiedAt = "2026-11-15T09:30:00+09:00",
            promulgatedOn = "2026-02-28",
            effectiveOn = "2027-01-02",
        )

        val cached = requireNotNull(LawCardValidator.card(dto, catalog()))

        assertEquals(Instant.parse("2026-11-15T00:30:00Z").toEpochMilli(), cached.verifiedAtMillis)
        assertEquals(LocalDate.of(2026, 2, 28), cached.promulgatedOn)
        assertEquals(LocalDate.of(2027, 1, 2), cached.effectiveOn)
    }

    @Test
    fun `absent law dates remain absent rather than becoming an invented effective date`() {
        val cached = requireNotNull(LawCardValidator.card(card(), catalog()))

        assertNull(cached.promulgatedOn)
        assertNull(cached.effectiveOn)
    }

    @Test
    fun `malformed dates reject only the affected card`() {
        val invalidCards = listOf(
            card().copy(verifiedAt = "not-a-date"),
            card().copy(verifiedAt = "2026-11-15T09:30:00"),
            card().copy(promulgatedOn = "2026-02-30"),
            card().copy(effectiveOn = "2026-13-01"),
            card().copy(effectiveOn = "2026-11-15T00:00:00Z"),
        )

        assertEquals(listOf(false, false, false, false, false, true), (invalidCards + card()).map {
            LawCardValidator.card(it, catalog()) != null
        })
    }

    @Test
    fun `card type rules reject contradictory scene and decision content`() {
        val decision = singleTable(wildcardRows)
        val invalidCards = listOf(
            card().copy(dontText = "Test-only unmatched scene"),
            card().copy(doText = "Test-only unmatched scene"),
            card().copy(decision = decision),
            conditional(decision).copy(dontText = "Test-only forbidden scene"),
            conditional(decision).copy(doText = "Test-only forbidden scene"),
            conditional(decision).copy(sceneKey = "test_scene"),
            card().copy(cardType = "CONDITIONAL"),
            card().copy(cardType = "FIXED_RESPONSE", decision = decision),
        )

        invalidCards.forEach { assertNull(LawCardValidator.card(it, catalog())) }
        assertNotNull(LawCardValidator.card(card().copy(
            dontText = "Test-only first scene", doText = "Test-only second scene",
        ), catalog()))
        assertNotNull(LawCardValidator.card(card().copy(cardType = "FIXED_RESPONSE"), catalog()))
    }

    @Test
    fun `source organization and limited audience follow the card contract`() {
        assertNull(LawCardValidator.card(card().copy(sourceOrg = "Test-only source"), catalog()))
        assertNull(LawCardValidator.card(card().copy(changeKind = "OPERATION"), catalog()))
        assertNull(LawCardValidator.card(card().copy(changeKind = "OPERATION", sourceOrg = " "), catalog()))
        assertNotNull(LawCardValidator.card(card().copy(changeKind = "OPERATION", sourceOrg = "Test-only source"), catalog()))
        assertNull(LawCardValidator.card(card().copy(audience = "LIMITED", eventTag = "SCHOOL_ZONE_ENTRY"), catalog()))
        assertNotNull(LawCardValidator.card(card().copy(audience = "LIMITED"), catalog()))
    }

    private fun catalog() = LawCardValidator.conditions(listOf(condition()))

    private fun condition(key: String = "test_condition", options: List<String> = listOf("yes", "no")) =
        ConditionDefinitionDto(
            key = key,
            question = "Test-only question for $key",
            options = options.map { ConditionOptionDto(it, "Test-only label for $it") },
        )

    private fun card() = SyncedCardDto(
        cardId = 41,
        version = 2,
        cardType = "SHORT",
        changeKind = "LAW",
        audience = "GENERAL",
        title = "Test-only title",
        ttsText = "Test-only voice",
        legalBasis = "Test-only legal basis fixture",
        lawExcerpt = "Test-only excerpt fixture",
        verifiedAt = "2026-11-15T00:30:00Z",
    )

    private fun conditional(decision: JsonObject) = card().copy(cardType = "CONDITIONAL", decision = decision)

    private fun singleTable(rows: String, conditions: String = """["test_condition"]""") = json("""
        {"schema":2,"tables":[{"key":"test_table","title":"Test-only table",
          "conditions":$conditions,"rows":$rows}]}
    """)

    private fun json(value: String) = Json.parseToJsonElement(value.trimIndent()).jsonObject

    private val wildcardRows = """[{"text":"Test-only wildcard","tts":"Test-only wildcard voice"}]"""
}
