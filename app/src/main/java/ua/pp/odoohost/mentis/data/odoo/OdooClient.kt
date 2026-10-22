package ua.pp.odoohost.mentis.data.odoo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Talks to Odoo's web controllers over JSON-RPC, as the logged-in user of the session cookie.
 *
 * Odoo 16 has no REST API; the web client itself uses `/web/dataset/call_kw`, so record rules
 * (including mentis_task_access) apply exactly as in the browser.
 */
class OdooClient(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    @Volatile
    private var baseUrl: HttpUrl? = null

    /** Context sent with every model call, so Odoo answers in the user's language and timezone. */
    @Volatile
    var userContext: JsonObject = JsonObject(emptyMap())

    private val requestId = AtomicInteger()

    fun setServer(url: String) {
        baseUrl = normalizeServerUrl(url).toHttpUrl()
    }

    fun url(path: String): HttpUrl =
        checkNotNull(baseUrl) { "Server URL is not set" }.resolve(path)
            ?: throw IllegalArgumentException("Bad path $path")

    /** Calls a `type='json'` route and returns its `result`. */
    suspend fun rpc(path: String, params: JsonObject = JsonObject(emptyMap())): JsonElement {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "call")
            put("id", requestId.incrementAndGet())
            put("params", params)
        }
        val request = Request.Builder()
            .url(url(path))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val text = execute(request).use { response ->
            if (!response.isSuccessful) throw OdooException.Http(response.code)
            response.body.string()
        }
        return parseResponse(json.parseToJsonElement(text).jsonObject)
    }

    /** Calls a model method, like `env[model].method(*args, **kwargs)` on the server. */
    suspend fun callKw(
        model: String,
        method: String,
        args: JsonArray = JsonArray(emptyList()),
        kwargs: JsonObject = JsonObject(emptyMap()),
    ): JsonElement {
        val context = JsonObject(userContext + (kwargs["context"]?.jsonObject ?: emptyMap()))
        return rpc(
            "/web/dataset/call_kw/$model/$method",
            buildJsonObject {
                put("model", model)
                put("method", method)
                put("args", args)
                put("kwargs", JsonObject(kwargs + ("context" to context)))
            },
        )
    }

    /** Fetches an HTML page; used only for the TOTP login step, which has no JSON route. */
    suspend fun getPage(path: String): Response = execute(Request.Builder().url(url(path)).build())

    suspend fun postForm(path: String, fields: Map<String, String>): Response {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url(path)).post(form).build())
    }

    private suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw OdooException.Network(e)
        }
    }

    internal fun parseResponse(envelope: JsonObject): JsonElement {
        val error = envelope["error"]?.takeUnless { it is JsonNull }?.jsonObject
            ?: return envelope["result"] ?: JsonNull
        val data = error["data"]?.takeUnless { it is JsonNull }?.jsonObject
        val name = data?.get("name")?.stringOrNull().orEmpty()
        val message = data?.get("message")?.stringOrNull()
            ?: error["message"]?.stringOrNull()
            ?: "Odoo error"
        throw when {
            name == "odoo.http.SessionExpiredException" || error["code"]?.intOrNull() == 100 ->
                OdooException.SessionExpired(message)
            name == "odoo.exceptions.AccessDenied" -> OdooException.AccessDenied(message)
            name == "odoo.exceptions.AccessError" -> OdooException.AccessError(message)
            else -> OdooException.Server(name, message)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Accepts "odoo.example.com", "https://odoo.example.com/web#..." and the like. */
        fun normalizeServerUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            val parsed = url.toHttpUrl()
            return parsed.newBuilder().encodedPath("/").query(null).fragment(null).build()
                .toString().trimEnd('/')
        }
    }
}

sealed class OdooException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: IOException) : OdooException(cause.message ?: "Network error", cause)
    class Http(val code: Int) : OdooException("HTTP $code")
    class SessionExpired(message: String) : OdooException(message)
    class AccessDenied(message: String) : OdooException(message)
    class AccessError(message: String) : OdooException(message)
    class Server(val name: String, message: String) : OdooException(message)
}
