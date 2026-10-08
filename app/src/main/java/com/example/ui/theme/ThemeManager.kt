package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class ThemeOption(
    val id: String,
    val name: String,
    val hex: String,
    val primaryColor: Color,
    val containerDark: Color,
    val containerLight: Color
)

object ThemeManager {

    val ThemeOptions = listOf(
        ThemeOption(
            id = "blue",
            name = "أزرق كلاسيكي",
            hex = "#2563EB",
            primaryColor = Color(0xFF2563EB),
            containerDark = Color(0xFF1E3A8A),
            containerLight = Color(0xFFDBEAFE)
        ),
        ThemeOption(
            id = "green",
            name = "أخضر زمردي",
            hex = "#10B981",
            primaryColor = Color(0xFF10B981),
            containerDark = Color(0xFF065F46),
            containerLight = Color(0xFFD1FAE5)
        ),
        ThemeOption(
            id = "purple",
            name = "بنفسجي ملكي",
            hex = "#8B5CF6",
            primaryColor = Color(0xFF8B5CF6),
            containerDark = Color(0xFF5B21B6),
            containerLight = Color(0xFFEDE9FE)
        ),
        ThemeOption(
            id = "red",
            name = "أحمر ياقوتي",
            hex = "#EF4444",
            primaryColor = Color(0xFFEF4444),
            containerDark = Color(0xFF991B1B),
            containerLight = Color(0xFFFEE2E2)
        ),
        ThemeOption(
            id = "orange",
            name = "برتقالي دافئ",
            hex = "#F97316",
            primaryColor = Color(0xFFF97316),
            containerDark = Color(0xFF9A3412),
            containerLight = Color(0xFFFFEDD5)
        ),
        ThemeOption(
            id = "gray",
            name = "رمادي عصري",
            hex = "#4B5563",
            primaryColor = Color(0xFF4B5563),
            containerDark = Color(0xFF1F2937),
            containerLight = Color(0xFFE5E7EB)
        )
    )

    fun getThemeOptionByHex(hex: String): ThemeOption {
        return ThemeOptions.firstOrNull { it.hex.equals(hex.trim(), ignoreCase = true) }
            ?: ThemeOptions.first()
    }

    fun createDarkColorScheme(themeHex: String): ColorScheme {
        val option = getThemeOptionByHex(themeHex)
        return darkColorScheme(
            primary = option.primaryColor,
            onPrimary = Color.White,
            primaryContainer = option.containerDark,
            onPrimaryContainer = Color(0xFFE2E8F0),
            secondary = option.primaryColor.copy(alpha = 0.85f),
            onSecondary = Color(0xFF0F172A),
            secondaryContainer = option.containerDark.copy(alpha = 0.6f),
            onSecondaryContainer = Color(0xFFF1F5F9),
            tertiary = AccentGoldDark,
            onTertiary = Color(0xFF451A03),
            background = BackgroundDark,
            onBackground = TextPrimaryDark,
            surface = SurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = DividerDark
        )
    }

    fun createLightColorScheme(themeHex: String): ColorScheme {
        val option = getThemeOptionByHex(themeHex)
        return lightColorScheme(
            primary = option.primaryColor,
            onPrimary = Color.White,
            primaryContainer = option.containerLight,
            onPrimaryContainer = option.containerDark,
            secondary = option.primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.White,
            secondaryContainer = option.containerLight.copy(alpha = 0.6f),
            onSecondaryContainer = option.containerDark,
            tertiary = AccentGold,
            onTertiary = Color.White,
            background = BackgroundLight,
            onBackground = TextPrimaryLight,
            surface = SurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = DividerLight
        )
    }
}
