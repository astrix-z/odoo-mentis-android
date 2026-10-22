package ua.pp.odoohost.mentis.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OdooPurple = Color(0xFF714B67)

private val LightColors = lightColorScheme(primary = OdooPurple, secondary = Color(0xFF017E84))
private val DarkColors = darkColorScheme(primary = Color(0xFFD7B4CD), secondary = Color(0xFF6FD3D9))

@Composable
fun MentisTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Odoo's kanban colour indexes 1–11; 0 means "no colour". */
fun odooColor(index: Int): Color? = when (index) {
    1 -> Color(0xFFF06050)
    2 -> Color(0xFFF4A460)
    3 -> Color(0xFFF7CD1F)
    4 -> Color(0xFF6CC1ED)
    5 -> Color(0xFF814968)
    6 -> Color(0xFFEB7E7F)
    7 -> Color(0xFF2C8397)
    8 -> Color(0xFF475577)
    9 -> Color(0xFFD6145F)
    10 -> Color(0xFF30C381)
    11 -> Color(0xFF9365B8)
    else -> null
}
