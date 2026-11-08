package com.rr.numio.files.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rr.numio.files.R

// Inter, SIL OFL 1.1 (see licenses/). Malayalam, Arabic and emoji in file
// names fall back to the phone's own system fonts.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** One set of Numio colours. Official palette (getnumio.org); never a generic mustard. */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    /** Fills: buttons, chips, the selected choice. */
    val amber: Color,
    /** Amber used as text or an icon on the background, dark enough to read in light mode. */
    val amberText: Color,
    val onAmber: Color,
    val error: Color,
    val isDark: Boolean,
) {
    companion object {
        val Dark = Palette(
            bg = Color(0xFF0F0F0E),
            surface = Color(0xFF1B1A18),
            surfaceHigh = Color(0xFF2A2925),
            line = Color(0xFF34322D),
            text = Color(0xFFF1EEE6),
            textDim = Color(0xFFA9A59B), // 7.6:1 on bg
            amber = Color(0xFFFFC21A),
            amberText = Color(0xFFFFC21A),
            onAmber = Color(0xFF1A1400),
            error = Color(0xFFF2B8B5),
            isDark = true,
        )
        val Light = Palette(
            bg = Color(0xFFF6F4EE),
            surface = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFFEAE7DE),
            line = Color(0xFFD9D5CA),
            text = Color(0xFF191815),
            textDim = Color(0xFF5C5850), // 6.6:1 on bg
            amber = Color(0xFFE9A800),
            amberText = Color(0xFF7A5600), // 6.4:1 on bg
            onAmber = Color(0xFF1A1400),
            error = Color(0xFFB3261E),
            isDark = false,
        )
    }
}

/** The app's choice in Settings. */
enum class AppTheme { SYSTEM, DARK, LIGHT }

/**
 * The colours in use right now. Backed by Compose state, so every screen
 * repaints when the theme changes; FilesTheme keeps it current.
 */
object Numio {
    var palette by mutableStateOf(Palette.Dark)

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Line get() = palette.line
    val Text get() = palette.text
    val TextDim get() = palette.textDim
    val Amber get() = palette.amber
    val AmberText get() = palette.amberText
    val OnAmber get() = palette.onAmber
    val Error get() = palette.error

    /** Soft amber behind folder icons and the selected tab. */
    val AmberSoft get() = if (palette.isDark) Color(0xFF3A2F0E) else Color(0xFFF7E3A6)
}

private fun schemeFor(p: Palette) = if (p.isDark) {
    darkColorScheme(
        primary = p.amber, onPrimary = p.onAmber,
        background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh,
        outline = p.line, error = p.error,
    )
} else {
    lightColorScheme(
        primary = p.amber, onPrimary = p.onAmber,
        background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh,
        outline = p.line, error = p.error,
    )
}

fun typographyFor(p: Palette): Typography {
    val base = TextStyle(fontFamily = Inter, color = p.text)
    return Typography(
        headlineMedium = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        titleSmall = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        bodyLarge = base.copy(fontSize = 15.sp),
        bodyMedium = base.copy(fontSize = 14.sp),
        bodySmall = base.copy(fontSize = 13.sp, color = p.textDim),
        labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
        labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
    )
}

fun paletteFor(theme: AppTheme, systemDark: Boolean): Palette = when (theme) {
    AppTheme.DARK -> Palette.Dark
    AppTheme.LIGHT -> Palette.Light
    AppTheme.SYSTEM -> if (systemDark) Palette.Dark else Palette.Light
}

@Composable
fun FilesTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val palette = paletteFor(theme, isSystemInDarkTheme())
    // Set during composition so the first frame already has the right colours
    if (Numio.palette != palette) Numio.palette = palette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.isDark
                isAppearanceLightNavigationBars = !palette.isDark
            }
        }
    }
    val scheme = remember(palette) { schemeFor(palette) }
    val type = remember(palette) { typographyFor(palette) }
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

