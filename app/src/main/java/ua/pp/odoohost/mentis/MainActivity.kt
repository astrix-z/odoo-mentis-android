package ua.pp.odoohost.mentis

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.map
import ua.pp.odoohost.mentis.data.auth.UserSession
import ua.pp.odoohost.mentis.ui.login.LoginScreen
import ua.pp.odoohost.mentis.ui.login.LoginViewModel
import ua.pp.odoohost.mentis.ui.projects.ProjectsScreen
import ua.pp.odoohost.mentis.ui.projects.ProjectsViewModel
import ua.pp.odoohost.mentis.ui.theme.MentisTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MentisApp).container
        setContent {
            MentisTheme { App(container) }
        }
    }
}

/** Not yet read from storage vs. read: logged out (null) or logged in. */
private sealed interface AuthState {
    data object Loading : AuthState
    data class Ready(val session: UserSession?) : AuthState
}

@Composable
private fun App(container: AppContainer) {
    val flow = remember {
        container.sessionStore.session.map { session ->
            container.auth.restore(session)
            AuthState.Ready(session)
        }
    }
    val state by flow.collectAsStateWithLifecycle(initialValue = AuthState.Loading)
    val session = (state as? AuthState.Ready)?.session

    LaunchedEffect(session?.lang) { session?.let { applyOdooLanguage(it.lang) } }

    when {
        state == AuthState.Loading -> Unit
        session == null -> LoginScreen(
            viewModel(factory = viewModelFactory { initializer { LoginViewModel(container.auth, container.sessionStore) } }),
        )
        // Keyed by user, so signing in as someone else does not reuse the previous user's list.
        else -> key(session.serverUrl, session.database, session.uid) {
            ProjectsScreen(
                viewModel(
                    key = "projects-${session.serverUrl}-${session.database}-${session.uid}",
                    factory = viewModelFactory { initializer { ProjectsViewModel(container.projects, container.auth) } },
                ),
                userName = session.name,
            )
        }
    }
}

/** Shows the app in the Odoo user's language (ru_RU, uk_UA); other languages fall back to English. */
private fun applyOdooLanguage(odooLang: String) {
    val tag = when (odooLang.substringBefore('_')) {
        "ru" -> "ru"
        "uk" -> "uk"
        else -> "en"
    }
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
