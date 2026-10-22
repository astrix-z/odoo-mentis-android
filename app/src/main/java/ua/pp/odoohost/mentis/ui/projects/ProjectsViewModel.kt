package ua.pp.odoohost.mentis.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.odoo.OdooException
import ua.pp.odoohost.mentis.data.project.Project
import ua.pp.odoohost.mentis.data.project.ProjectRepository

data class ProjectsState(
    val projects: List<Project> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
) {
    val visible: List<Project>
        get() = if (query.isBlank()) projects else projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
}

class ProjectsViewModel(
    private val repository: ProjectRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsState())
    val state: StateFlow<ProjectsState> = _state.asStateFlow()

    init {
        load()
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value) }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        load()
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val projects = repository.projects()
                _state.update { it.copy(projects = projects, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OdooException.SessionExpired) {
                auth.logout()
            } catch (e: Exception) {
                _state.update { it.copy(error = e) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }
}
