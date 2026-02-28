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
    primary = Color(0xFFFF8A65),
    onPrimary = Color(0xFF3E1500),
    primaryContainer = Color(0xFF5C2200),
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFF78DAD4),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF1E4E4C),
    onSecondaryContainer = Color(0xFFA4F1EB),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF462A00),
    background = Color(0xFF1A1110),
    surface = Color(0xFF1A1110),
    surfaceVariant = Color(0xFF2D2220),
    onSurfaceVariant = Color(0xFFD8C2BC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00251E),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF5EDEA),
    onSurfaceVariant = Color(0xFF534341),
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
    dynamicColor: Boolean = true,
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
