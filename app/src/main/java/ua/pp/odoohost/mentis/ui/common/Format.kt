package ua.pp.odoohost.mentis.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Formats in the language of the UI (set from the Odoo user), not the phone's default.

@Composable
@ReadOnlyComposable
fun LocalDate.formatted(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))

@Composable
@ReadOnlyComposable
fun Instant.formatted(): String =
    atZone(ZoneId.systemDefault()).format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(LocalConfiguration.current.locales[0]),
    )
