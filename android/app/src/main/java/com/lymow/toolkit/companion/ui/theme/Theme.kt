package com.lymow.toolkit.companion.ui.theme

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
import com.lymow.toolkit.companion.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0B5),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF516350),
    secondaryContainer = Color(0xFFD4E8D0),
    tertiary = Color(0xFF39656B),
    tertiaryContainer = Color(0xFFBCEBF2),
    surface = Color(0xFFF6FBF2),
    background = Color(0xFFF6FBF2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD493),
    onPrimary = Color(0xFF003909),
    primaryContainer = Color(0xFF11521B),
    onPrimaryContainer = Color(0xFFB9F0B5),
    secondary = Color(0xFFB8CCB5),
    secondaryContainer = Color(0xFF3A4B39),
    tertiary = Color(0xFFA1CED5),
    tertiaryContainer = Color(0xFF1F4D53),
    surface = Color(0xFF101510),
    background = Color(0xFF101510),
)

@Composable
fun LymowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        // Material You dynamic color on Android 12+
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
