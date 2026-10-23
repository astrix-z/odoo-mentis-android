package ua.pp.odoohost.mentis.ui.task

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.odoo.Html
import ua.pp.odoohost.mentis.data.odoo.Ref
import ua.pp.odoohost.mentis.data.task.Change
import ua.pp.odoohost.mentis.data.task.Task
import ua.pp.odoohost.mentis.data.task.TaskRepository
import ua.pp.odoohost.mentis.data.task.TaskValues
import ua.pp.odoohost.mentis.ui.common.OdooViewModel
import java.time.LocalDate

data class TaskForm(
    val name: String = "",
    val stage: Ref? = null,
    val assignees: List<Ref> = emptyList(),
    val deadline: LocalDate? = null,
    val tags: List<Ref> = emptyList(),
    val highPriority: Boolean = false,
    val outlet: Ref? = null,
    val description: String = "",
)

data class TaskEditState(
    val form: TaskForm = TaskForm(),
    val stages: List<Ref> = emptyList(),
    /** The existing description has lists, tables or images that plain-text editing would drop. */
    val richDescription: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Throwable? = null,
) {
    val canSave get() = !loading && !saving && form.name.isNotBlank()
}

/** Creates a task in [projectId] when [taskId] is null, otherwise edits that task. */
class TaskEditViewModel(
    private val taskId: Int?,
    private val projectId: Int,
    private val defaultStageId: Int?,
    private val tasks: TaskRepository,
    auth: AuthRepository,
) : OdooViewModel(auth) {

    private val _state = MutableStateFlow(TaskEditState())
    val state: StateFlow<TaskEditState> = _state.asStateFlow()

    private val _saved = Channel<Int>(Channel.BUFFERED)

    /** Id of the created or updated task, once saved. */
    val saved: Flow<Int> = _saved.receiveAsFlow()

    private var original: TaskForm? = null

    init {
        load()
    }

    fun retry() = load()

    fun edit(change: (TaskForm) -> TaskForm) = _state.update { it.copy(form = change(it.form), error = null) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        launchSafe(
            onError = { e -> _state.update { it.copy(error = e) } },
            onFinally = { _state.update { it.copy(saving = false) } },
        ) {
            val id = taskId?.also { tasks.update(it, changes(original!!, s.form)) }
                ?: tasks.create(projectId, changes(TaskForm(), s.form))
            _saved.send(id)
        }
    }

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }
        launchSafe(
            onError = { e -> _state.update { it.copy(error = e) } },
            onFinally = { _state.update { it.copy(loading = false) } },
        ) {
            val stages = tasks.projectStages(projectId)
            val task = taskId?.let { tasks.task(it) }
            val form = task?.let(::formOf) ?: TaskForm(stage = stages.firstOrNull { it.id == defaultStageId })
            original = form
            _state.update {
                it.copy(
                    form = form,
                    stages = stages,
                    richDescription = task?.description?.let(Html::hasRichFormatting) ?: false,
                )
            }
        }
    }

    private fun formOf(task: Task) = TaskForm(
        name = task.name,
        stage = task.stage,
        assignees = task.assignees,
        deadline = task.deadline,
        tags = task.tags.map { Ref(it.id, it.name) },
        highPriority = task.highPriority,
        outlet = task.outlet,
        description = HtmlCompat.fromHtml(task.description, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim(),
    )

    /** Only what the user changed, so an edit never rewrites fields (or rich text) they did not touch. */
    private fun changes(from: TaskForm, to: TaskForm) = TaskValues(
        name = to.name.takeIf { it != from.name },
        stageId = to.stage?.id.takeIf { to.stage?.id != from.stage?.id },
        assigneeIds = to.assignees.map { it.id }.takeIf { it != from.assignees.map { a -> a.id } },
        deadline = Change(to.deadline).takeIf { to.deadline != from.deadline },
        tagIds = to.tags.map { it.id }.takeIf { it != from.tags.map { t -> t.id } },
        highPriority = to.highPriority.takeIf { it != from.highPriority },
        outletId = Change(to.outlet?.id).takeIf { to.outlet?.id != from.outlet?.id },
        description = to.description.takeIf { it != from.description },
    )
}
