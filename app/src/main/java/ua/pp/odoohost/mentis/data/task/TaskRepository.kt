package ua.pp.odoohost.mentis.data.task

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ua.pp.odoohost.mentis.data.odoo.Html
import ua.pp.odoohost.mentis.data.odoo.OdooClient
import ua.pp.odoohost.mentis.data.odoo.Ref
import ua.pp.odoohost.mentis.data.odoo.booleanOrFalse
import ua.pp.odoohost.mentis.data.odoo.intList
import ua.pp.odoohost.mentis.data.odoo.intOrNull
import ua.pp.odoohost.mentis.data.odoo.ints
import ua.pp.odoohost.mentis.data.odoo.refOrNull
import ua.pp.odoohost.mentis.data.odoo.setIds
import ua.pp.odoohost.mentis.data.odoo.stringOrNull
import ua.pp.odoohost.mentis.data.odoo.strings
import ua.pp.odoohost.mentis.data.odoo.term
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class TaskRepository(private val client: OdooClient) {

    private val userNames = ConcurrentHashMap<Int, String>()
    private val tags = ConcurrentHashMap<Int, Tag>()

    private val _changes = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** Ids of tasks this app has just changed, so open screens can reload them. */
    val changes: SharedFlow<Int> = _changes.asSharedFlow()

    fun notifyChanged(taskId: Int) {
        _changes.tryEmit(taskId)
    }

    /**
     * The project's kanban columns with task counts under [filter], in Odoo's stage order.
     * The project's empty stages are included, like in the web client.
     */
    suspend fun stages(projectId: Int, filter: TaskFilter): List<Stage> {
        val groups = client.callKw(
            "project.task",
            "read_group",
            kwargs = buildJsonObject {
                put("domain", domain(projectId, filter))
                put("fields", strings(listOf("stage_id")))
                put("groupby", strings(listOf("stage_id")))
                put("lazy", true)
                // Makes Odoo add the project's empty stages (group_expand).
                put("context", buildJsonObject { put("default_project_id", projectId) })
            },
        ) as JsonArray
        return groups.map { group ->
            val g = group.jsonObject
            val stage = g["stage_id"]?.refOrNull()
            Stage(
                id = stage?.id,
                name = stage?.name.orEmpty(),
                folded = g["__fold"]?.booleanOrFalse() ?: false,
                count = g["stage_id_count"]?.intOrNull() ?: 0,
            )
        }
    }

    /** The project's stages, for picking one; no counts. */
    suspend fun projectStages(projectId: Int): List<Ref> {
        val rows = client.callKw(
            "project.task.type",
            "search_read",
            kwargs = buildJsonObject {
                put("domain", JsonArray(listOf(term("project_ids", "in", ints(listOf(projectId))))))
                put("fields", strings(listOf("name")))
            },
        ) as JsonArray
        return rows.map { Ref(it.jsonObject.getValue("id").intOrNull()!!, it.jsonObject["name"]?.stringOrNull().orEmpty()) }
    }

    suspend fun tasks(projectId: Int, stageId: Int?, filter: TaskFilter, offset: Int, limit: Int): List<TaskCard> {
        val rows = client.callKw(
            "project.task",
            "search_read",
            kwargs = buildJsonObject {
                put("domain", domain(projectId, filter, term("stage_id", "=", stageId)))
                put("fields", strings(CARD_FIELDS))
                put("offset", offset)
                put("limit", limit)
            },
        ) as JsonArray
        val objects = rows.map { it.jsonObject }
        resolveNames(objects)
        return objects.map(::parseCard)
    }

    suspend fun task(id: Int): Task {
        val rows = client.callKw(
            "project.task",
            "read",
            args = JsonArray(listOf(ints(listOf(id)), strings(DETAIL_FIELDS))),
        ) as JsonArray
        val row = rows.single().jsonObject
        resolveNames(listOf(row))
        return parseTask(row)
    }

    suspend fun create(projectId: Int, values: TaskValues): Int {
        val id = client.callKw(
            "project.task",
            "create",
            args = JsonArray(listOf(JsonObject(toVals(values) + ("project_id" to JsonPrimitive(projectId))))),
            kwargs = buildJsonObject { put("context", buildJsonObject { put("default_project_id", projectId) }) },
        ).intOrNull()!!
        notifyChanged(id)
        return id
    }

    suspend fun update(id: Int, values: TaskValues) {
        val vals = toVals(values)
        if (vals.isEmpty()) return
        client.callKw("project.task", "write", args = JsonArray(listOf(ints(listOf(id)), JsonObject(vals))))
        notifyChanged(id)
    }

    /** Internal users the task can be assigned to. */
    suspend fun searchUsers(query: String): List<Ref> =
        nameSearch("res.users", query, JsonArray(listOf(term("share", "=", false))))

    suspend fun searchTags(query: String): List<Ref> = nameSearch("project.tags", query)

    suspend fun searchOutlets(query: String): List<Ref> = nameSearch("mentis.outlet", query)

    private suspend fun nameSearch(model: String, query: String, domain: JsonArray = JsonArray(emptyList())): List<Ref> {
        val rows = client.callKw(
            model,
            "name_search",
            kwargs = buildJsonObject {
                put("name", query)
                put("args", domain)
                put("limit", 40)
            },
        ) as JsonArray
        return rows.mapNotNull { it.refOrNull() }
    }

    // Domain terms are JsonArrays, i.e. lists themselves: always add() them, never `+` or `+=`,
    // which would splice their elements into the domain.
    private fun domain(projectId: Int, filter: TaskFilter, vararg extra: JsonArray): JsonArray {
        val terms = mutableListOf<JsonElement>(term("project_id", "=", projectId))
        extra.forEach { terms.add(it) }
        if (filter.mine) client.userContext["uid"]?.intOrNull()?.let { terms.add(term("user_ids", "in", ints(listOf(it)))) }
        if (filter.overdue) {
            terms.add(term("date_deadline", "<", LocalDate.now().toString()))
            terms.add(term("is_closed", "=", false))
        }
        val query = filter.query.trim()
        if (query.isNotEmpty()) {
            terms.add(JsonPrimitive("|"))
            terms.add(term("name", "ilike", query))
            terms.add(term("mentis_outlet_id", "ilike", query))
        }
        return JsonArray(terms)
    }

    /** Fetches the names of assignees and tags not seen yet; read returns only their ids. */
    private suspend fun resolveNames(rows: List<JsonObject>) {
        val userIds = rows.flatMap { it["user_ids"]?.intList().orEmpty() }.toSet() - userNames.keys
        if (userIds.isNotEmpty()) {
            read("res.users", userIds, listOf("name")).forEach {
                userNames[it.getValue("id").intOrNull()!!] = it["name"]?.stringOrNull().orEmpty()
            }
        }
        val tagIds = rows.flatMap { it["tag_ids"]?.intList().orEmpty() }.toSet() - tags.keys
        if (tagIds.isNotEmpty()) {
            read("project.tags", tagIds, listOf("name", "color")).forEach {
                val id = it.getValue("id").intOrNull()!!
                tags[id] = Tag(id, it["name"]?.stringOrNull().orEmpty(), it["color"]?.intOrNull() ?: 0)
            }
        }
    }

    private suspend fun read(model: String, ids: Collection<Int>, fields: List<String>): List<JsonObject> =
        (client.callKw(model, "read", args = JsonArray(listOf(ints(ids), strings(fields)))) as JsonArray)
            .map { it.jsonObject }

    private fun assignees(row: JsonObject) =
        row["user_ids"]?.intList().orEmpty().map { Ref(it, userNames[it].orEmpty()) }

    private fun tags(row: JsonObject) = row["tag_ids"]?.intList().orEmpty().mapNotNull { tags[it] }

    private fun parseCard(row: JsonObject) = TaskCard(
        id = row.getValue("id").intOrNull()!!,
        name = row["name"]?.stringOrNull().orEmpty(),
        stageId = row["stage_id"]?.refOrNull()?.id,
        highPriority = row["priority"]?.stringOrNull() == "1",
        kanbanState = KanbanState.of(row["kanban_state"]?.stringOrNull()),
        deadline = row["date_deadline"]?.stringOrNull()?.let(LocalDate::parse),
        isClosed = row["is_closed"]?.booleanOrFalse() ?: false,
        assignees = assignees(row),
        tags = tags(row),
        outlet = row["mentis_outlet_id"]?.refOrNull(),
        subtaskCount = row["subtask_count"]?.intOrNull() ?: 0,
        color = row["color"]?.intOrNull() ?: 0,
    )

    private fun parseTask(row: JsonObject) = Task(
        id = row.getValue("id").intOrNull()!!,
        name = row["name"]?.stringOrNull().orEmpty(),
        project = row["project_id"]?.refOrNull(),
        stage = row["stage_id"]?.refOrNull(),
        highPriority = row["priority"]?.stringOrNull() == "1",
        kanbanState = KanbanState.of(row["kanban_state"]?.stringOrNull()),
        kanbanLegends = mapOf(
            KanbanState.Normal to row["legend_normal"]?.stringOrNull().orEmpty(),
            KanbanState.Done to row["legend_done"]?.stringOrNull().orEmpty(),
            KanbanState.Blocked to row["legend_blocked"]?.stringOrNull().orEmpty(),
        ),
        deadline = row["date_deadline"]?.stringOrNull()?.let(LocalDate::parse),
        isClosed = row["is_closed"]?.booleanOrFalse() ?: false,
        assignees = assignees(row),
        tags = tags(row),
        outlet = row["mentis_outlet_id"]?.refOrNull(),
        network = row["mentis_network_id"]?.refOrNull(),
        customer = row["partner_id"]?.refOrNull(),
        parent = row["parent_id"]?.refOrNull(),
        subtaskCount = row["subtask_count"]?.intOrNull() ?: 0,
        description = row["description"]?.stringOrNull().orEmpty(),
        createdBy = row["create_uid"]?.refOrNull(),
    )

    private fun toVals(v: TaskValues): Map<String, JsonElement> = buildMap {
        v.name?.let { put("name", JsonPrimitive(it.trim())) }
        v.stageId?.let { put("stage_id", JsonPrimitive(it)) }
        v.assigneeIds?.let { put("user_ids", setIds(it)) }
        v.deadline?.let { put("date_deadline", it.value?.toString()?.let(::JsonPrimitive) ?: JsonPrimitive(false)) }
        v.tagIds?.let { put("tag_ids", setIds(it)) }
        v.highPriority?.let { put("priority", JsonPrimitive(if (it) "1" else "0")) }
        v.kanbanState?.let { put("kanban_state", JsonPrimitive(it.odoo)) }
        v.outletId?.let { put("mentis_outlet_id", it.value?.let(::JsonPrimitive) ?: JsonPrimitive(false)) }
        v.description?.let { put("description", JsonPrimitive(Html.fromPlainText(it))) }
    }

    private companion object {
        val CARD_FIELDS = listOf(
            "name", "stage_id", "priority", "kanban_state", "date_deadline", "is_closed", "user_ids", "tag_ids",
            "mentis_outlet_id", "subtask_count", "color",
        )
        val DETAIL_FIELDS = CARD_FIELDS + listOf(
            "project_id", "legend_normal", "legend_done", "legend_blocked", "mentis_network_id", "partner_id",
            "parent_id", "description", "create_uid",
        )
    }
}
