package ua.pp.odoohost.mentis.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The logged-in Odoo user, as returned by `/web/session/authenticate`. */
data class UserSession(
    val serverUrl: String,
    val database: String,
    val uid: Int,
    val login: String,
    val name: String,
    /** Odoo language code, e.g. `ru_RU` or `uk_UA`. */
    val lang: String,
    /** The raw `user_context` JSON, sent back with every model call. */
    val userContext: String,
)

/** What the login form is pre-filled with after a logout. */
data class LoginDefaults(val serverUrl: String?, val database: String?, val login: String?)

/** The password is never stored: Odoo's session cookie is what keeps the user logged in. */
class SessionStore(private val store: DataStore<Preferences>) {

    val session: Flow<UserSession?> = store.data.map { p ->
        val uid = p[UID] ?: return@map null
        UserSession(
            serverUrl = p[SERVER] ?: return@map null,
            database = p[DATABASE] ?: return@map null,
            uid = uid,
            login = p[LOGIN].orEmpty(),
            name = p[NAME].orEmpty(),
            lang = p[LANG].orEmpty(),
            userContext = p[CONTEXT] ?: "{}",
        )
    }

    suspend fun loginDefaults(): LoginDefaults = store.data.first().let {
        LoginDefaults(it[SERVER], it[DATABASE], it[LOGIN])
    }

    suspend fun save(session: UserSession) {
        store.edit {
            it[SERVER] = session.serverUrl
            it[DATABASE] = session.database
            it[UID] = session.uid
            it[LOGIN] = session.login
            it[NAME] = session.name
            it[LANG] = session.lang
            it[CONTEXT] = session.userContext
        }
    }

    suspend fun clear() {
        store.edit {
            it.remove(UID)
            it.remove(NAME)
            it.remove(CONTEXT)
        }
    }

    private companion object {
        val SERVER = stringPreferencesKey("server_url")
        val DATABASE = stringPreferencesKey("database")
        val UID = intPreferencesKey("uid")
        val LOGIN = stringPreferencesKey("login")
        val NAME = stringPreferencesKey("name")
        val LANG = stringPreferencesKey("lang")
        val CONTEXT = stringPreferencesKey("user_context")
    }
}
