package cn.oryjk.changuan.infrastructure

import cn.oryjk.changuan.application.SessionStore
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ApiError(override val message: String) : Exception(message) {
    class Configuration : ApiError("服务地址配置不正确，请检查 API 配置")
    class Response(val statusCode: Int, message: String) : ApiError(message)
    class InvalidData : ApiError("服务返回的数据无法读取，请稍后重试")
}

data class RawHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface HttpTransport {
    suspend fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): RawHttpResponse
}

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {
    override suspend fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): RawHttpResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (method == "POST") {
            val requestBody = (body ?: "{}").toRequestBody(JSON_MEDIA_TYPE)
            builder.post(requestBody)
        } else {
            builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            RawHttpResponse(
                statusCode = response.code,
                body = response.body?.string().orEmpty(),
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class ApiClient(
    private val baseUrl: String,
    private val store: SessionStore,
    private val transport: HttpTransport = OkHttpTransport(),
) {
    suspend fun <T> send(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JsonObject? = null,
        authenticated: Boolean = false,
        mapper: (JsonObject) -> T,
    ): T {
        val normalizedBase = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        val parsedBase = normalizedBase.toHttpUrlOrNull() ?: throw ApiError.Configuration()
        if (parsedBase.scheme != "https" && parsedBase.scheme != "http") {
            throw ApiError.Configuration()
        }
        val cleanPath = path.trimStart('/')
        val urlBuilder = parsedBase.newBuilder().addPathSegments(cleanPath)
        for ((key, value) in query) {
            urlBuilder.addQueryParameter(key, value)
        }
        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = "application/json"
        if (body != null) {
            headers["Content-Type"] = "application/json"
        }
        if (authenticated) {
            val token = store.token()
            if (!token.isNullOrBlank()) {
                headers["Authorization"] = "Bearer $token"
            }
        }
        val response = try {
            transport.execute(
                url = urlBuilder.build().toString(),
                method = if (body == null) "GET" else "POST",
                headers = headers,
                body = body?.toString(),
            )
        } catch (error: ApiError) {
            throw error
        } catch (error: Exception) {
            throw ApiError.Response(0, error.message ?: "网络连接失败，请检查网络后重试")
        }
        if (response.statusCode !in 200..299) {
            val detail = runCatching {
                JsonParser.parseString(response.body).asJsonObject.optString("detail")
            }.getOrNull()
            throw ApiError.Response(
                statusCode = response.statusCode,
                message = detail?.takeIf { it.isNotBlank() }
                    ?: "请求失败（${response.statusCode}），请稍后重试",
            )
        }
        val root = try {
            JsonParser.parseString(response.body).asJsonObject
        } catch (_: Exception) {
            throw ApiError.InvalidData()
        }
        return try {
            mapper(root)
        } catch (error: ApiError) {
            throw error
        } catch (_: Exception) {
            throw ApiError.InvalidData()
        }
    }
}

internal fun JsonObject.optElement(key: String): JsonElement? {
    val el = get(key) ?: return null
    return if (el.isJsonNull) null else el
}

internal fun JsonObject.reqString(key: String): String =
    optElement(key)?.asString ?: throw ApiError.InvalidData()

internal fun JsonObject.optString(key: String): String? =
    optElement(key)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonObject.reqDouble(key: String): Double =
    optElement(key)?.asDouble ?: throw ApiError.InvalidData()

internal fun JsonObject.optDouble(key: String): Double? =
    optElement(key)?.takeIf { it.isJsonPrimitive }?.asDouble

internal fun JsonObject.reqInt(key: String): Int =
    optElement(key)?.asInt ?: throw ApiError.InvalidData()

internal fun JsonObject.optInt(key: String): Int? =
    optElement(key)?.takeIf { it.isJsonPrimitive }?.asInt

internal fun JsonObject.reqBoolean(key: String): Boolean =
    optElement(key)?.asBoolean ?: throw ApiError.InvalidData()

internal fun JsonObject.optBoolean(key: String): Boolean? =
    optElement(key)?.takeIf { it.isJsonPrimitive }?.asBoolean
