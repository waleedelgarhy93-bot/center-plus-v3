package com.example.ui.screens.subjects

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SubjectIconOption(
    val key: String,
    val title: String,
    val icon: ImageVector
)

data class SubjectColorOption(
    val hex: String,
    val title: String,
    val color: Color
)

object SubjectVisuals {
    val availableIcons = listOf(
        SubjectIconOption("functions", "رياضيات ومسائل", Icons.Default.Functions),
        SubjectIconOption("science", "فيزياء وعلوم", Icons.Default.Science),
        SubjectIconOption("biotech", "كيمياء وأحياء", Icons.Default.Biotech),
        SubjectIconOption("calculate", "حساب وإحصاء", Icons.Default.Calculate),
        SubjectIconOption("menu_book", "كتاب وقراءة", Icons.Default.MenuBook),
        SubjectIconOption("auto_stories", "لغات ومناهج", Icons.Default.AutoStories),
        SubjectIconOption("computer", "حاسب وتكنولوجيا", Icons.Default.Computer),
        SubjectIconOption("history_edu", "تاريخ ودراسات", Icons.Default.HistoryEdu)
    )

    val availableColors = listOf(
        SubjectColorOption("#EF4444", "أحمر", Color(0xFFEF4444)),
        SubjectColorOption("#3B82F6", "أزرق", Color(0xFF3B82F6)),
        SubjectColorOption("#10B981", "أخضر", Color(0xFF10B981)),
        SubjectColorOption("#8B5CF6", "بنفسجي", Color(0xFF8B5CF6)),
        SubjectColorOption("#F97316", "برتقالي", Color(0xFFF97316)),
        SubjectColorOption("#06B6D4", "سماوي", Color(0xFF06B6D4)),
        SubjectColorOption("#EC4899", "وردي", Color(0xFFEC4899)),
        SubjectColorOption("#F59E0B", "ذهبي", Color(0xFFF59E0B))
    )

    fun getIconByKey(key: String): ImageVector {
        return availableIcons.firstOrNull { it.key == key }?.icon ?: Icons.Default.Functions
    }

    fun parseColor(hex: String, fallback: Color = Color(0xFF3B82F6)): Color {
        return try {
            val cleanHex = hex.removePrefix("#")
            val colorLong = cleanHex.toLong(16)
            if (cleanHex.length == 6) {
                Color(0xFF000000 or colorLong)
            } else if (cleanHex.length == 8) {
                Color(colorLong)
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
