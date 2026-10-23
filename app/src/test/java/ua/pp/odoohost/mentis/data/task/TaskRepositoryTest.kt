package ua.pp.odoohost.mentis.data.task

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.Ref
import java.time.LocalDate

class TaskRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val client = OdooClient(OkHttpClient()).apply {
        setServer(server.url("/").toString())
        userContext = buildJsonObject { put("uid", 7) }
    }
    private val repository = TaskRepository(client)

    @After
    fun tearDown() = server.close()

    private fun result(json: String) = MockResponse(body = """{"jsonrpc":"2.0","id":1,"result":$json}""")

    private fun params(): JsonObject =
        Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject.getValue("params").jsonObject

    @Test
    fun `stages come from read_group with empty stages expanded`() = runTest {
        server.enqueue(
            result(
                """[{"stage_id":[93,"In Progress"],"stage_id_count":3,"__fold":false},
                   {"stage_id":[95,"Done"],"stage_id_count":0,"__fold":true},
                   {"stage_id":false,"stage_id_count":11,"__fold":false}]""",
            ),
        )

        val stages = repository.stages(5, TaskFilter())

        assertEquals(
            listOf(Stage(93, "In Progress", false, 3), Stage(95, "Done", true, 0), Stage(null, "", false, 11)),
            stages,
        )
        val kwargs = params().getValue("kwargs").jsonObject
        assertEquals("""[["project_id","=",5]]""", kwargs["domain"].toString())
        assertEquals("5", kwargs.getValue("context").jsonObject["default_project_id"].toString())
    }

    @Test
    fun `filters become domain terms, each kept whole`() = runTest {
        server.enqueue(result("[]"))

        repository.tasks(5, stageId = null, TaskFilter(mine = true, overdue = true, query = "Bufet"), offset = 40, limit = 40)

        val kwargs = params().getValue("kwargs").jsonObject
        val today = LocalDate.now()
        assertEquals(
            """[["project_id","=",5],["stage_id","=",false],["user_ids","in",[7]],""" +
                """["date_deadline","<","$today"],["is_closed","=",false],""" +
                """"|",["name","ilike","Bufet"],["mentis_outlet_id","ilike","Bufet"]]""",
            kwargs["domain"].toString(),
        )
        assertEquals("40", kwargs["offset"].toString())
    }

    @Test
    fun `cards resolve assignee and tag names once`() = runTest {
        val row = """{"id":50,"name":"Update POS","stage_id":[93,"In Progress"],"priority":"1","kanban_state":"blocked",
            "date_deadline":"2026-10-01","is_closed":false,"user_ids":[7,8],"tag_ids":[3],"mentis_outlet_id":[4,"Melodia"],
            "subtask_count":2,"color":0}"""
        server.enqueue(result("[$row]"))
        server.enqueue(result("""[{"id":7,"name":"Oleg"},{"id":8,"name":"Olena"}]"""))
        server.enqueue(result("""[{"id":3,"name":"POS","color":4}]"""))
        server.enqueue(result("[$row]"))

        val card = repository.tasks(5, 93, TaskFilter(), 0, 40).single()
        repository.tasks(5, 93, TaskFilter(), 0, 40)

        assertEquals(listOf(Ref(7, "Oleg"), Ref(8, "Olena")), card.assignees)
        assertEquals(listOf(Tag(3, "POS", 4)), card.tags)
        assertTrue(card.highPriority)
        assertEquals(KanbanState.Blocked, card.kanbanState)
        assertTrue(card.isOverdue(today = LocalDate.of(2026, 10, 2)))
        assertFalse(card.isOverdue(today = LocalDate.of(2026, 10, 1)))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `update writes only the given fields and announces the change`() = runTest {
        server.enqueue(result("true"))
        var changed: Int? = null
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            changed = repository.changes.first()
        }

        repository.update(
            50,
            TaskValues(
                assigneeIds = listOf(7),
                deadline = Change(null),
                outletId = Change(4),
                description = "Line 1\n<b>2</b>",
            ),
        )

        val vals = params().getValue("args").toString()
        assertEquals(
            """[[50],{"user_ids":[[6,0,[7]]],"date_deadline":false,"mentis_outlet_id":4,""" +
                """"description":"<p>Line 1</p><p>&lt;b&gt;2&lt;/b&gt;</p>"}]""",
            vals,
        )
        assertEquals(50, changed)
        collector.cancel()
    }

    @Test
    fun `nothing to write sends nothing`() = runTest {
        repository.update(50, TaskValues())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `create puts the task in the project`() = runTest {
        server.enqueue(result("61"))

        val id = repository.create(5, TaskValues(name = " Fix printer ", stageId = 93))

        assertEquals(61, id)
        val p = params()
        assertEquals("""[{"name":"Fix printer","stage_id":93,"project_id":5}]""", p["args"].toString())
        assertNull(p.getValue("kwargs").jsonObject["domain"])
    }
}
