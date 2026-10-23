package ua.pp.odoohost.mentis.ui.task

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ua.pp.odoohost.mentis.R
import ua.pp.odoohost.mentis.data.task.Attachment
import ua.pp.odoohost.mentis.data.task.ChatMessage
import ua.pp.odoohost.mentis.data.task.KanbanState
import ua.pp.odoohost.mentis.data.task.Task
import ua.pp.odoohost.mentis.ui.common.Avatar
import ua.pp.odoohost.mentis.ui.common.errorText
import ua.pp.odoohost.mentis.ui.common.formatted
import ua.pp.odoohost.mentis.ui.common.serverUrl
import ua.pp.odoohost.mentis.ui.tasks.KanbanStateDot
import ua.pp.odoohost.mentis.ui.tasks.PriorityStar
import ua.pp.odoohost.mentis.ui.tasks.TagRow
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(viewModel: TaskDetailViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val noViewerApp = stringResource(R.string.error_no_viewer)

    LaunchedEffect(viewModel) {
        viewModel.openFile.collect { (uri, mimetype) ->
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimetype).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                snackbar.showSnackbar(noViewerApp)
            }
        }
    }
    state.actionError?.let { error ->
        val message = errorText(error)
        LaunchedEffect(error) {
            snackbar.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.task != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val task = state.task
        Box(Modifier.padding(padding).fillMaxSize().imePadding()) {
            when {
                task == null && state.loadError != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(errorText(state.loadError!!), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                task == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 840.dp) {
                        // Tablet: the task on the left, its chatter on the right.
                        Row(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) { details(task, state, viewModel) }
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) { chatter(state, viewModel) }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                            details(task, state, viewModel)
                            item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
                            chatter(state, viewModel)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.details(task: Task, state: TaskDetailState, viewModel: TaskDetailViewModel) {
    item("header") {
        Row(verticalAlignment = Alignment.Top) {
            Text(task.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::togglePriority) {
                Icon(
                    if (task.highPriority) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.task_priority_high),
                    tint = if (task.highPriority) PriorityStar else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item("stages") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.stages.forEach { stage ->
                FilterChip(
                    selected = stage.id == task.stage?.id,
                    onClick = { if (stage.id != task.stage?.id) viewModel.setStage(stage.id) },
                    label = { Text(stage.name) },
                )
            }
        }
    }
    item("kanban_state") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KanbanState.entries.forEach { s ->
                FilterChip(
                    selected = task.kanbanState == s,
                    onClick = { if (task.kanbanState != s) viewModel.setKanbanState(s) },
                    leadingIcon = { KanbanStateDot(s) },
                    label = { Text(task.kanbanLegends[s].orEmpty().ifEmpty { stringResource(kanbanStateLabel(s)) }) },
                )
            }
        }
    }
    item("fields") {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            task.project?.let { Field(R.string.field_project) { Text(it.name) } }
            task.outlet?.let { Field(R.string.field_outlet) { Text(it.name) } }
            task.network?.let { Field(R.string.field_network) { Text(it.name) } }
            Field(R.string.field_assignees) {
                if (task.assignees.isEmpty()) {
                    Text(stringResource(R.string.value_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                task.assignees.forEach { user ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(user.id, user.name, size = 24.dp)
                        Text(user.name)
                    }
                }
            }
            task.deadline?.let { deadline ->
                Field(R.string.field_deadline) {
                    val overdue = !task.isClosed && deadline < LocalDate.now()
                    Text(deadline.formatted(), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (task.tags.isNotEmpty()) Field(R.string.field_tags) { TagRow(task.tags.map { it.name to it.color }) }
            task.customer?.let { Field(R.string.field_customer) { Text(it.name) } }
            task.parent?.let { Field(R.string.field_parent) { Text(it.name) } }
            if (task.subtaskCount > 0) Field(R.string.field_subtasks) { Text(task.subtaskCount.toString()) }
            task.createdBy?.let { Field(R.string.field_created_by) { Text(it.name) } }
        }
    }
    if (task.description.isNotBlank()) {
        item("description") {
            Column(Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.field_description), style = MaterialTheme.typography.labelLarge)
                Text(AnnotatedString.fromHtml(task.description), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun LazyListScope.chatter(state: TaskDetailState, viewModel: TaskDetailViewModel) {
    item("composer") { ComposerView(state.composer, viewModel) }
    items(state.messages, key = { "m${it.id}" }) { MessageView(it, state.openingAttachmentId, viewModel::openAttachment) }
    item("older") {
        when {
            state.messagesLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
            !state.messagesEndReached -> TextButton(onClick = viewModel::loadOlderMessages, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chatter_older))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerView(composer: Composer, viewModel: TaskDetailViewModel) {
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        photoUri?.takeIf { taken }?.let { viewModel.addFiles(listOf(it)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { viewModel.addFiles(it) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !composer.isNote,
                onClick = { viewModel.onComposerNote(false) },
                label = { Text(stringResource(R.string.chatter_message)) },
            )
            FilterChip(
                selected = composer.isNote,
                onClick = { viewModel.onComposerNote(true) },
                label = { Text(stringResource(R.string.chatter_note)) },
            )
        }
        OutlinedTextField(
            value = composer.text,
            onValueChange = viewModel::onComposerText,
            placeholder = {
                Text(stringResource(if (composer.isNote) R.string.chatter_note_hint else R.string.chatter_message_hint))
            },
            minLines = 2,
            enabled = !composer.sending,
            modifier = Modifier.fillMaxWidth(),
        )
        if (composer.files.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                composer.files.forEach { file ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeFile(file.key) },
                        label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp)) },
                        leadingIcon = {
                            when {
                                file.error != null -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                file.attachment == null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else -> Icon(Icons.Filled.AttachFile, null)
                            }
                        },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove)) },
                    )
                }
            }
            composer.files.firstNotNullOfOrNull { it.error }?.let {
                Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.newPhotoUri().also { photoUri = it }.let(camera::launch) }, enabled = !composer.sending) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.chatter_take_photo))
            }
            IconButton(onClick = { picker.launch("*/*") }, enabled = !composer.sending) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.chatter_attach))
            }
            Box(Modifier.weight(1f))
            if (composer.sending) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = viewModel::send, enabled = composer.canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chatter_send))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageView(message: ChatMessage, openingId: Int?, onOpen: (Attachment) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val author = message.author
        if (author != null) Avatar(author.id, author.name, size = 36.dp, model = "res.partner")
        else Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(author?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(message.date.formatted(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message.isNote && message.body.isNotBlank()) {
                Text(
                    stringResource(R.string.chatter_note_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (message.body.isNotBlank()) {
                Text(AnnotatedString.fromHtml(message.body), style = MaterialTheme.typography.bodyMedium)
            } else {
                message.subtypeDescription?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message.trackings.forEach { t ->
                Text(
                    "${t.field}: ${t.oldValue.ifEmpty { "—" }} → ${t.newValue.ifEmpty { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (message.attachments.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.attachments.forEach { attachment ->
                        if (attachment.isImage) {
                            AsyncImage(
                                model = serverUrl("/web/image/${attachment.id}/300x300"),
                                contentDescription = attachment.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.small)
                                    .clickable { onOpen(attachment) },
                            )
                        } else {
                            AssistChip(
                                onClick = { onOpen(attachment) },
                                label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    if (openingId == attachment.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Description, null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: Int, value: @Composable () -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { value() }
    }
}

private fun kanbanStateLabel(state: KanbanState) = when (state) {
    KanbanState.Normal -> R.string.kanban_normal
    KanbanState.Done -> R.string.kanban_done
    KanbanState.Blocked -> R.string.kanban_blocked
}
