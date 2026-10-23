package ua.pp.odoohost.mentis.ui.tasks

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.task.Stage
import ua.pp.odoohost.mentis.data.task.TaskCard
import ua.pp.odoohost.mentis.data.task.TaskFilter
import ua.pp.odoohost.mentis.data.task.TaskRepository
import ua.pp.odoohost.mentis.data.task.TaskValues
import ua.pp.odoohost.mentis.ui.common.OdooViewModel

data class ColumnState(
    val tasks: List<TaskCard> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: Throwable? = null,
)

data class TasksState(
    val stages: List<Stage> = emptyList(),
    /** Loaded tasks per stage id (null = no stage); a column loads when it first shows up. */
    val columns: Map<Int?, ColumnState> = emptyMap(),
    val filter: TaskFilter = TaskFilter(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
    /** Bumped on every reload so visible columns know to fetch again. */
    val generation: Int = 0,
)

class TasksViewModel(
    private val projectId: Int,
    private val repository: TaskRepository,
    auth: AuthRepository,
) : OdooViewModel(auth) {

    private val _state = MutableStateFlow(TasksState())
    val state: StateFlow<TasksState> = _state.asStateFlow()

    private var reloadJob: Job? = null

    init {
        reload()
        viewModelScope.launch { repository.changes.collect { reload(delayMillis = 300) } }
    }

    fun onMine(value: Boolean) = setFilter(_state.value.filter.copy(mine = value))
    fun onOverdue(value: Boolean) = setFilter(_state.value.filter.copy(overdue = value))
    fun onQuery(value: String) = setFilter(_state.value.filter.copy(query = value), delayMillis = 400)

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        reload()
    }

    /** Loads the first page of a column that has just become visible. */
    fun ensureLoaded(stageId: Int?) {
        val column = _state.value.columns[stageId]
        if (column == null || (column.tasks.isEmpty() && !column.endReached && !column.loading && column.error == null)) {
            loadMore(stageId)
        }
    }

    fun loadMore(stageId: Int?) {
        val s = _state.value
        val column = s.columns[stageId] ?: ColumnState()
        if (column.loading || column.endReached) return
        val generation = s.generation
        updateColumn(stageId) { it.copy(loading = true, error = null) }
        launchSafe(onError = { e -> updateColumn(stageId) { it.copy(loading = false, error = e) } }) {
            val page = repository.tasks(projectId, stageId, s.filter, offset = column.tasks.size, limit = PAGE_SIZE)
            if (_state.value.generation != generation) return@launchSafe
            updateColumn(stageId) {
                it.copy(tasks = it.tasks + page, loading = false, endReached = page.size < PAGE_SIZE)
            }
        }
    }

    fun moveTask(task: TaskCard, stageId: Int) {
        launchSafe(onError = { e -> _state.update { it.copy(error = e) } }) {
            repository.update(task.id, TaskValues(stageId = stageId))
        }
    }

    private fun setFilter(filter: TaskFilter, delayMillis: Long = 0) {
        _state.update { it.copy(filter = filter) }
        reload(delayMillis)
    }

    private fun reload(delayMillis: Long = 0) {
        reloadJob?.cancel()
        reloadJob = launchSafe(
            onError = { e -> _state.update { it.copy(error = e) } },
            onFinally = { _state.update { it.copy(loading = false, refreshing = false) } },
        ) {
            delay(delayMillis)
            val stages = repository.stages(projectId, _state.value.filter)
            _state.update {
                it.copy(stages = stages, columns = emptyMap(), error = null, generation = it.generation + 1)
            }
        }
    }

    private fun updateColumn(stageId: Int?, change: (ColumnState) -> ColumnState) =
        _state.update { it.copy(columns = it.columns + (stageId to change(it.columns[stageId] ?: ColumnState()))) }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
