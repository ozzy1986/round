package com.raund.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A00),
    onPrimary = Color(0xFF462A00),
    primaryContainer = Color(0xFF5C3000),
    onPrimaryContainer = Color(0xFFFFD6A8),
    secondary = Color(0xFFCE93D8),
    onSecondary = Color(0xFF38004A),
    secondaryContainer = Color(0xFF4A1462),
    onSecondaryContainer = Color(0xFFF3E0F5),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF462A00),
    background = Color(0xFF1A0F1E),
    surface = Color(0xFF1A0F1E),
    surfaceVariant = Color(0xFF2D1B35),
    onSurfaceVariant = Color(0xFFD4BFD8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDFCE),
    onPrimaryContainer = Color(0xFF3E1500),
    secondary = Color(0xFF7B1FA2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8D0F0),
    onSecondaryContainer = Color(0xFF38003E),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceVariant = Color(0xFFF0E4F5),
    onSurfaceVariant = Color(0xFF4A3850),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val RaundTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun RaundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RaundTypography,
        content = content
    )
}
