package com.jochana.driverule.core.data.repository

import android.content.Context
import com.jochana.driverule.core.data.local.geo.AtomicSchoolZoneStorage
import com.jochana.driverule.core.data.local.geo.MAX_SCHOOL_ZONE_STORAGE_BYTES
import com.jochana.driverule.core.data.local.geo.SchoolZoneStorage
import com.jochana.driverule.core.data.local.geo.SchoolZoneValidator
import com.jochana.driverule.core.data.remote.BackendApiClient
import com.jochana.driverule.core.data.remote.BackendResult
import com.jochana.driverule.core.data.remote.SchoolZoneManifestDto
import com.jochana.driverule.core.model.SchoolZoneLoadState
import com.jochana.driverule.core.model.SchoolZoneSnapshot
import com.jochana.driverule.core.model.SchoolZoneSyncFailure
import com.jochana.driverule.core.model.SchoolZoneSyncResult
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One app-container instance owns this complete public dataset; vehicle locations never enter it. */
class SchoolZoneRepository internal constructor(
    private val backend: BackendApiClient,
    private val storage: SchoolZoneStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context, backend: BackendApiClient) : this(backend, AtomicSchoolZoneStorage(context))

    private val mutex = Mutex()
    private val initializationMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutableState = MutableStateFlow<SchoolZoneLoadState>(SchoolZoneLoadState.Loading)
    val state: StateFlow<SchoolZoneLoadState> = mutableState.asStateFlow()
    private var cached: CachedDataset? = null

    suspend fun read(): SchoolZoneSnapshot? {
        initialize()
        // StateFlow publishes the immutable snapshot across threads; a download never blocks reads.
        return (state.value as? SchoolZoneLoadState.Ready)?.snapshot
    }

    fun observe(): Flow<SchoolZoneSnapshot?> = flow {
        read()
        emitAll(state.map { (it as? SchoolZoneLoadState.Ready)?.snapshot }.distinctUntilChanged())
    }

    /** Serializes manifest/download/commit, so an older in-flight file cannot replace a newer one. */
    suspend fun sync(): SchoolZoneSyncResult = mutex.withLock {
        withContext(ioDispatcher) {
            initialize()
            var manifestResult = backend.schoolZoneManifest(cached?.etag)
            var refetched = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (manifestResult is BackendResult.NotModified) {
                    if (cached != null) return@withContext SchoolZoneSyncResult.Unchanged
                    // A 304 without a verified local representation cannot establish readiness.
                    if (refetched) return@withContext invalidData()
                    refetched = true
                    manifestResult = backend.schoolZoneManifest()
                    continue
                }
                val manifestSuccess = manifestResult as? BackendResult.Success<SchoolZoneManifestDto>
                    ?: return@withContext failure(manifestResult)
                if (manifestSuccess.statusCode != 200) return@withContext invalidData()
                val manifest = manifestSuccess.value
                if (!SchoolZoneValidator.validManifest(manifest) || backend.resolveSchoolZoneFileUrl(manifest.url) == null)
                    return@withContext invalidData()
                val etag = safeEtag(manifestSuccess.etag)
                val previous = cached
                if (previous != null && previous.manifest.sha256 == manifest.sha256) {
                    // Equal hashes skip download, but changed metadata must still agree with bytes.
                    val snapshot = SchoolZoneValidator.validate(manifest, previous.bytes)
                        ?: return@withContext invalidData()
                    return@withContext commit(manifest, etag, previous.bytes, snapshot, unchanged = true)
                }
                when (val downloaded = backend.downloadSchoolZoneFile(manifest.url)) {
                    is BackendResult.Success -> {
                        if (downloaded.statusCode != 200) return@withContext invalidData()
                        val snapshot = SchoolZoneValidator.validate(manifest, downloaded.value)
                            ?: return@withContext invalidData()
                        return@withContext commit(manifest, etag, downloaded.value, snapshot, unchanged = false)
                    }
                    is BackendResult.HttpFailure -> {
                        if (downloaded.statusCode == 404 && !refetched) {
                            refetched = true
                            manifestResult = backend.schoolZoneManifest() // Avoid an obsolete ETag.
                        } else return@withContext failure(downloaded)
                    }
                    else -> return@withContext failure(downloaded)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            invalidData()
        }
    }

    private suspend fun initialize() {
        if (state.value != SchoolZoneLoadState.Loading) return
        initializationMutex.withLock { withContext(ioDispatcher) { loadIfNeeded() } }
    }

    private suspend fun loadIfNeeded() {
        if (state.value != SchoolZoneLoadState.Loading) return
        currentCoroutineContext().ensureActive()
        val restored = try {
            storage.read()?.let { encoded ->
                if (encoded.size > MAX_SCHOOL_ZONE_STORAGE_BYTES) return@let null
                val envelope = json.decodeFromString<StoredDataset>(encoded.decodeToString(throwOnInvalidSequence = true))
                if (envelope.format != 1 || backend.resolveSchoolZoneFileUrl(envelope.manifest.url) == null) return@let null
                val bytes = Base64.getDecoder().decode(envelope.payloadBase64)
                val snapshot = SchoolZoneValidator.validate(envelope.manifest, bytes) ?: return@let null
                CachedDataset(envelope.manifest, safeEtag(envelope.etag), bytes, snapshot)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: IOException) { null
        } catch (_: SerializationException) { null
        } catch (_: IllegalArgumentException) { null
        } catch (_: SecurityException) { null }
        currentCoroutineContext().ensureActive()
        cached = restored
        mutableState.value = restored?.let { SchoolZoneLoadState.Ready(it.snapshot) } ?: SchoolZoneLoadState.Unavailable
    }

    private suspend fun commit(
        manifest: SchoolZoneManifestDto,
        etag: String?,
        bytes: ByteArray,
        snapshot: SchoolZoneSnapshot,
        unchanged: Boolean,
    ): SchoolZoneSyncResult {
        try {
            currentCoroutineContext().ensureActive()
            val previous = cached
            if (previous?.manifest != manifest || previous?.etag != etag) {
                val envelope = StoredDataset(1, manifest, etag, Base64.getEncoder().encodeToString(bytes))
                storage.write(json.encodeToString(envelope).encodeToByteArray())
            }
            currentCoroutineContext().ensureActive()
            cached = CachedDataset(manifest, etag, bytes, snapshot)
            mutableState.value = SchoolZoneLoadState.Ready(snapshot)
            return if (unchanged) SchoolZoneSyncResult.Unchanged else SchoolZoneSyncResult.Updated(snapshot)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: IOException) { return storageFailure()
        } catch (_: SecurityException) { return storageFailure()
        } catch (_: IllegalArgumentException) { return storageFailure() }
    }

    private fun safeEtag(value: String?): String? = value?.takeIf {
        it.length in 1..1_024 && it.all { character -> character.code in 32..126 }
    }

    private fun failure(result: BackendResult<*>): SchoolZoneSyncResult.Failure = when (result) {
        BackendResult.NetworkFailure -> SchoolZoneSyncResult.Failure(SchoolZoneSyncFailure.Network, retryable = true)
        is BackendResult.HttpFailure -> SchoolZoneSyncResult.Failure(SchoolZoneSyncFailure.Http,
            retryable = result.statusCode in 500..599 || result.statusCode == 408 || result.statusCode == 429)
        else -> invalidData()
    }

    private fun invalidData() = SchoolZoneSyncResult.Failure(SchoolZoneSyncFailure.InvalidData, retryable = false)
    private fun storageFailure() = SchoolZoneSyncResult.Failure(SchoolZoneSyncFailure.Storage, retryable = true)
}

private data class CachedDataset(
    val manifest: SchoolZoneManifestDto,
    val etag: String?,
    val bytes: ByteArray,
    val snapshot: SchoolZoneSnapshot,
)

@Serializable
private data class StoredDataset(
    val format: Int,
    val manifest: SchoolZoneManifestDto,
    val etag: String?,
    val payloadBase64: String,
)
