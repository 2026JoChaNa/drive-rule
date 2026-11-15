package com.jochana.driverule.core.data.remote

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Tag
import retrofit2.http.Url

/**
 * Unauthenticated app API transport. Construction is lazy with respect to network I/O.
 * No profile, device, location, question text, cache, or scheduling dependency belongs here.
 */
class BackendApiClient private constructor(
    private val service: AppBackendService,
    private val http: OkHttpClient,
    private val json: Json,
    private val manifestUrl: HttpUrl,
) : Closeable {
    suspend fun syncCards(syncToken: String? = null): BackendResult<CardSyncDto> =
        execute({ service.syncCards(syncToken) }) { body ->
            json.decodeFromString<CardSyncDto>(requiredBody(body))
        }

    suspend fun schoolZoneManifest(ifNoneMatch: String? = null): BackendResult<SchoolZoneManifestDto> =
        execute({ service.schoolZoneManifest(ifNoneMatch, GeoReadLimit(64 * 1_024)) }, allowNotModified = true) { body ->
            json.decodeFromString<SchoolZoneManifestDto>(requiredBody(body))
        }

    /** Resolve against the manifest document, with no cross-origin download or credentials. */
    internal fun resolveSchoolZoneFileUrl(reference: String): HttpUrl? {
        if (reference.isBlank() || reference.length > 4_096 || reference != reference.trim() ||
            reference.any { it.code < 32 || it == '\\' }) return null
        val resolved = manifestUrl.resolve(reference) ?: return null
        return resolved.takeIf {
            it.scheme == manifestUrl.scheme && it.host == manifestUrl.host && it.port == manifestUrl.port &&
                it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
        }
    }

    /** Only the bounded geo repository calls this; an invalid reference never starts I/O. */
    internal suspend fun downloadSchoolZoneFile(reference: String): BackendResult<ByteArray> {
        val url = resolveSchoolZoneFileUrl(reference) ?: return BackendResult.MalformedResponse(0)
        return withContext(Dispatchers.IO) {
            execute({ service.schoolZoneFile(url, GeoReadLimit(2 * 1_024 * 1_024)) }) { body ->
                body?.bytes() ?: throw SerializationException("Expected a school zone file")
            }
        }
    }

    suspend fun reportCardError(cardId: Long, cardVersion: Int): BackendResult<Unit> =
        execute({ service.reportCardError(cardId, CardErrorReportDto(cardVersion)) }) { Unit }

    suspend fun reportAnswerHold(topic: String, conditions: Map<String, String>? = null): BackendResult<Unit> =
        execute({ service.reportAnswerHold(AnswerHoldDto(topic, conditions?.toMap())) }) { Unit }

    private suspend fun <T> execute(
        request: suspend () -> Response<ResponseBody>,
        allowNotModified: Boolean = false,
        decode: (ResponseBody?) -> T,
    ): BackendResult<T> {
        try {
            val response = request()
            try {
                currentCoroutineContext().ensureActive()
                val status = response.code()
                val etag = response.headers()["ETag"]
                val result = when {
                    allowNotModified && status == 304 -> BackendResult.NotModified(etag)
                    !response.isSuccessful -> {
                        val problem = response.errorBody()?.let { body ->
                            try {
                                json.decodeFromString<ProblemDetailsDto>(body.string())
                            } catch (_: SerializationException) {
                                null
                            } catch (_: IOException) {
                                null // Retain the HTTP status if its explanation is unreadable.
                            }
                        }
                        BackendResult.HttpFailure(status, problem)
                    }
                    else -> try {
                        BackendResult.Success(decode(response.body()), status, etag)
                    } catch (_: SerializationException) {
                        BackendResult.MalformedResponse(status)
                    }
                }
                currentCoroutineContext().ensureActive()
                return result
            } finally {
                response.body()?.close()
                response.errorBody()?.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (tooLarge: GeoPayloadTooLargeException) {
            currentCoroutineContext().ensureActive()
            return BackendResult.MalformedResponse(tooLarge.statusCode)
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            return BackendResult.NetworkFailure
        }
    }

    override fun close() {
        http.dispatcher.cancelAll()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    companion object {
        fun create(baseUrl: String): BackendApiClient {
            val url = baseUrl.toHttpUrl()
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "API base URL must not contain credentials, query, or fragment"
            }
            val normalizedUrl = if (url.encodedPath.endsWith('/')) url
                else url.newBuilder().addPathSegment("").build()
            val json = Json { ignoreUnknownKeys = true }
            val http = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val limit = chain.request().tag(GeoReadLimit::class.java)
                    val body = response.body
                    if (limit == null || body == null) response else {
                        // Application interceptors see transparent-gzip decompressed bytes. This
                        // also bounds Retrofit's buffering of non-2xx error response bodies.
                        if (body.contentLength() > limit.maxBytes) {
                            body.close()
                            throw GeoPayloadTooLargeException(response.code)
                        }
                        response.newBuilder().body(BoundedGeoBody(body, limit.maxBytes, response.code)).build()
                    }
                }
                .addInterceptor { chain ->
                    val request = chain.request()
                    val body = request.body
                    val singleUse = if (body == null) request else request.newBuilder()
                        .method(request.method, SingleUseRequestBody(body)).build()
                    chain.proceed(singleUse)
                }
                .build()
            val service = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AppBackendService::class.java)
            return BackendApiClient(service, http, json,
                requireNotNull(normalizedUrl.resolve("api/v1/geo/school-zones")))
        }

        private fun requiredBody(body: ResponseBody?): String =
            body?.string() ?: throw SerializationException("Expected a JSON response body")
    }
}

internal data class GeoReadLimit(val maxBytes: Int)
private class GeoPayloadTooLargeException(val statusCode: Int) : IOException("Geo response exceeds capacity")

private class BoundedGeoBody(
    private val delegate: ResponseBody,
    limit: Int,
    statusCode: Int,
) : ResponseBody() {
    private val boundedSource = object : ForwardingSource(delegate.source()) {
        private var consumed = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            val count = super.read(sink, minOf(byteCount, limit + 1L - consumed))
            if (count > 0) consumed += count
            if (consumed > limit) throw GeoPayloadTooLargeException(statusCode)
            return count
        }
    }.buffer()
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun source() = boundedSource
}

private class SingleUseRequestBody(private val delegate: RequestBody) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    override fun isOneShot() = true
}

internal interface AppBackendService {
    @GET("api/v1/cards/sync")
    suspend fun syncCards(@Query("syncToken") syncToken: String?): Response<ResponseBody>

    @GET("api/v1/geo/school-zones")
    @Streaming
    suspend fun schoolZoneManifest(@Header("If-None-Match") etag: String?, @Tag limit: GeoReadLimit): Response<ResponseBody>

    @GET
    @Streaming
    suspend fun schoolZoneFile(@Url url: HttpUrl, @Tag limit: GeoReadLimit): Response<ResponseBody>

    @POST("api/v1/cards/{cardId}/error-reports")
    suspend fun reportCardError(@Path("cardId") cardId: Long, @Body body: CardErrorReportDto): Response<ResponseBody>

    @POST("api/v1/answer-holds")
    suspend fun reportAnswerHold(@Body body: AnswerHoldDto): Response<ResponseBody>
}
