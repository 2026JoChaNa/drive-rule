package com.jochana.driverule.core.data.local.geo

import com.jochana.driverule.core.data.remote.SchoolZoneManifestDto
import com.jochana.driverule.core.model.SchoolZonePoint
import com.jochana.driverule.core.model.SchoolZoneSnapshot
import java.security.MessageDigest
import java.nio.charset.CharacterCodingException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Schema-1 checks are deliberately independent of Android and network I/O. */
internal object SchoolZoneValidator {
    const val MAX_FILE_BYTES = 2 * 1_024 * 1_024
    private val json = Json { ignoreUnknownKeys = true }

    fun validManifest(manifest: SchoolZoneManifestDto): Boolean = with(manifest) {
        dataset == "school-zones" && schema == 1 && count in 1..MAX_FILE_BYTES &&
            bytes in 1..MAX_FILE_BYTES.toLong() && sha256.matches(Regex("[0-9a-f]{64}")) &&
            validDate(referenceDate) && url.isNotBlank() && url.length <= 4_096
    }

    fun validate(manifest: SchoolZoneManifestDto, bytes: ByteArray): SchoolZoneSnapshot? {
        if (!validManifest(manifest) || bytes.size.toLong() != manifest.bytes ||
            bytes.size > MAX_FILE_BYTES || sha256(bytes) != manifest.sha256) return null
        return try {
            val file = json.decodeFromString<SchoolZoneFile>(bytes.decodeToString(throwOnInvalidSequence = true))
            if (file.schema != manifest.schema || file.dataset != manifest.dataset ||
                file.referenceDate != manifest.referenceDate || file.count != manifest.count ||
                file.zones.size != file.count) return null
            val points = file.zones.map { pair ->
                if (pair.size != 2) return null
                val latitude = pair[0].takeUnless { it.isString }?.doubleOrNull ?: return null
                val longitude = pair[1].takeUnless { it.isString }?.doubleOrNull ?: return null
                if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                    !longitude.isFinite() || longitude !in -180.0..180.0) return null
                SchoolZonePoint(latitude, longitude)
            }
            SchoolZoneSnapshot(file.referenceDate, manifest.sha256, Collections.unmodifiableList(points))
        } catch (_: SerializationException) {
            null
        } catch (_: CharacterCodingException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun validDate(value: String): Boolean = try {
        value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && LocalDate.parse(value).toString() == value
    } catch (_: DateTimeParseException) { false }
}

@Serializable
private data class SchoolZoneFile(
    val schema: Int,
    val dataset: String,
    val referenceDate: String,
    val count: Int,
    val zones: List<List<JsonPrimitive>>,
)
