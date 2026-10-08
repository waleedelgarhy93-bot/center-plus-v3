package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ThemeManager
import java.io.File

@Composable
fun AppLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    iconSize: Dp = 48.dp,
    showGlow: Boolean = true,
    customLogoPath: String? = null,
    themeHex: String = "#2563EB"
) {
    val themeOption = remember(themeHex) { ThemeManager.getThemeOptionByHex(themeHex) }
    val primary = themeOption.primaryColor
    val darkContainer = themeOption.containerDark

    val bitmap = remember(customLogoPath) {
        if (!customLogoPath.isNullOrBlank()) {
            val file = File(customLogoPath)
            if (file.exists() && file.length() > 0) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier.shadow(16.dp, CircleShape, spotColor = primary.copy(alpha = 0.4f))
                } else Modifier
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primary,
                        darkContainer,
                        Color(0xFF0F172A)
                    )
                )
            )
            .border(2.dp, Color(0x55FBBF24), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "شعار السنتر",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            // Inner subtle ring with School/Graduation icon
            Box(
                modifier = Modifier
                    .size(size - 12.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "شعار السنتر",
                    modifier = Modifier.size(iconSize),
                    tint = Color(0xFFFBBF24)
                )
            }
        }
    }
}

