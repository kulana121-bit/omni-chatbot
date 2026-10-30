package com.ravi.omnichat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Claude / ChatGPT style: warm ivory neutrals, near-black text, one clay accent.
private val Light = lightColorScheme(
    primary = Color(0xFFC96442),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E3DA),
    onPrimaryContainer = Color(0xFF5A2A18),
    secondary = Color(0xFF73726C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEBE3),
    onSecondaryContainer = Color(0xFF3D3D3A),
    tertiary = Color(0xFF8A6D3B),
    tertiaryContainer = Color(0xFFF3EBDA),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1F1E1D),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1F1E1D),
    onSurfaceVariant = Color(0xFF6E6D68),
    inverseSurface = Color(0xFF2B2A27),
    inverseOnSurface = Color(0xFFF5F4EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4EE),
    surfaceContainer = Color(0xFFF0EEE6),
    surfaceContainerHigh = Color(0xFFECEAE1),
    surfaceContainerHighest = Color(0xFFE5E2D8),
    outline = Color(0xFFADABA3),
    outlineVariant = Color(0xFFE2DFD5),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9E1DE),
    onErrorContainer = Color(0xFF5C1A15)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFF3A1508),
    primaryContainer = Color(0xFF5A2E1F),
    onPrimaryContainer = Color(0xFFFADDCF),
    secondary = Color(0xFFB7B5AC),
    secondaryContainer = Color(0xFF3A3936),
    onSecondaryContainer = Color(0xFFE6E4DC),
    tertiary = Color(0xFFD9BF8C),
    tertiaryContainer = Color(0xFF4A3F2A),
    background = Color(0xFF262624),
    onBackground = Color(0xFFF2F1EC),
    surface = Color(0xFF262624),
    onSurface = Color(0xFFF2F1EC),
    onSurfaceVariant = Color(0xFFA9A79F),
    inverseSurface = Color(0xFFF2F1EC),
    inverseOnSurface = Color(0xFF262624),
    surfaceContainerLowest = Color(0xFF1F1E1D),
    surfaceContainerLow = Color(0xFF2A2A27),
    surfaceContainer = Color(0xFF30302E),
    surfaceContainerHigh = Color(0xFF393936),
    surfaceContainerHighest = Color(0xFF42423F),
    outline = Color(0xFF6F6E69),
    outlineVariant = Color(0xFF41413E),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C2724),
    onErrorContainer = Color(0xFFF9DEDC)
)

/** Soft gradient used for the logo / hero text. */
val SoftGradient = listOf(Color(0xFFD97757), Color(0xFFE0A27E), Color(0xFFC96442))

/** Everything rounded: fields, chips, menus, cards, sheets and dialogs. */
val RoundShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val Pill = RoundedCornerShape(50)

@Composable
fun OmniTheme(dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val base = androidx.compose.material3.Typography()
    val type = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.1.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    )
    MaterialTheme(colorScheme = scheme, shapes = RoundShapes, typography = type, content = content)
}
