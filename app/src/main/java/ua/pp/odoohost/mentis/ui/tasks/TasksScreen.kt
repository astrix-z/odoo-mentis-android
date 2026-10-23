package ua.pp.odoohost.mentis.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.pp.odoohost.mentis.R
import ua.pp.odoohost.mentis.data.task.KanbanState
import ua.pp.odoohost.mentis.data.task.Stage
import ua.pp.odoohost.mentis.data.task.TaskCard
import ua.pp.odoohost.mentis.ui.common.Avatar
import ua.pp.odoohost.mentis.ui.common.errorText
import ua.pp.odoohost.mentis.ui.common.formatted
import ua.pp.odoohost.mentis.ui.theme.odooColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    projectName: String,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (stageId: Int?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The stage a new task goes to: the open tab on phones, the first open stage on tablets.
    var currentStageId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onCreateTask(currentStageId ?: state.stages.firstOrNull { !it.folded }?.id) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.task_new))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = viewModel::onQuery,
                placeholder = { Text(stringResource(R.string.tasks_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter.mine,
                    onClick = { viewModel.onMine(!state.filter.mine) },
                    label = { Text(stringResource(R.string.filter_mine)) },
                )
                FilterChip(
                    selected = state.filter.overdue,
                    onClick = { viewModel.onOverdue(!state.filter.overdue) },
                    label = { Text(stringResource(R.string.filter_overdue)) },
                )
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> Centered { CircularProgressIndicator() }
                    state.error != null && state.stages.isEmpty() -> Centered {
                        Text(errorText(state.error!!), color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                    }
                    state.stages.isEmpty() -> Centered { Text(stringResource(R.string.tasks_empty)) }
                    else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        if (maxWidth < 600.dp) {
                            PhoneKanban(state, viewModel, onOpenTask, onStageShown = { currentStageId = it })
                        } else {
                            TabletKanban(state, viewModel, onOpenTask)
                        }
                    }
                }
            }
        }
    }
}

/** One stage at a time: tabs on top, swipe between stages. */
@Composable
private fun PhoneKanban(
    state: TasksState,
    viewModel: TasksViewModel,
    onOpenTask: (Int) -> Unit,
    onStageShown: (Int?) -> Unit,
) {
    val firstOpen = state.stages.indexOfFirst { !it.folded }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = firstOpen) { state.stages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, state.stages) {
        snapshotFlow { pager.currentPage }.collect { onStageShown(state.stages.getOrNull(it)?.id) }
    }
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage.coerceAtMost(state.stages.lastIndex), edgePadding = 16.dp) {
            state.stages.forEachIndexed { index, stage ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text("${stageName(stage)} (${stage.count})") },
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { state.stages[it].id ?: -1 }) { page ->
            val stage = state.stages[page]
            StageColumn(stage, state, viewModel, onOpenTask, Modifier.fillMaxSize())
        }
    }
}

/** All stages side by side, as in the Odoo web kanban. */
@Composable
private fun TabletKanban(state: TasksState, viewModel: TasksViewModel, onOpenTask: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.stages, key = { it.id ?: -1 }) { stage ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.width(320.dp).fillMaxHeight().padding(vertical = 8.dp),
            ) {
                Column {
                    Text(
                        "${stageName(stage)} (${stage.count})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(12.dp),
                    )
                    StageColumn(stage, state, viewModel, onOpenTask, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun StageColumn(
    stage: Stage,
    state: TasksState,
    viewModel: TasksViewModel,
    onOpenTask: (Int) -> Unit,
    modifier: Modifier,
) {
    val column = state.columns[stage.id] ?: ColumnState()
    LaunchedEffect(stage.id, state.generation) { viewModel.ensureLoaded(stage.id) }

    val listState = rememberLazyListState()
    // Next page when the user nears the end of what is loaded.
    LaunchedEffect(listState, column.tasks.size, column.endReached) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (!column.endReached && last >= column.tasks.size - 5) viewModel.loadMore(stage.id) }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(column.tasks, key = { it.id }) { task ->
            TaskCardView(task, state.stages, onClick = { onOpenTask(task.id) }, onMove = { viewModel.moveTask(task, it) })
        }
        when {
            column.loading -> item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
            column.error != null -> item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorText(column.error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.loadMore(stage.id) }) { Text(stringResource(R.string.retry)) }
                }
            }
            column.endReached && column.tasks.isEmpty() -> item {
                Text(
                    stringResource(R.string.tasks_stage_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        // Room for the floating button over the last card.
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCardView(task: TaskCard, stages: List<Stage>, onClick: () -> Unit, onMove: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(odooColor(task.color) ?: Color.Transparent))
                Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            task.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (task.highPriority) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.task_priority_high),
                                tint = PriorityStar,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    task.outlet?.let {
                        Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.tags.isNotEmpty()) TagRow(task.tags.map { it.name to it.color })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        task.deadline?.let { deadline ->
                            val color = if (task.isOverdue()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(Icons.Filled.Event, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            Text(deadline.formatted(), style = MaterialTheme.typography.labelMedium, color = color)
                        }
                        if (task.subtaskCount > 0) {
                            Text(
                                stringResource(R.string.task_subtasks_short, task.subtaskCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        KanbanStateDot(task.kanbanState)
                        AssigneeStack(task)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                stringResource(R.string.task_move_to),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            stages.filter { it.id != null && it.id != task.stageId }.forEach { stage ->
                DropdownMenuItem(text = { Text(stage.name) }, onClick = { menu = false; onMove(stage.id!!) })
            }
        }
    }
}

@Composable
private fun AssigneeStack(task: TaskCard) {
    Row {
        task.assignees.take(3).forEachIndexed { index, user ->
            Avatar(user.id, user.name, size = 24.dp, modifier = Modifier.offset(x = (-8 * index).dp))
        }
    }
}

@Composable
fun KanbanStateDot(state: KanbanState, modifier: Modifier = Modifier) {
    val color = when (state) {
        KanbanState.Normal -> return
        KanbanState.Done -> Color(0xFF28A745)
        KanbanState.Blocked -> Color(0xFFDC3545)
    }
    Box(modifier.size(12.dp).background(color, CircleShape))
}

/** Small coloured tag labels; [tags] are name and Odoo colour index. */
@Composable
fun TagRow(tags: List<Pair<String, Int>>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { (name, color) ->
            val tint = odooColor(color) ?: MaterialTheme.colorScheme.outline
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
                modifier = Modifier
                    .background(tint.copy(alpha = 0.12f), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun stageName(stage: Stage) = stage.id?.let { stage.name } ?: stringResource(R.string.stage_none)

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

val PriorityStar = Color(0xFFF3CC00)
