package ua.pp.odoohost.mentis.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.pp.odoohost.mentis.R
import ua.pp.odoohost.mentis.data.auth.DatabaseRequiredException
import ua.pp.odoohost.mentis.data.auth.InvalidTotpException
import ua.pp.odoohost.mentis.data.odoo.OdooException

/** A user-facing message for an error from the data layer. */
@Composable
fun errorText(error: Throwable): String = when (error) {
    is OdooException.Network -> stringResource(R.string.error_network)
    is OdooException.AccessDenied -> stringResource(R.string.error_credentials)
    is OdooException.AccessError -> stringResource(R.string.error_access)
    is OdooException.Http -> stringResource(R.string.error_http, error.code)
    is DatabaseRequiredException -> stringResource(R.string.error_database_required)
    is InvalidTotpException -> stringResource(R.string.error_totp)
    is IllegalArgumentException -> stringResource(R.string.error_bad_url)
    // Odoo's own messages (UserError, ValidationError) are already translated for the user.
    is OdooException.Server -> error.message.orEmpty()
    else -> stringResource(R.string.error_unknown, error.message ?: error.javaClass.simpleName)
}
