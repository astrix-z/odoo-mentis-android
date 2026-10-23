package ua.pp.odoohost.mentis.data.task

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.Ref
import java.time.Instant

class ChatterRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val client = OdooClient(OkHttpClient()).apply { setServer(server.url("/").toString()) }
    private val chatter = ChatterRepository(client, TaskRepository(client))

    @After
    fun tearDown() = server.close()

    private fun result(json: String) = MockResponse(body = """{"jsonrpc":"2.0","id":1,"result":$json}""")

    private fun request(): Pair<String, JsonObject> = server.takeRequest().let {
        it.url.encodedPath to Json.parseToJsonElement(it.body!!.utf8()).jsonObject.getValue("params").jsonObject
    }

    private val message = """{"id":885,"body":"<p>Done</p>","date":"2026-10-21 13:45:48","author":{"id":83,"name":"Oleg"},
        "is_note":true,"subtype_description":false,
        "trackingValues":[{"changedField":"Stage","newValue":{"value":"Done"},"oldValue":{"value":""}},
                          {"changedField":"Priority","newValue":{"value":1},"oldValue":{"value":false}}],
        "attachment_ids":[{"id":12,"name":"photo.jpg","mimetype":"image/jpeg"}]}"""

    @Test
    fun `messages are parsed with tracking and attachments`() = runTest {
        server.enqueue(result("[$message]"))

        val m = chatter.messages(50, beforeId = 900).single()

        assertEquals(Ref(83, "Oleg"), m.author)
        assertEquals(Instant.parse("2026-10-21T13:45:48Z"), m.date)
        assertTrue(m.isNote)
        assertEquals(null, m.subtypeDescription)
        assertEquals(listOf(Tracking("Stage", "", "Done"), Tracking("Priority", "", "1")), m.trackings)
        assertTrue(m.attachments.single().isImage)
        val (path, params) = request()
        assertEquals("/mail/thread/messages", path)
        assertEquals("900", params["max_id"].toString())
    }

    @Test
    fun `posting escapes the text and links the uploaded files`() = runTest {
        server.enqueue(result(message))

        chatter.post(50, " a < b\nnext ", isNote = false, attachmentIds = listOf(12))

        val (path, params) = request()
        assertEquals("/mail/message/post", path)
        assertEquals(
            """{"body":"a &lt; b<br>next","message_type":"comment","subtype_xmlid":"mail.mt_comment","attachment_ids":[12]}""",
            params["post_data"].toString(),
        )
    }

    @Test
    fun `uploads stay pending until posted`() = runTest {
        server.enqueue(result("12"))

        val attachment = chatter.upload("a.txt", "text/plain", "hi".toByteArray())

        assertEquals(Attachment(12, "a.txt", "text/plain"), attachment)
        val vals = request().second.getValue("args").toString()
        assertEquals("""[{"name":"a.txt","mimetype":"text/plain","datas":"aGk=","res_model":"mail.compose.message","res_id":0}]""", vals)
    }
}
