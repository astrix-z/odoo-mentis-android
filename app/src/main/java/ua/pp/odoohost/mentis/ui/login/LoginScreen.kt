package ua.pp.odoohost.mentis.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.pp.odoohost.mentis.R
import ua.pp.odoohost.mentis.ui.common.errorText

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            // A phone-width column, centred on tablets.
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.totpStep) TotpForm(state, viewModel) else PasswordForm(state, viewModel)
                state.error?.let {
                    Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PasswordForm(state: LoginState, viewModel: LoginViewModel) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = state.server,
        onValueChange = viewModel::onServer,
        label = { Text(stringResource(R.string.login_server)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.showDatabase) {
        OutlinedTextField(
            value = state.database,
            onValueChange = viewModel::onDatabase,
            label = { Text(stringResource(R.string.login_database)) },
            placeholder = { Text(stringResource(R.string.login_database_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = state.login,
        onValueChange = viewModel::onLogin,
        label = { Text(stringResource(R.string.login_login)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPassword,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton(R.string.login_submit, state.busy, onClick = viewModel::submit)
}

@Composable
private fun TotpForm(state: LoginState, viewModel: LoginViewModel) {
    Text(stringResource(R.string.totp_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.totp_hint), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCode,
        label = { Text(stringResource(R.string.totp_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitCode() }),
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.rememberDevice, onCheckedChange = viewModel::onRemember, enabled = !state.busy)
        Text(stringResource(R.string.totp_remember))
    }
    SubmitButton(R.string.totp_submit, state.busy, onClick = viewModel::submitCode)
    TextButton(onClick = viewModel::backToPassword, enabled = !state.busy) {
        Text(stringResource(R.string.totp_back))
    }
}

@Composable
private fun SubmitButton(label: Int, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(label))
        }
    }
}
