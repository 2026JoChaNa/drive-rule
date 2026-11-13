package com.jochana.driverule.core.data.remote

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Unauthenticated app API transport. Construction is lazy with respect to network I/O.
 * No profile, device, location, question text, cache, or scheduling dependency belongs here.
 */
class BackendApiClient private constructor(
    private val service: AppBackendService,
    private val http: OkHttpClient,
    private val json: Json,
) : Closeable {
    suspend fun syncCards(syncToken: String? = null): BackendResult<CardSyncDto> =
        execute({ service.syncCards(syncToken) }) { body ->
            json.decodeFromString<CardSyncDto>(requiredBody(body))
        }

    suspend fun schoolZoneManifest(ifNoneMatch: String? = null): BackendResult<SchoolZoneManifestDto> =
        execute({ service.schoolZoneManifest(ifNoneMatch) }, allowNotModified = true) { body ->
            json.decodeFromString<SchoolZoneManifestDto>(requiredBody(body))
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
            return BackendApiClient(service, http, json)
        }

        private fun requiredBody(body: ResponseBody?): String =
            body?.string() ?: throw SerializationException("Expected a JSON response body")
    }
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
    suspend fun schoolZoneManifest(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    @POST("api/v1/cards/{cardId}/error-reports")
    suspend fun reportCardError(@Path("cardId") cardId: Long, @Body body: CardErrorReportDto): Response<ResponseBody>

    @POST("api/v1/answer-holds")
    suspend fun reportAnswerHold(@Body body: AnswerHoldDto): Response<ResponseBody>
}
