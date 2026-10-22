package ua.pp.odoohost.mentis.data.project

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.Ref

class ProjectRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val client = OdooClient(OkHttpClient()).apply { setServer(server.url("/").toString()) }

    @After
    fun tearDown() = server.close()

    @Test
    fun `projects are parsed with favourites first`() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"jsonrpc":"2.0","id":1,"result":[
                {"id":1,"name":"IT","is_favorite":false,"task_count":4,"color":0,"user_id":false,
                 "partner_id":false,"label_tasks":"Tasks","mentis_queue":"it"},
                {"id":2,"name":"Service","is_favorite":true,"task_count":1,"color":10,"user_id":[5,"Ivan"],
                 "partner_id":[9,"Bufet"],"label_tasks":"Requests","mentis_queue":false}]}""",
            ),
        )

        val projects = ProjectRepository(client).projects()

        assertEquals(listOf(2, 1), projects.map { it.id })
        assertEquals(Ref(5, "Ivan"), projects[0].manager)
        assertNull(projects[0].queue)
        assertEquals("it", projects[1].queue)
        assertNull(projects[1].manager)
    }
}
