package ua.pp.odoohost.mentis.data.odoo

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OdooClientTest {
    private val server = MockWebServer()
    private val client = OdooClient(OkHttpClient())

    @Before
    fun setUp() {
        server.start()
        client.setServer(server.url("/").toString())
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `call_kw posts to the model route with the user context`() = runTest {
        client.userContext = buildJsonObject { put("lang", "uk_UA") }
        server.enqueue(MockResponse(body = """{"jsonrpc":"2.0","id":1,"result":[1,2]}"""))

        val result = client.callKw("project.project", "search", kwargs = buildJsonObject { put("limit", 5) })

        assertEquals("[1,2]", result.toString())
        val request = server.takeRequest()
        assertEquals("/web/dataset/call_kw/project.project/search", request.url.encodedPath)
        val params = Json.parseToJsonElement(request.body!!.utf8()).jsonObject["params"]!!.jsonObject
        assertEquals(JsonPrimitive("project.project"), params["model"])
        val kwargs = params["kwargs"]!!.jsonObject
        assertEquals(JsonPrimitive(5), kwargs["limit"])
        assertEquals(JsonPrimitive("uk_UA"), kwargs["context"]!!.jsonObject["lang"])
    }

    @Test
    fun `odoo errors map to typed exceptions`() = runTest {
        fun error(name: String, code: Int = 200) =
            MockResponse(body = """{"jsonrpc":"2.0","error":{"code":$code,"message":"x","data":{"name":"$name","message":"msg"}}}""")
        server.enqueue(error("odoo.http.SessionExpiredException", code = 100))
        server.enqueue(error("odoo.exceptions.AccessDenied"))
        server.enqueue(error("odoo.exceptions.AccessError"))
        server.enqueue(error("odoo.exceptions.UserError"))

        assertThrows(OdooException.SessionExpired::class.java) { kotlinx.coroutines.runBlocking { client.rpc("/x") } }
        assertThrows(OdooException.AccessDenied::class.java) { kotlinx.coroutines.runBlocking { client.rpc("/x") } }
        assertThrows(OdooException.AccessError::class.java) { kotlinx.coroutines.runBlocking { client.rpc("/x") } }
        val server = assertThrows(OdooException.Server::class.java) { kotlinx.coroutines.runBlocking { client.rpc("/x") } }
        assertEquals("msg", server.message)
    }

    @Test
    fun `http failures and unreachable servers are reported`() = runTest {
        server.enqueue(MockResponse(code = 502))
        val http = assertThrows(OdooException.Http::class.java) { kotlinx.coroutines.runBlocking { client.rpc("/x") } }
        assertEquals(502, http.code)

        server.close()
        assertThrows(OdooException.Network::class.java) { kotlinx.coroutines.runBlocking { client.rpc("/x") } }
    }

    @Test
    fun `server urls are normalized`() {
        assertEquals("https://odoo.example.com", OdooClient.normalizeServerUrl(" odoo.example.com/ "))
        assertEquals("https://odoo.example.com", OdooClient.normalizeServerUrl("https://odoo.example.com/web#action=1"))
        assertEquals("http://localhost:8069", OdooClient.normalizeServerUrl("http://localhost:8069/web/login"))
    }

    @Test
    fun `false means no value`() {
        val row = Json.parseToJsonElement("""{"a":false,"b":[7,"Bob"],"c":"x","d":3}""").jsonObject
        assertNull(row["a"]!!.stringOrNull())
        assertNull(row["a"]!!.refOrNull())
        assertEquals(Ref(7, "Bob"), row["b"]!!.refOrNull())
        assertEquals("x", row["c"]!!.stringOrNull())
        assertEquals(3, row["d"]!!.intOrNull())
        assertTrue(!row["a"]!!.booleanOrFalse())
    }
}
