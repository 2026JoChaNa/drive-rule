package com.jochana.driverule.core.data.local.guidance

import android.content.Context
import android.util.AtomicFile
import com.jochana.driverule.domain.guidance.GuidanceAttemptLedger
import com.jochana.driverule.domain.guidance.GuidanceAttemptPolicy
import com.jochana.driverule.domain.guidance.GuidanceAttemptRecord
import com.jochana.driverule.domain.guidance.GuidanceAttemptRequest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Atomic, backup-excluded attempt storage. The app container must own one instance per process. */
class FileGuidanceAttemptLedger(context: Context) : GuidanceAttemptLedger {
    private val delegate = StoredGuidanceAttemptLedger(AtomicAttemptStorage(
        File(context.applicationContext.noBackupFilesDir, "guidance/attempt-ledger-v1.json"),
    ))

    override suspend fun reserve(request: GuidanceAttemptRequest): Boolean = delegate.reserve(request)
}

/** Injectable synchronous storage; only the ledger's IO context calls these methods. */
internal interface GuidanceAttemptStorage {
    /** Null means an absent initial file. An empty, unreadable or corrupt file is not empty state. */
    fun read(): ByteArray?
    /** Return only after the replacement is durable; throw on a failed or uncertain write. */
    fun write(bytes: ByteArray)
}

internal class StoredGuidanceAttemptLedger(
    private val storage: GuidanceAttemptStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GuidanceAttemptLedger {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    override suspend fun reserve(request: GuidanceAttemptRequest): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            try {
                // Re-read even after a failed write: it may have committed before reporting error.
                val bytes = storage.read()
                val records = if (bytes == null) emptyList() else {
                    if (bytes.isEmpty() || bytes.size > MAX_STORAGE_BYTES) return@withContext false
                    val snapshot = json.decodeFromString<AttemptSnapshot>(bytes.decodeToString(throwOnInvalidSequence = true))
                    if (snapshot.schemaVersion != SCHEMA_VERSION || snapshot.records.size > GuidanceAttemptPolicy.MAX_RECORDS)
                        return@withContext false
                    snapshot.records.map { it.record() }
                }
                val reserved = GuidanceAttemptPolicy.reserve(records, request) ?: return@withContext false
                currentCoroutineContext().ensureActive()
                val next = AttemptSnapshot(SCHEMA_VERSION, reserved.map { AttemptEntry.from(it) })
                storage.write(json.encodeToString(next).encodeToByteArray())
                // Cancellation after persistence still consumes this attempt, but cannot start output.
                currentCoroutineContext().ensureActive()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                false
            } catch (_: SerializationException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    private companion object { const val SCHEMA_VERSION = 1 }
}

private const val MAX_STORAGE_BYTES = 64 * 1_024

private class AtomicAttemptStorage(private val file: File) : GuidanceAttemptStorage {
    private val atomic = AtomicFile(file)

    override fun read(): ByteArray? {
        // Recognize interrupted AtomicFile generations as existing state, never a fresh ledger.
        if (!file.exists() && !File(file.path + ".bak").exists() && !File(file.path + ".new").exists()) return null
        return atomic.openRead().use { input ->
            val bytes = input.readBytesBounded()
            if (bytes.size > MAX_STORAGE_BYTES) throw IOException("Attempt ledger exceeds capacity")
            bytes
        }
    }

    override fun write(bytes: ByteArray) {
        require(bytes.size <= MAX_STORAGE_BYTES)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            stream.flush()
            // AtomicFile itself can log some sync/rename failures without throwing. Check both.
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            if (read()?.contentEquals(bytes) != true) throw IOException("Attempt ledger commit not verified")
        } catch (failure: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw failure
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        while (output.size() <= MAX_STORAGE_BYTES) {
            val count = read(buffer, 0, minOf(buffer.size, MAX_STORAGE_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

@Serializable
private data class AttemptSnapshot(val schemaVersion: Int, val records: List<AttemptEntry>)

@Serializable
private data class AttemptEntry(
    val profileId: Long,
    val cardId: Long,
    val stage: String,
    val epochMs: Long,
    val elapsedMs: Long,
    val bootId: String,
) {
    fun record() = GuidanceAttemptRecord(profileId, cardId, stage, epochMs, elapsedMs, bootId)
    companion object {
        fun from(record: GuidanceAttemptRecord) = AttemptEntry(record.profileId, record.cardId, record.stage,
            record.epochMs, record.elapsedMs, record.bootId)
    }
}
