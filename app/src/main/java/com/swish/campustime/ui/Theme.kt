package com.swish.campustime.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4B91),
    secondary = Color(0xFF006A6A),
    tertiary = Color(0xFF8B4A5F),
    background = Color(0xFFF9F8FF),
    surface = Color(0xFFF9F8FF)
)
private val DarkColors = darkColorScheme(primary = Color(0xFFCCBEFF), secondary = Color(0xFF4DDAD5))

@Composable
fun CampusTimeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
