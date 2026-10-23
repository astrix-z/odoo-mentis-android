package ua.pp.odoohost.mentis.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

/** The Odoo server the signed-in user works with; images are loaded from it. */
val LocalServerUrl = staticCompositionLocalOf { "" }

/** A relative Odoo URL (`/web/image/...`) on the current server. */
@Composable
@ReadOnlyComposable
fun serverUrl(path: String): String = LocalServerUrl.current + path

/**
 * The Odoo avatar of a user (or, with [model] `res.partner`, of a message author),
 * or their initials while it loads or when it fails.
 */
@Composable
fun Avatar(id: Int, name: String, size: Dp = 28.dp, modifier: Modifier = Modifier, model: String = "res.users") {
    val shape = CircleShape
    SubcomposeAsyncImage(
        model = serverUrl("/web/image/$model/$id/avatar_128"),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape).border(1.dp, MaterialTheme.colorScheme.surface, shape),
        loading = { Initials(name, size) },
        error = { Initials(name, size) },
    )
}

@Composable
private fun Initials(name: String, size: Dp) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = (size.value * 0.4f).sp)
    }
}
