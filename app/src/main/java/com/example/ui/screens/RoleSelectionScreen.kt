package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppLogoBadge
import com.example.ui.theme.CairoFontFamily

@Composable
fun RoleSelectionScreen(
    onSelectTeacher: () -> Unit,
    onSelectParent: () -> Unit,
    onSelectStudent: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("role_selection_screen")
    ) {
        // Top Header Bar: Small App Branding + Theme Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppLogoBadge(size = 42.dp, iconSize = 24.dp, showGlow = false)
                Column {
                    Text(
                        text = "سنتر لوبيتال",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "أ. وليد الجارحي",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Dark / Light Mode Switch Button
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "الوضع النهاري" else "الوضع الليلي",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Main Greeting & Question
        Text(
            text = "من أنت؟",
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.testTag("role_selection_title")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "اختر صفتك للمتابعة والوصول إلى لوحتك التعليمية",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = CairoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Card 1: معلم / إدارة
        RoleCard(
            title = "معلم / إدارة",
            description = "إدارة السنتر والطلاب والحضور",
            badgeText = "لوحة التحكم",
            icon = Icons.Default.School,
            iconTint = Color(0xFF2563EB),
            containerColor = if (isDarkTheme) Color(0xFF132240) else Color(0xFFEFF6FF),
            borderColor = if (isDarkTheme) Color(0xFF1E3A8A) else Color(0xFFBFDBFE),
            onClick = onSelectTeacher,
            testTag = "role_card_teacher"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: ولي أمر
        RoleCard(
            title = "ولي أمر",
            description = "متابعة حضور ودرجات ابنك",
            badgeText = "متابعة وتقارير",
            icon = Icons.Default.FamilyRestroom,
            iconTint = Color(0xFF0D9488),
            containerColor = if (isDarkTheme) Color(0xFF112E2B) else Color(0xFFF0FDFA),
            borderColor = if (isDarkTheme) Color(0xFF134E48) else Color(0xFF99F6E4),
            onClick = onSelectParent,
            testTag = "role_card_parent"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: طالب
        RoleCard(
            title = "طالب",
            description = "متابعة الدروس والدرجات",
            badgeText = "الواجبات والدروس",
            icon = Icons.Default.SupervisedUserCircle,
            iconTint = Color(0xFF7C3AED),
            containerColor = if (isDarkTheme) Color(0xFF241438) else Color(0xFFFAF5FF),
            borderColor = if (isDarkTheme) Color(0xFF4C1D95) else Color(0xFFE9D5FF),
            onClick = onSelectStudent,
            testTag = "role_card_student"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Center Info Footer Note
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "جميع الحقوق محفوظة لسنتر لوبيتال - إشراف أ. وليد الجارحي",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    badgeText: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Colored Icon Box with Soft Shadow
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(iconTint.copy(alpha = 0.15f))
                        .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(30.dp),
                        tint = iconTint
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        // Pill badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(iconTint.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = iconTint
                                )
                            )
                        }
                    }

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Arrow Indicator (RTL oriented: points towards left)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "دخول",
                tint = iconTint.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
