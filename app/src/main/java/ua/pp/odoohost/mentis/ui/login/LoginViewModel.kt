package ua.pp.odoohost.mentis.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.pp.odoohost.mentis.BuildConfig
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.auth.DatabaseRequiredException
import ua.pp.odoohost.mentis.data.auth.LoginResult
import ua.pp.odoohost.mentis.data.auth.SessionStore

data class LoginState(
    val server: String = BuildConfig.DEFAULT_SERVER_URL,
    val database: String = "",
    val login: String = "",
    val password: String = "",
    /** The database field stays hidden until the server turns out not to list its databases. */
    val showDatabase: Boolean = false,
    val totpStep: Boolean = false,
    val code: String = "",
    val rememberDevice: Boolean = true,
    val busy: Boolean = false,
    val error: Throwable? = null,
)

/** Leaving the screen happens by itself: a saved session switches the app to the project list. */
class LoginViewModel(private val auth: AuthRepository, store: SessionStore) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.loginDefaults()
            _state.update {
                it.copy(
                    server = saved.serverUrl ?: it.server,
                    database = saved.database.orEmpty(),
                    showDatabase = saved.database != null,
                    login = saved.login.orEmpty(),
                )
            }
        }
    }

    fun onServer(value: String) = _state.update { it.copy(server = value, error = null) }
    fun onDatabase(value: String) = _state.update { it.copy(database = value, error = null) }
    fun onLogin(value: String) = _state.update { it.copy(login = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }
    fun onRemember(value: Boolean) = _state.update { it.copy(rememberDevice = value) }
    fun backToPassword() = _state.update { it.copy(totpStep = false, code = "", error = null) }

    fun submit() {
        val s = _state.value
        if (s.busy || s.server.isBlank() || s.login.isBlank() || s.password.isEmpty()) return
        run {
            when (auth.login(s.server, s.database, s.login, s.password)) {
                is LoginResult.Success -> _state.update { it.copy(password = "") }
                LoginResult.TotpRequired -> _state.update { it.copy(totpStep = true, code = "") }
            }
        }
    }

    fun submitCode() {
        val s = _state.value
        if (s.busy || s.code.length != 6) return
        run {
            auth.submitTotp(s.code, s.rememberDevice)
            _state.update { it.copy(password = "", code = "") }
        }
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e, showDatabase = it.showDatabase || e is DatabaseRequiredException) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
