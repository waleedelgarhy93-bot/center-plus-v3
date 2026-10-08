package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettings
import com.example.ui.theme.CairoFontFamily

fun resolveThemeColor(colorKey: String?): Color {
    if (colorKey == null) return Color(0xFF2563EB)
    return when (colorKey.lowercase().trim()) {
        "blue", "أزرق", "#2563eb" -> Color(0xFF2563EB)
        "green", "أخضر", "#10b981" -> Color(0xFF10B981)
        "purple", "بنفسجي", "#8b5cf6" -> Color(0xFF8B5CF6)
        "red", "أحمر", "#ef4444" -> Color(0xFFEF4444)
        "orange", "برتقالي", "#f97316" -> Color(0xFFF97316)
        "gray", "grey", "رمادي", "#6b7280" -> Color(0xFF6B7280)
        else -> {
            try {
                if (colorKey.startsWith("#")) {
                    Color(android.graphics.Color.parseColor(colorKey))
                } else {
                    Color(0xFF2563EB)
                }
            } catch (_: Exception) {
                Color(0xFF2563EB)
            }
        }
    }
}

fun formatCenterTitle(raw: String?): String {
    val clean = raw?.trim().orEmpty()
    if (clean.isBlank()) return "سنتر لوبيتال"
    return if (clean.startsWith("سنتر")) clean else "سنتر $clean"
}

fun formatTeacherTitle(raw: String?): String {
    val clean = raw?.trim().orEmpty()
    if (clean.isBlank()) return "أ. وليد الجارحي"
    return if (clean.startsWith("أ.") || clean.startsWith("أستاذ") || clean.startsWith("م.")) clean else "أ. $clean"
}

@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val settingsState by database.appSettingsDao().getSettings().collectAsState(initial = null)
    return settingsState ?: AppSettings(
        id = 1L,
        centerName = "سنتر لوبيتال",
        teacherName = "أ. وليد الجارحي",
        phone = "",
        themeColor = "blue",
        isSetupComplete = true
    )
}

/**
 * مكوّن موحّد للبيانات:
 * يقرأ البيانات من app_settings ويُستخدم في كل الشاشات لتوحيد العرض.
 */
@Composable
fun AppHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    badgeText: String? = null,
    showLogo: Boolean = true,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.(primaryColor: Color) -> Unit)? = null
) {
    val settings = rememberAppSettings()
    val centerTitle = formatCenterTitle(settings.centerName)
    val teacherTitle = formatTeacherTitle(settings.teacherName)
    val primaryColor = resolveThemeColor(settings.themeColor)

    val finalTitle = title ?: centerTitle
    val finalSubtitle = subtitle ?: teacherTitle

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            primaryColor.copy(alpha = 0.2f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (showBack && onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (showLogo) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = primaryColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            primaryColor.copy(alpha = 0.45f)
                        )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "🎓",
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = finalTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!badgeText.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = primaryColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (finalSubtitle.isNotBlank()) {
                        Text(
                            text = finalSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = primaryColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (actions != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    actions(primaryColor)
                }
            }
        }
    }
}
