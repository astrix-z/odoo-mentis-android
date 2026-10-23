package ua.pp.odoohost.mentis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ua.pp.odoohost.mentis.AppContainer
import ua.pp.odoohost.mentis.data.auth.UserSession
import ua.pp.odoohost.mentis.ui.projects.ProjectsScreen
import ua.pp.odoohost.mentis.ui.projects.ProjectsViewModel
import ua.pp.odoohost.mentis.ui.task.TaskDetailScreen
import ua.pp.odoohost.mentis.ui.task.TaskDetailViewModel
import ua.pp.odoohost.mentis.ui.task.TaskEditScreen
import ua.pp.odoohost.mentis.ui.task.TaskEditViewModel
import ua.pp.odoohost.mentis.ui.tasks.TasksScreen
import ua.pp.odoohost.mentis.ui.tasks.TasksViewModel

/** The signed-in part of the app. ViewModels live as long as their back-stack entry. */
@Composable
fun AppNavHost(container: AppContainer, session: UserSession) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = ProjectsRoute) {
        composable<ProjectsRoute> {
            ProjectsScreen(
                viewModel(factory = viewModelFactory { initializer { ProjectsViewModel(container.projects, container.auth) } }),
                userName = session.name,
                onOpenProject = { nav.navigate(TasksRoute(it.id, it.name)) },
            )
        }
        composable<TasksRoute> { entry ->
            val route = entry.toRoute<TasksRoute>()
            TasksScreen(
                viewModel(factory = viewModelFactory { initializer { TasksViewModel(route.projectId, container.tasks, container.auth) } }),
                projectName = route.projectName,
                onBack = { nav.popBackStack() },
                onOpenTask = { nav.navigate(TaskRoute(it)) },
                onCreateTask = { stageId -> nav.navigate(TaskEditRoute(route.projectId, stageId = stageId)) },
            )
        }
        composable<TaskRoute> { entry ->
            val route = entry.toRoute<TaskRoute>()
            val viewModel = viewModel<TaskDetailViewModel>(
                factory = viewModelFactory {
                    initializer { TaskDetailViewModel(route.taskId, container.tasks, container.chatter, container.files, container.auth) }
                },
            )
            TaskDetailScreen(
                viewModel,
                onBack = { nav.popBackStack() },
                onEdit = {
                    viewModel.state.value.task?.project?.let { nav.navigate(TaskEditRoute(it.id, taskId = route.taskId)) }
                },
            )
        }
        composable<TaskEditRoute> { entry ->
            val route = entry.toRoute<TaskEditRoute>()
            TaskEditScreen(
                viewModel(
                    factory = viewModelFactory {
                        initializer { TaskEditViewModel(route.taskId, route.projectId, route.stageId, container.tasks, container.auth) }
                    },
                ),
                isNew = route.taskId == null,
                searchUsers = container.tasks::searchUsers,
                searchTags = container.tasks::searchTags,
                searchOutlets = container.tasks::searchOutlets,
                onClose = { nav.popBackStack() },
                onSaved = { id ->
                    if (route.taskId == null) {
                        // A new task opens in place of the form.
                        nav.navigate(TaskRoute(id)) { popUpTo<TaskEditRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
    }
}
