package ua.pp.odoohost.mentis.data.task

import ua.pp.odoohost.mentis.data.odoo.Ref
import java.time.LocalDate

/** Odoo's per-stage traffic light on a task. */
enum class KanbanState(val odoo: String) {
    Normal("normal"), Done("done"), Blocked("blocked");

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.odoo == value } ?: Normal
    }
}

data class Tag(val id: Int, val name: String, val color: Int)

/** What a kanban card shows. */
data class TaskCard(
    val id: Int,
    val name: String,
    val stageId: Int?,
    val highPriority: Boolean,
    val kanbanState: KanbanState,
    val deadline: LocalDate?,
    val isClosed: Boolean,
    val assignees: List<Ref>,
    val tags: List<Tag>,
    val outlet: Ref?,
    val subtaskCount: Int,
    val color: Int,
) {
    fun isOverdue(today: LocalDate = LocalDate.now()) = !isClosed && deadline != null && deadline < today
}

data class Task(
    val id: Int,
    val name: String,
    val project: Ref?,
    val stage: Ref?,
    val highPriority: Boolean,
    val kanbanState: KanbanState,
    /** Stage-specific labels of the three kanban states, already translated by Odoo. */
    val kanbanLegends: Map<KanbanState, String>,
    val deadline: LocalDate?,
    val isClosed: Boolean,
    val assignees: List<Ref>,
    val tags: List<Tag>,
    val outlet: Ref?,
    val network: Ref?,
    val customer: Ref?,
    val parent: Ref?,
    val subtaskCount: Int,
    /** HTML, as stored by Odoo. */
    val description: String,
    val createdBy: Ref?,
)

/** A kanban column: a stage of the project, or the tasks that have none (`id == null`). */
data class Stage(val id: Int?, val name: String, val folded: Boolean, val count: Int)

data class TaskFilter(val mine: Boolean = false, val overdue: Boolean = false, val query: String = "")

/** The editable fields of a task; null in an update means "leave unchanged". */
data class TaskValues(
    val name: String? = null,
    val stageId: Int? = null,
    val assigneeIds: List<Int>? = null,
    val deadline: Change<LocalDate>? = null,
    val tagIds: List<Int>? = null,
    val highPriority: Boolean? = null,
    val kanbanState: KanbanState? = null,
    val outletId: Change<Int>? = null,
    /** Plain text; stored as simple HTML paragraphs. */
    val description: String? = null,
)

/** A value that may be deliberately cleared, unlike a plain nullable "unchanged". */
data class Change<out T>(val value: T?)
