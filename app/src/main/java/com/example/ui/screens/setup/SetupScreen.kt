package com.example.ui.screens.setup

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.repository.SettingsRepository
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.launch

data class ThemeColorItem(
    val id: String,
    val name: String,
    val color: Color
)

@Composable
fun SetupScreen(
    onSetupCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var centerName by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    // 6 مربعات ملونة: أزرق، أخضر، بنفسجي، أحمر، برتقالي، رمادي
    val colorOptions = remember {
        listOf(
            ThemeColorItem("blue", "أزرق", Color(0xFF2563EB)),
            ThemeColorItem("green", "أخضر", Color(0xFF10B981)),
            ThemeColorItem("purple", "بنفسجي", Color(0xFF8B5CF6)),
            ThemeColorItem("red", "أحمر", Color(0xFFEF4444)),
            ThemeColorItem("orange", "برتقالي", Color(0xFFF97316)),
            ThemeColorItem("gray", "رمادي", Color(0xFF6B7280))
        )
    }

    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF2563EB)
    val activeColor = colorOptions[selectedColorIndex].color

    // تحميل الإعدادات الحالية إن وجدت
    LaunchedEffect(Unit) {
        try {
            val db = AppDatabase.getDatabase(context)
            val repo = SettingsRepository(db.appSettingsDao())
            val existing = repo.getSettings()
            if (existing.centerName.isNotBlank()) centerName = existing.centerName
            if (existing.teacherName.isNotBlank()) teacherName = existing.teacherName
            if (existing.phone.isNotBlank()) phone = existing.phone
            val foundIdx = colorOptions.indexOfFirst {
                it.id.equals(existing.themeColor, ignoreCase = true) || it.name == existing.themeColor
            }
            if (foundIdx >= 0) {
                selectedColorIndex = foundIdx
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("setup_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // شعار كبير في الأعلى (🎓)
        Surface(
            modifier = Modifier.size(92.dp),
            shape = CircleShape,
            color = activeColor.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(2.dp, activeColor.copy(alpha = 0.5f))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "🎓",
                    fontSize = 44.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // عنوان: "أهلاً بك في تطبيقك!"
        Text(
            text = "أهلاً بك في تطبيقك!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // نص: "قبل البدء، أدخل بيانات سنترك"
        Text(
            text = "قبل البدء، أدخل بيانات سنترك",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = CairoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // حقل "اسم السنتر" (TextField مع أيقونة مدرسة)
        OutlinedTextField(
            value = centerName,
            onValueChange = { centerName = it },
            label = { Text("اسم السنتر *", fontFamily = CairoFontFamily) },
            placeholder = { Text("مثال: سنتر التفوق التعليمي", fontFamily = CairoFontFamily) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = activeColor
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("center_name_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // حقل "اسم المعلم / الإدارة" (TextField مع أيقونة شخص)
        OutlinedTextField(
            value = teacherName,
            onValueChange = { teacherName = it },
            label = { Text("اسم المعلم / الإدارة *", fontFamily = CairoFontFamily) },
            placeholder = { Text("مثال: أ. محمد أحمد", fontFamily = CairoFontFamily) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = activeColor
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("teacher_name_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // حقل "رقم الموبايل" (TextField مع أيقونة هاتف، اختياري)
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("رقم الموبايل (اختياري)", fontFamily = CairoFontFamily) },
            placeholder = { Text("مثال: 01000000000", fontFamily = CairoFontFamily) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = activeColor
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("phone_input")
        )

        Spacer(modifier = Modifier.height(22.dp))

        // قسم "لون التطبيق" فيه 6 مربعات ملونة: أزرق، أخضر، بنفسجي، أحمر، برتقالي، رمادي
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "لون التطبيق",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorOptions.forEachIndexed { index, item ->
                        val isSelected = index == selectedColorIndex
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(item.color)
                                .clickable { selectedColorIndex = index }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "تم الاختيار",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // زر "حفظ والبدء" (أزرق كبير)
        Button(
            onClick = {
                val cName = centerName.trim()
                val tName = teacherName.trim()
                val ph = phone.trim()

                if (cName.isEmpty() || tName.isEmpty()) {
                    Toast.makeText(context, "الرجاء إدخال اسم السنتر واسم المعلم", Toast.LENGTH_SHORT).show()
                } else {
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val selectedColor = colorOptions[selectedColorIndex]
                            val db = AppDatabase.getDatabase(context)
                            val repo = SettingsRepository(db.appSettingsDao())
                            repo.markSetupComplete(
                                centerName = cName,
                                teacherName = tName,
                                phone = ph,
                                themeColor = selectedColor.id
                            )

                            // تحديث اسم المعلم في الحساب الأساسي إن وجد
                            val adminUser = db.userDao().getUserByUsername("admin")
                            if (adminUser != null) {
                                db.userDao().updateUser(
                                    adminUser.copy(
                                        fullName = tName,
                                        phone = ph
                                    )
                                )
                            }

                            showSuccessDialog = true
                        } catch (e: Exception) {
                            Toast.makeText(context, "حدث خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("save_and_start_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "حفظ والبدء",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // عرض Dialog: "تم حفظ البيانات بنجاح ✅"
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onSetupCompleted()
            },
            title = {
                Text(
                    text = "إعداد التطبيق",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "تم حفظ البيانات بنجاح ✅",
                    fontFamily = CairoFontFamily,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onSetupCompleted()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("setup_success_confirm_button")
                ) {
                    Text(
                        text = "حسنًا",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}
