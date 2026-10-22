package ua.pp.odoohost.mentis.data.odoo

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Keeps Odoo's cookies across app restarts: `session_id` (the login) and `td_id`
 * (the "trusted device" mark that lets 2FA users skip the code for 90 days).
 * Session cookies without an expiry are kept as well, since the app replaces the browser.
 */
class PersistentCookieJar(private val prefs: SharedPreferences) : CookieJar {

    private val json = Json { ignoreUnknownKeys = true }
    private val cookies: MutableList<Cookie> = load().toMutableList()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        if (cookies.removeAll { it.expiresAt <= now }) persist()
        return cookies.filter { it.matches(url) }
    }

    /** Drops the login but keeps the trusted-device mark. */
    @Synchronized
    fun clearSession() {
        cookies.removeAll { it.name == SESSION_COOKIE }
        persist()
    }

    @Synchronized
    fun clearAll() {
        cookies.clear()
        persist()
    }

    private fun persist() {
        val stored = cookies.map { StoredCookie.from(it) }
        prefs.edit().putString(KEY, json.encodeToString(ListSerializer(StoredCookie.serializer()), stored)).apply()
    }

    private fun load(): List<Cookie> {
        val text = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(StoredCookie.serializer()), text) }
            .getOrDefault(emptyList())
            .map { it.toCookie() }
    }

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    ) {
        fun toCookie(): Cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
            .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
            .apply { if (secure) secure() }
            .apply { if (httpOnly) httpOnly() }
            .build()

        companion object {
            fun from(c: Cookie) =
                StoredCookie(c.name, c.value, c.expiresAt, c.domain, c.path, c.secure, c.httpOnly, c.hostOnly)
        }
    }

    companion object {
        const val SESSION_COOKIE = "session_id"
        private const val KEY = "cookies"
    }
}
