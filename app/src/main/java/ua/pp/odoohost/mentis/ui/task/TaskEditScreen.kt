package ua.pp.odoohost.mentis.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.pp.odoohost.mentis.R
import ua.pp.odoohost.mentis.data.odoo.Ref
import ua.pp.odoohost.mentis.ui.common.SearchPickerDialog
import ua.pp.odoohost.mentis.ui.common.errorText
import ua.pp.odoohost.mentis.ui.common.formatted
import ua.pp.odoohost.mentis.ui.tasks.PriorityStar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class Picker { Assignees, Tags, Outlet, Deadline }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    isNew: Boolean,
    searchUsers: suspend (String) -> List<Ref>,
    searchTags: suspend (String) -> List<Ref>,
    searchOutlets: suspend (String) -> List<Ref>,
    onClose: () -> Unit,
    onSaved: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    var picker by remember { mutableStateOf<Picker?>(null) }

    LaunchedEffect(viewModel) { viewModel.saved.collect(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.task_new else R.string.task_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = viewModel::save, enabled = state.canSave) { Text(stringResource(R.string.save)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { error ->
                    Text(errorText(error), color = MaterialTheme.colorScheme.error)
                    if (state.stages.isEmpty() && !isNew) Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { v -> viewModel.edit { it.copy(name = v) } },
                        label = { Text(stringResource(R.string.field_name)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.edit { it.copy(highPriority = !it.highPriority) } }) {
                        Icon(
                            if (form.highPriority) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(R.string.task_priority_high),
                            tint = if (form.highPriority) PriorityStar else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StageField(form.stage, state.stages) { stage -> viewModel.edit { it.copy(stage = stage) } }
                PickerField(R.string.field_outlet, form.outlet?.name.orEmpty()) { picker = Picker.Outlet }
                PickerField(R.string.field_assignees, form.assignees.joinToString { it.name }) { picker = Picker.Assignees }
                PickerField(R.string.field_deadline, form.deadline?.formatted().orEmpty(), icon = Icons.Filled.Event) {
                    picker = Picker.Deadline
                }
                PickerField(R.string.field_tags, form.tags.joinToString { it.name }) { picker = Picker.Tags }
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> viewModel.edit { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.field_description)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.richDescription) {
                    Text(
                        stringResource(R.string.description_rich_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (picker) {
        Picker.Assignees -> SearchPickerDialog(
            title = stringResource(R.string.field_assignees),
            selected = form.assignees,
            multiple = true,
            search = searchUsers,
            onDismiss = { picker = null },
            onConfirm = { refs -> viewModel.edit { it.copy(assignees = refs) }; picker = null },
        )
        Picker.Tags -> SearchPickerDialog(
            title = stringResource(R.string.field_tags),
            selected = form.tags,
            multiple = true,
            search = searchTags,
            onDismiss = { picker = null },
            onConfirm = { refs -> viewModel.edit { it.copy(tags = refs) }; picker = null },
        )
        Picker.Outlet -> SearchPickerDialog(
            title = stringResource(R.string.field_outlet),
            selected = listOfNotNull(form.outlet),
            multiple = false,
            search = searchOutlets,
            onDismiss = { picker = null },
            onConfirm = { refs -> viewModel.edit { it.copy(outlet = refs.firstOrNull()) }; picker = null },
        )
        Picker.Deadline -> DeadlineDialog(
            initial = form.deadline,
            onDismiss = { picker = null },
            onPick = { date -> viewModel.edit { it.copy(deadline = date) }; picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun StageField(stage: Ref?, stages: List<Ref>, onPick: (Ref) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PickerField(R.string.field_stage, stage?.name.orEmpty(), icon = Icons.Filled.ArrowDropDown) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            stages.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { open = false; onPick(s) }) }
        }
    }
}

/** A read-only field that opens a picker when tapped. */
@Composable
private fun PickerField(
    label: Int,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.ArrowDropDown,
    onClick: () -> Unit,
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { Icon(icon, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field would swallow the tap; this layer catches it instead.
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate?) -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) { DatePicker(pickerState) }
}
