package ua.pp.odoohost.mentis.data.auth

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.OdooException
import ua.pp.odoohost.mentis.data.odoo.PersistentCookieJar
import ua.pp.odoohost.mentis.data.odoo.intOrNull
import ua.pp.odoohost.mentis.data.odoo.stringOrNull

sealed interface LoginResult {
    data class Success(val session: UserSession) : LoginResult
    /** Password accepted; the user has 2FA on and must enter the code from their authenticator app. */
    data object TotpRequired : LoginResult
}

/** The server does not list its databases, so the user has to type the name. */
class DatabaseRequiredException : Exception("Database name required")

class InvalidTotpException : Exception("Invalid authentication code")

class AuthRepository(
    private val client: OdooClient,
    private val cookies: PersistentCookieJar,
    private val store: SessionStore,
) {
    private var pending: PendingLogin? = null

    /** Points the client at the saved session on app start; returns it, or null if logged out. */
    fun restore(session: UserSession?) {
        session ?: return
        client.setServer(session.serverUrl)
        client.userContext = parseContext(session.userContext)
    }

    suspend fun login(serverUrl: String, database: String, login: String, password: String): LoginResult {
        val server = OdooClient.normalizeServerUrl(serverUrl)
        client.setServer(server)
        cookies.clearSession()
        val db = database.trim().ifEmpty { detectDatabase() ?: throw DatabaseRequiredException() }

        val info = client.rpc(
            "/web/session/authenticate",
            buildJsonObject {
                put("db", db)
                put("login", login.trim())
                put("password", password)
            },
        ).jsonObject

        if (info["uid"]?.intOrNull() != null) return LoginResult.Success(finish(server, db, info))

        // 2FA: the session now holds a half-authenticated user. A trusted device cookie
        // finishes the login on the TOTP page by itself; otherwise the page gives us a form.
        pending = PendingLogin(server, db)
        val page = client.getPage(TOTP_PATH).use { it.body.string() }
        sessionInfo()?.let { return LoginResult.Success(finish(server, db, it)) }
        pending = PendingLogin(server, db, csrfToken(page))
        return LoginResult.TotpRequired
    }

    suspend fun submitTotp(code: String, rememberDevice: Boolean): UserSession {
        val login = checkNotNull(pending) { "No login waiting for a code" }
        val fields = buildMap {
            put("csrf_token", login.csrfToken.orEmpty())
            put("totp_token", code.filterNot(Char::isWhitespace))
            if (rememberDevice) put("remember", "1")
        }
        val page = client.postForm(TOTP_PATH, fields).use { it.body.string() }
        val info = sessionInfo()
        if (info == null) {
            csrfToken(page)?.let { pending = login.copy(csrfToken = it) }
            throw InvalidTotpException()
        }
        return finish(login.server, login.database, info)
    }

    suspend fun logout() {
        runCatching { client.rpc("/web/session/destroy") }
        cookies.clearSession()
        store.clear()
        client.userContext = JsonObject(emptyMap())
    }

    /** Uses the only database when the server lists them; null when listing is disabled or ambiguous. */
    private suspend fun detectDatabase(): String? = try {
        (client.rpc("/web/database/list") as? JsonArray)?.singleOrNull()?.stringOrNull()
    } catch (_: OdooException.AccessDenied) {
        null
    } catch (_: OdooException.Server) {
        null
    }

    private suspend fun sessionInfo(): JsonObject? = try {
        client.rpc("/web/session/get_session_info").jsonObject.takeIf { it["uid"]?.intOrNull() != null }
    } catch (_: OdooException.SessionExpired) {
        null
    }

    private suspend fun finish(server: String, db: String, info: JsonObject): UserSession {
        pending = null
        val context = info["user_context"] as? JsonObject ?: JsonObject(emptyMap())
        val session = UserSession(
            serverUrl = server,
            database = info["db"]?.stringOrNull() ?: db,
            uid = checkNotNull(info["uid"]?.intOrNull()),
            login = info["username"]?.stringOrNull().orEmpty(),
            name = info["name"]?.stringOrNull().orEmpty(),
            lang = context["lang"]?.stringOrNull().orEmpty(),
            userContext = context.toString(),
        )
        client.userContext = context
        store.save(session)
        return session
    }

    private data class PendingLogin(val server: String, val database: String, val csrfToken: String? = null)

    companion object {
        private const val TOTP_PATH = "/web/login/totp"
        private val CSRF = Regex("""name="csrf_token"\s+value="([^"]+)"""")

        internal fun csrfToken(html: String): String? = CSRF.find(html)?.groupValues?.get(1)

        private fun parseContext(text: String): JsonObject =
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject }
                .getOrDefault(JsonObject(emptyMap()))
    }
}
