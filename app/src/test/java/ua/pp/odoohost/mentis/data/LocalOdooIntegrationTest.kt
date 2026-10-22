package ua.pp.odoohost.mentis.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.auth.LoginResult
import ua.pp.odoohost.mentis.data.auth.SessionStore
import ua.pp.odoohost.mentis.data.odoo.FakeSharedPreferences
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.PersistentCookieJar
import ua.pp.odoohost.mentis.data.project.ProjectRepository

/**
 * Runs against a real Odoo (the local Docker replica), skipped unless configured:
 *
 *     MENTIS_IT_URL=http://localhost:8069 MENTIS_IT_DB=mentis_dev MENTIS_IT_LOGIN=localadmin \
 *     MENTIS_IT_PASSWORD=... ./gradlew testDebugUnitTest --tests '*LocalOdooIntegrationTest'
 */
class LocalOdooIntegrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `logs in and lists projects`() = runTest {
        val password = System.getenv("MENTIS_IT_PASSWORD")
        assumeTrue("MENTIS_IT_PASSWORD not set", !password.isNullOrEmpty())

        val cookies = PersistentCookieJar(FakeSharedPreferences())
        val client = OdooClient(OkHttpClient.Builder().cookieJar(cookies).build())
        val store = SessionStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("s.preferences_pb") })
        val auth = AuthRepository(client, cookies, store)

        val result = auth.login(
            System.getenv("MENTIS_IT_URL") ?: "http://localhost:8069",
            System.getenv("MENTIS_IT_DB").orEmpty(),
            System.getenv("MENTIS_IT_LOGIN") ?: "localadmin",
            password!!,
        )
        assertTrue("expected a password-only user, got $result", result is LoginResult.Success)

        val projects = ProjectRepository(client).projects()
        println("Projects: " + projects.joinToString { "${it.name} (${it.taskCount}, queue=${it.queue})" })
        assertTrue(projects.isNotEmpty())
        auth.logout()
    }
}
