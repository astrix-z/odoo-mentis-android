package ua.pp.odoohost.mentis.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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
import ua.pp.odoohost.mentis.data.odoo.ints
import ua.pp.odoohost.mentis.data.project.ProjectRepository
import ua.pp.odoohost.mentis.data.task.Change
import ua.pp.odoohost.mentis.data.task.ChatterRepository
import ua.pp.odoohost.mentis.data.task.KanbanState
import ua.pp.odoohost.mentis.data.task.TaskFilter
import ua.pp.odoohost.mentis.data.task.TaskRepository
import ua.pp.odoohost.mentis.data.task.TaskValues
import kotlinx.serialization.json.JsonArray
import java.time.LocalDate

/**
 * Runs against a real Odoo (the local Docker replica), skipped unless configured. It creates
 * one task, changes it, posts a note with a file, and deletes the task again:
 *
 *     MENTIS_IT_URL=http://localhost:8069 MENTIS_IT_DB=mentis_dev MENTIS_IT_LOGIN=localadmin \
 *     MENTIS_IT_PASSWORD=... ./gradlew testDebugUnitTest --tests '*LocalOdooIntegrationTest'
 */
class LocalOdooIntegrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `logs in and works with projects, tasks and chatter`() = runTest {
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

        // Kanban of the project with the most tasks, every column fully read.
        val tasks = TaskRepository(client)
        val project = projects.maxBy { it.taskCount }
        val stages = tasks.stages(project.id, TaskFilter())
        println("Stages of ${project.name}: " + stages.joinToString { "${it.name.ifEmpty { "<none>" }}=${it.count}" })
        for (stage in stages) {
            assertEquals(stage.count, tasks.tasks(project.id, stage.id, TaskFilter(), 0, 500).size)
        }
        tasks.stages(project.id, TaskFilter(mine = true, overdue = true, query = "a"))

        // Full cycle on a throwaway task, removed afterwards.
        val stageIds = tasks.projectStages(project.id)
        val user = tasks.searchUsers("").first()
        val outlet = tasks.searchOutlets("").firstOrNull()
        val id = tasks.create(project.id, TaskValues(name = "App integration test", stageId = stageIds.firstOrNull()?.id))
        try {
            tasks.update(
                id,
                TaskValues(
                    assigneeIds = listOf(user.id),
                    deadline = Change(LocalDate.now().minusDays(1)),
                    highPriority = true,
                    kanbanState = KanbanState.Blocked,
                    outletId = outlet?.let { Change(it.id) },
                    description = "Line 1\nLine <2>",
                    stageId = stageIds.lastOrNull()?.id,
                ),
            )
            val task = tasks.task(id)
            assertEquals(listOf(user.id), task.assignees.map { it.id })
            assertEquals(user.name, task.assignees.single().name)
            assertTrue(task.highPriority)
            assertEquals(KanbanState.Blocked, task.kanbanState)
            assertEquals(outlet?.id, task.outlet?.id)
            assertTrue(task.description, task.description.contains("Line &lt;2&gt;"))
            println("Task legends: ${task.kanbanLegends}")

            val chatter = ChatterRepository(client, tasks)
            val file = chatter.upload("note.txt", "text/plain", "hello".toByteArray())
            val posted = chatter.post(id, "Checked <ok>", isNote = true, attachmentIds = listOf(file.id))
            assertEquals(listOf(file.id), posted.attachments.map { it.id })

            val messages = chatter.messages(id)
            println("Messages: " + messages.joinToString { "${it.author?.name}: ${it.body} ${it.trackings}" })
            assertTrue(messages.first().body.contains("Checked &lt;ok&gt;"))
            assertTrue(messages.any { m -> m.trackings.isNotEmpty() })

            val discard = chatter.upload("drop.txt", "text/plain", "x".toByteArray())
            chatter.discard(discard.id)
        } finally {
            client.callKw("project.task", "unlink", args = JsonArray(listOf(ints(listOf(id)))))
        }
        auth.logout()
    }
}
