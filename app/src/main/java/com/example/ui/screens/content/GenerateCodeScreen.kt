package com.example.ui.screens.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppDatabase
import com.example.data.model.AccessCode
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

@Composable
fun GenerateCodeScreen(
    subjectId: Long,
    subjectName: String,
    onBack: () -> Unit,
    onCodeGenerated: () -> Unit,
    viewModel: ContentViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val accessCodeDao = remember { database.accessCodeDao() }

    // 0 = كود طالب سنتر, 1 = كود طالب أونلاين
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Center student state
    var centerCardNumber by remember { mutableStateOf("") }

    // Online student state
    val subjectsList by viewModel.subjects.collectAsStateWithLifecycle()
    var onlineSelectedSubjectId by remember { mutableLongStateOf(subjectId) }
    var onlineSelectedSubjectName by remember {
        mutableStateOf(
            subjectName.ifBlank { "رياضة" }
        )
    }
    var onlineCodeCountText by remember { mutableStateOf("1") }
    var isSubjectDropdownExpanded by remember { mutableStateOf(false) }

    // Online codes list generated during this session or recently
    var generatedOnlineCodes by remember { mutableStateOf<List<AccessCode>>(emptyList()) }
    var isGeneratingCodes by remember { mutableStateOf(false) }

    val prefix = remember(subjectName) {
        when {
            subjectName.contains("رياض") -> "RWD"
            subjectName.contains("فيز") -> "PHZ"
            subjectName.contains("كيم") -> "CHM"
            subjectName.contains("عرب") -> "ARB"
            subjectName.contains("إنج") || subjectName.contains("انج") -> "ENG"
            else -> subjectName.take(3).uppercase(Locale.ENGLISH)
        }
    }

    val cleanCenterCard = centerCardNumber.trim()
    val centerPreviewCode = "$prefix-${if (cleanCenterCard.isNotBlank()) cleanCenterCard else "____"}"

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("generate_code_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "توليد أكواد التفعيل",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        // 1. خياران في الأعلى (Tabs): "كود طالب سنتر" و "كود طالب أونلاين"
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = if (selectedTabIndex == 0) Color(0xFFEAB308) else Color(0xFF2563EB),
                    height = 3.dp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = if (selectedTabIndex == 0) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "كود طالب سنتر",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == 0) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            )

            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (selectedTabIndex == 1) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "كود طالب أونلاين",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == 1) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area according to selected tab
        if (selectedTabIndex == 0) {
            // ================= 5. كود طالب سنتر (السلوك القديم) =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live Preview Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAB308).copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEAB308).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEAB308).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = Color(0xFFEAB308),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "معاينة كود طالب سنتر (مربوط بكارت)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = centerPreviewCode,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEAB308),
                                    letterSpacing = 2.sp
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }

                        Text(
                            text = "المادة: $subjectName • صيغة الكود: [$prefix - رقم الكارت]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Card Number Input
                OutlinedTextField(
                    value = centerCardNumber,
                    onValueChange = { centerCardNumber = it },
                    label = { Text("أدخل رقم كارت الطالب *", fontFamily = CairoFontFamily) },
                    placeholder = { Text("مثال: 3118 أو 1001", fontFamily = CairoFontFamily) },
                    leadingIcon = {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { centerCardNumber = Random.nextInt(1000, 9999).toString() }) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "توليد رقم عشوائي", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEAB308),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf("3118", "1001", "1002", "1003")
                    presets.forEach { sampleCard ->
                        Surface(
                            onClick = { centerCardNumber = sampleCard },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = sampleCard,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("إلغاء", fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            viewModel.generateCode(
                                subjectId = subjectId,
                                subjectName = subjectName,
                                cardNumber = centerCardNumber,
                                isOnline = false,
                                onSuccess = onCodeGenerated
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEAB308),
                            contentColor = Color(0xFF713F12)
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .testTag("confirm_generate_center_code_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                            Text(
                                text = "توليد الكود الآن",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // ================= 2. كود طالب أونلاين =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // حقل: "اسم المادة" (Dropdown من المواد)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = onlineSelectedSubjectName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اسم المادة", fontFamily = CairoFontFamily) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = Color(0xFF2563EB)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isSubjectDropdownExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "قائمة المواد",
                                    tint = Color(0xFF2563EB)
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSubjectDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = isSubjectDropdownExpanded,
                        onDismissRequest = { isSubjectDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        val availableSubjects = if (subjectsList.isNotEmpty()) {
                            subjectsList.map { it.id to it.name }
                        } else {
                            listOf(1L to "رياضة", 2L to "فيزياء", 3L to "كيمياء")
                        }

                        availableSubjects.forEach { (sId, sName) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sName,
                                        fontFamily = CairoFontFamily,
                                        fontWeight = if (sId == onlineSelectedSubjectId) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sId == onlineSelectedSubjectId) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onlineSelectedSubjectId = sId
                                    onlineSelectedSubjectName = sName
                                    isSubjectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // حقل: "عدد الأكواد المطلوبة" (رقم افتراضي 1)
                OutlinedTextField(
                    value = onlineCodeCountText,
                    onValueChange = { onlineCodeCountText = it },
                    label = { Text("عدد الأكواد المطلوبة", fontFamily = CairoFontFamily) },
                    placeholder = { Text("1", fontFamily = CairoFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Numbers,
                            contentDescription = null,
                            tint = Color(0xFF2563EB)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // زر: "توليد الأكواد"
                Button(
                    onClick = {
                        val count = onlineCodeCountText.toIntOrNull() ?: 1
                        if (count <= 0) {
                            Toast.makeText(context, "يرجى إدخال عدد صحيح للأكواد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isGeneratingCodes = true
                        scope.launch {
                            try {
                                val generatedList = mutableListOf<AccessCode>()
                                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                                withContext(Dispatchers.IO) {
                                    for (i in 1..count) {
                                        var codeCandidate = ""
                                        var exists = true
                                        while (exists) {
                                            codeCandidate = (1..8)
                                                .map { chars[Random.nextInt(chars.length)] }
                                                .joinToString("")
                                            exists = accessCodeDao.getCodeByString(codeCandidate) != null
                                        }

                                        val newCode = AccessCode(
                                            code = codeCandidate,
                                            subjectId = onlineSelectedSubjectId,
                                            cardNumber = null,
                                            isOnline = true,
                                            usedByStudentId = null,
                                            usedAt = null
                                        )
                                        val id = accessCodeDao.insertCode(newCode)
                                        generatedList.add(newCode.copy(id = id))
                                    }
                                }

                                generatedOnlineCodes = generatedList
                                isGeneratingCodes = false
                                Toast.makeText(
                                    context,
                                    "تم توليد $count كود أونلاين لمادة $onlineSelectedSubjectName بنجاح",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                isGeneratingCodes = false
                                Toast.makeText(context, "حدث خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isGeneratingCodes,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_online_codes_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = null)
                        Text(
                            text = if (isGeneratingCodes) "جاري التوليد..." else "توليد الأكواد",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // 3 & 4. عرض الأكواد في قائمة تحت الشاشة مع زري "نسخ" و "إرسال واتساب"
                if (generatedOnlineCodes.isNotEmpty()) {
                    Text(
                        text = "الأكواد المُولّدة (${generatedOnlineCodes.size}):",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(generatedOnlineCodes) { accessCode ->
                            OnlineCodeCard(
                                code = accessCode.code,
                                subjectName = onlineSelectedSubjectName,
                                context = context
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// 4. كارت عرض الكود مع زري "نسخ" 📋 و "إرسال واتساب" 📤
@Composable
private fun OnlineCodeCard(
    code: String,
    subjectName: String,
    context: Context,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            Color(0xFF2563EB).copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // الكود المميز
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF2563EB).copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB),
                            letterSpacing = 2.sp
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "متاح للبيع",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "المادة: $subjectName • كود مستقل 8 خانات",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = CairoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // أزرار: نسخ و إرسال واتساب
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // زر نسخ 📋
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OnlineAccessCode", code))
                        Toast.makeText(context, "تم نسخ الكود: $code", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "نسخ",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // زر إرسال واتساب 📤
                Button(
                    onClick = {
                        val message = "كود اشتراك مادة $subjectName على منصة سنتر لوبيتال التعليمية:\n$code\n\nقم بالتسجيل في التطبيق واستخدم الكود لتفعيل المادة ومتابعة الفيديوهات والاختبارات فوراً."
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "إرسال كود التفعيل"))
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "إرسال واتساب",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "إرسال واتساب",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
