package ua.pp.odoohost.mentis.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import ua.pp.odoohost.mentis.R
import ua.pp.odoohost.mentis.data.odoo.Ref

/**
 * Picks records found by Odoo's name_search. With [multiple] the user ticks several and
 * confirms; otherwise a tap picks one, and "Clear" removes the value.
 */
@Composable
fun SearchPickerDialog(
    title: String,
    selected: List<Ref>,
    multiple: Boolean,
    search: suspend (String) -> List<Ref>,
    onDismiss: () -> Unit,
    onConfirm: (List<Ref>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Ref>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val chosen = remember { mutableStateListOf<Ref>().apply { addAll(selected) } }

    LaunchedEffect(query) {
        delay(if (query.isEmpty()) 0 else 300)
        loading = true
        try {
            results = search(query)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        }
        loading = false
    }

    // Already chosen records stay on top, so they can be unticked whatever the search.
    val shown = chosen.toList() + results.filter { r -> chosen.none { it.id == r.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { ref ->
                        val isChosen = chosen.any { it.id == ref.id }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (multiple) {
                                    if (isChosen) chosen.removeAll { it.id == ref.id } else chosen += ref
                                } else {
                                    onConfirm(listOf(ref))
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) Checkbox(checked = isChosen, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            else RadioButton(selected = isChosen, onClick = null, modifier = Modifier.padding(8.dp))
                            Text(ref.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiple) {
                TextButton(onClick = { onConfirm(chosen.toList()) }) { Text(stringResource(R.string.done)) }
            } else if (selected.isNotEmpty()) {
                TextButton(onClick = { onConfirm(emptyList()) }) { Text(stringResource(R.string.clear)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
