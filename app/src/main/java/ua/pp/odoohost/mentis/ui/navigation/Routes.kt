package ua.pp.odoohost.mentis.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ProjectsRoute

@Serializable
data class TasksRoute(val projectId: Int, val projectName: String)

@Serializable
data class TaskRoute(val taskId: Int)

/** Edits [taskId], or creates a task in [projectId] (in [stageId]) when it is null. */
@Serializable
data class TaskEditRoute(val projectId: Int, val taskId: Int? = null, val stageId: Int? = null)
