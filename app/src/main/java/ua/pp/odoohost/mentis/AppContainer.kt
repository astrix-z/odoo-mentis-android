package ua.pp.odoohost.mentis

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import okhttp3.OkHttpClient
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.auth.SessionStore
import ua.pp.odoohost.mentis.data.files.AttachmentFiles
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.PersistentCookieJar
import ua.pp.odoohost.mentis.data.project.ProjectRepository
import ua.pp.odoohost.mentis.data.task.ChatterRepository
import ua.pp.odoohost.mentis.data.task.TaskRepository
import java.util.concurrent.TimeUnit

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** Hand-wired dependencies; small enough not to need a DI framework yet. */
class AppContainer(context: Context) {
    val cookies = PersistentCookieJar(context.getSharedPreferences("odoo_cookies", Context.MODE_PRIVATE))

    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val odoo = OdooClient(http)
    val sessionStore = SessionStore(context.sessionDataStore)
    val auth = AuthRepository(odoo, cookies, sessionStore)
    val projects = ProjectRepository(odoo)
    val tasks = TaskRepository(odoo)
    val chatter = ChatterRepository(odoo, tasks)
    val files = AttachmentFiles(context.applicationContext, http)
}
