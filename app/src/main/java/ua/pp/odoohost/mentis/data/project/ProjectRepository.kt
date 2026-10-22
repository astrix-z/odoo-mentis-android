package ua.pp.odoohost.mentis.data.project

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.Ref
import ua.pp.odoohost.mentis.data.odoo.booleanOrFalse
import ua.pp.odoohost.mentis.data.odoo.intOrNull
import ua.pp.odoohost.mentis.data.odoo.refOrNull
import ua.pp.odoohost.mentis.data.odoo.stringOrNull

data class Project(
    val id: Int,
    val name: String,
    val isFavorite: Boolean,
    /** Open tasks, as on the project's kanban card in Odoo. */
    val taskCount: Int,
    /** Odoo's colour index (0–11) of the kanban card. */
    val color: Int,
    val manager: Ref?,
    val customer: Ref?,
    /** What the project calls its tasks, e.g. "Tasks" or "Requests". */
    val tasksLabel: String,
    /** mentis_task_access department queue: it, service, accounting, or null. */
    val queue: String?,
)

class ProjectRepository(private val client: OdooClient) {

    /** Projects the user can see, favourites first, in Odoo's own order otherwise. */
    suspend fun projects(): List<Project> {
        val rows = client.callKw(
            "project.project",
            "search_read",
            kwargs = buildJsonObject {
                put("domain", JsonArray(emptyList()))
                put("fields", JsonArray(FIELDS.map(::JsonPrimitive)))
            },
        ) as JsonArray
        return rows.map { parse(it.jsonObject) }.sortedByDescending { it.isFavorite }
    }

    private fun parse(row: JsonObject) = Project(
        id = checkNotNull(row["id"]?.intOrNull()),
        name = row["name"]?.stringOrNull().orEmpty(),
        isFavorite = row["is_favorite"]?.booleanOrFalse() ?: false,
        taskCount = row["task_count"]?.intOrNull() ?: 0,
        color = row["color"]?.intOrNull() ?: 0,
        manager = row["user_id"]?.refOrNull(),
        customer = row["partner_id"]?.refOrNull(),
        tasksLabel = row["label_tasks"]?.stringOrNull().orEmpty(),
        queue = row["mentis_queue"]?.stringOrNull(),
    )

    private companion object {
        val FIELDS = listOf(
            "name", "is_favorite", "task_count", "color", "user_id", "partner_id", "label_tasks", "mentis_queue",
        )
    }
}
