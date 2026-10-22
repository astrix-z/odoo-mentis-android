package ua.pp.odoohost.mentis.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ua.pp.odoohost.mentis.data.odoo.FakeSharedPreferences
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.PersistentCookieJar

class AuthRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val cookies = PersistentCookieJar(FakeSharedPreferences())
    private val client = OdooClient(OkHttpClient.Builder().cookieJar(cookies).build())

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun TestScope.repository(): Pair<AuthRepository, SessionStore> {
        val store = SessionStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("session.preferences_pb") },
        )
        return AuthRepository(client, cookies, store) to store
    }

    private fun json(result: String) = MockResponse(body = """{"jsonrpc":"2.0","id":1,"result":$result}""")

    private val sessionInfo = """{"uid":7,"name":"Olena","username":"olena","db":"prod",
        "user_context":{"lang":"uk_UA","tz":"Europe/Kyiv","uid":7}}"""
    private val sessionExpired =
        MockResponse(body = """{"jsonrpc":"2.0","error":{"code":100,"message":"Odoo Session Expired","data":{"name":"odoo.http.SessionExpiredException","message":"Session expired"}}}""")

    @Test
    fun `password login stores the session`() = runTest {
        val (auth, store) = repository()
        server.enqueue(json(sessionInfo))

        val result = auth.login(server.url("/").toString(), "prod", "olena", "secret")

        val session = (result as LoginResult.Success).session
        assertEquals(7, session.uid)
        assertEquals("uk_UA", session.lang)
        assertEquals(session, store.session.first())
        assertEquals("/web/session/authenticate", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `database is detected when the server lists exactly one`() = runTest {
        val (auth, _) = repository()
        server.enqueue(json("""["prod"]"""))
        server.enqueue(json(sessionInfo))

        auth.login(server.url("/").toString(), "", "olena", "secret")

        assertEquals("/web/database/list", server.takeRequest().url.encodedPath)
        assertTrue(server.takeRequest().body!!.utf8().contains("\"db\":\"prod\""))
    }

    @Test(expected = DatabaseRequiredException::class)
    fun `database is required when listing is disabled`() = runTest {
        val (auth, _) = repository()
        server.enqueue(MockResponse(body = """{"jsonrpc":"2.0","error":{"code":200,"message":"x","data":{"name":"odoo.exceptions.AccessDenied","message":"Access Denied"}}}"""))

        auth.login(server.url("/").toString(), "", "olena", "secret")
    }

    @Test
    fun `two-factor login asks for the code and posts it with the csrf token`() = runTest {
        val (auth, store) = repository()
        server.enqueue(json("""{"uid":null}"""))
        server.enqueue(MockResponse(body = """<form><input type="hidden" name="csrf_token" value="tok123"/></form>"""))
        server.enqueue(sessionExpired)

        val result = auth.login(server.url("/").toString(), "prod", "olena", "secret")
        assertEquals(LoginResult.TotpRequired, result)
        assertNull(store.session.first())

        server.enqueue(MockResponse(body = "<html>web client</html>"))
        server.enqueue(json(sessionInfo))
        val session = auth.submitTotp("123 456", rememberDevice = true)

        assertEquals(7, session.uid)
        repeat(3) { server.takeRequest() }
        val post = server.takeRequest()
        assertEquals("/web/login/totp", post.url.encodedPath)
        assertEquals("csrf_token=tok123&totp_token=123456&remember=1", post.body!!.utf8())
    }

    @Test(expected = InvalidTotpException::class)
    fun `a wrong code is reported`() = runTest {
        val (auth, _) = repository()
        server.enqueue(json("""{"uid":null}"""))
        server.enqueue(MockResponse(body = """<input type="hidden" name="csrf_token" value="tok"/>"""))
        server.enqueue(sessionExpired)
        auth.login(server.url("/").toString(), "prod", "olena", "secret")

        server.enqueue(MockResponse(body = """<input type="hidden" name="csrf_token" value="tok"/>"""))
        server.enqueue(sessionExpired)
        auth.submitTotp("000000", rememberDevice = false)
    }

    @Test
    fun `trusted device skips the code`() = runTest {
        val (auth, _) = repository()
        server.enqueue(json("""{"uid":null}"""))
        server.enqueue(MockResponse(body = "<html>web client</html>"))
        server.enqueue(json(sessionInfo))

        val result = auth.login(server.url("/").toString(), "prod", "olena", "secret")

        assertEquals(7, (result as LoginResult.Success).session.uid)
    }
}
