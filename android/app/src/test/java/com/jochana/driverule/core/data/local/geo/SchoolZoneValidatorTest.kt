package com.jochana.driverule.core.data.local.geo

import com.jochana.driverule.core.data.remote.SchoolZoneManifestDto
import com.jochana.driverule.core.model.SchoolZonePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolZoneValidatorTest {
    @Test
    fun `valid schema tolerates unknown metadata and creates stable coordinate identity`() {
        val bytes = file("[[37.5,127.0],[33.2,126.2]]")
        val snapshot = requireNotNull(SchoolZoneValidator.validate(manifest(bytes), bytes))
        assertEquals("2026-10-27", snapshot.datasetVersion)
        assertEquals(listOf(SchoolZonePoint(37.5, 127.0), SchoolZonePoint(33.2, 126.2)), snapshot.zones)
        assertEquals(SchoolZonePoint(-0.0, 1.0).id, SchoolZonePoint(0.0, 1.0).id)
    }

    @Test
    fun `manifest rejects unsupported schema dataset date hash size and empty count`() {
        val valid = manifest(file())
        assertTrue(SchoolZoneValidator.validManifest(valid))
        listOf(valid.copy(schema = 2), valid.copy(dataset = "other"), valid.copy(count = 0),
            valid.copy(bytes = 0), valid.copy(bytes = SchoolZoneValidator.MAX_FILE_BYTES + 1L),
            valid.copy(sha256 = "a".repeat(63)), valid.copy(sha256 = "Z".repeat(64)),
            valid.copy(referenceDate = "2026-02-30"), valid.copy(referenceDate = "2026-1-1"),
            valid.copy(url = " ")).forEach { assertFalse(it.toString(), SchoolZoneValidator.validManifest(it)) }
    }

    @Test
    fun `exact bytes hash and manifest size are required`() {
        val bytes = file()
        val manifest = manifest(bytes)
        assertNull(SchoolZoneValidator.validate(manifest.copy(sha256 = "0".repeat(64)), bytes))
        assertNull(SchoolZoneValidator.validate(manifest.copy(bytes = bytes.size + 1L), bytes))
        assertNull(SchoolZoneValidator.validate(manifest, bytes + ' '.code.toByte()))
    }

    @Test
    fun `file metadata and count must match manifest`() {
        val bytes = file()
        val valid = manifest(bytes)
        assertNull(SchoolZoneValidator.validate(valid.copy(count = 3), bytes))
        assertNull(SchoolZoneValidator.validate(valid.copy(referenceDate = "2026-10-28"), bytes))
        listOf(
            file().decodeToString().replace("\"schema\":1", "\"schema\":2"),
            file().decodeToString().replace("school-zones", "other"),
            file().decodeToString().replace("\"count\":2", "\"count\":1"),
        ).forEach { content ->
            val bad = content.encodeToByteArray()
            assertNull(SchoolZoneValidator.validate(manifest(bad), bad))
        }
    }

    @Test
    fun `coordinate shape nonnumeric nonfinite and out of range are rejected`() {
        listOf("[[37,127,0],[33,126]]", "[[37],[33,126]]", "[[91,127],[33,126]]",
            "[[37,-181],[33,126]]", "[[null,127],[33,126]]", "[[NaN,127],[33,126]]",
            "[[1e999,127],[33,126]]", "[[\"37\",127],[33,126]]").forEach { zones ->
            val bytes = file(zones)
            assertNull(zones, SchoolZoneValidator.validate(manifest(bytes), bytes))
        }
    }

    @Test
    fun `invalid utf8 and truncated json cannot establish a snapshot`() {
        val broken = file().dropLast(2).toByteArray()
        assertNull(SchoolZoneValidator.validate(manifest(broken), broken))
        val invalidUtf8 = file().toMutableList().apply { add(0, 0xff.toByte()) }.toByteArray()
        assertNull(SchoolZoneValidator.validate(manifest(invalidUtf8), invalidUtf8))
        assertNotNull(SchoolZoneValidator.validate(manifest(file()), file()))
    }

    private fun file(zones: String = "[[37.5,127.0],[33.2,126.2]]") =
        """{"schema":1,"dataset":"school-zones","source":"public fixture","referenceDate":"2026-10-27","count":2,"zones":$zones,"future":true}""".encodeToByteArray()

    private fun manifest(bytes: ByteArray) = SchoolZoneManifestDto("school-zones", 1, "2026-10-27", 2,
        SchoolZoneValidator.sha256(bytes), bytes.size.toLong(), "/fixture.json")
}
