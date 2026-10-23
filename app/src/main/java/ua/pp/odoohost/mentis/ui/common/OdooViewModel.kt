package ua.pp.odoohost.mentis.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ua.pp.odoohost.mentis.data.auth.AuthRepository
import ua.pp.odoohost.mentis.data.odoo.OdooException

/** Runs Odoo calls; an expired session signs the user out, which brings back the login screen. */
abstract class OdooViewModel(private val auth: AuthRepository) : ViewModel() {

    protected fun launchSafe(
        onError: (Throwable) -> Unit,
        onFinally: () -> Unit = {},
        block: suspend () -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: OdooException.SessionExpired) {
            auth.logout()
        } catch (e: Exception) {
            onError(e)
        } finally {
            onFinally()
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
