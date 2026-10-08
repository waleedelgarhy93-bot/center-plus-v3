package com.example.ui.screens.subjects

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubjectWithGroupCount
import com.example.ui.theme.CairoFontFamily

@Composable
fun AddEditSubjectScreen(
    subjectToEdit: SubjectWithGroupCount? = null,
    onBack: () -> Unit,
    onSubjectSaved: () -> Unit,
    viewModel: SubjectsViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var name by remember { mutableStateOf(subjectToEdit?.name ?: "") }
    var description by remember { mutableStateOf(subjectToEdit?.description ?: "") }
    var selectedIconKey by remember { mutableStateOf(subjectToEdit?.icon ?: "functions") }
    var selectedColorHex by remember { mutableStateOf(subjectToEdit?.color ?: "#EF4444") }
    var isNameError by remember { mutableStateOf(false) }

    val isEditing = subjectToEdit != null
    val screenTitle = if (isEditing) "تعديل المادة" else "إضافة مادة جديدة"
    val scrollState = rememberScrollState()

    val selectedColor = SubjectVisuals.parseColor(selectedColorHex)
    val selectedIcon = SubjectVisuals.getIconByKey(selectedIconKey)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("add_edit_subject_screen")
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
                    .testTag("add_subject_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = screenTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Live Preview Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = selectedColor.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, selectedColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(selectedColor.copy(alpha = 0.2f))
                            .border(1.dp, selectedColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selectedIcon,
                            contentDescription = null,
                            tint = selectedColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = if (name.isBlank()) "اسم المادة (معاينة)" else name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = if (description.isBlank()) "وصف توضيحي للمادة والمقرر" else description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 2
                        )
                    }
                }
            }

            // Field 1: Subject Name (Required)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isNameError = false
                },
                label = { Text("اسم المادة *", fontFamily = CairoFontFamily) },
                placeholder = { Text("مثال: رياضيات، فيزياء، لغة عربية", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                isError = isNameError,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subject_name_input")
            )

            // Field 2: Description (Optional)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("وصف المادة (اختياري)", fontFamily = CairoFontFamily) },
                placeholder = { Text("مثال: منهج الثانوية العامة والأزهرية", fontFamily = CairoFontFamily) },
                leadingIcon = {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subject_description_input")
            )

            // Picker: Choose Icon
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "اختر أيقونة المادة المميزة:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubjectVisuals.availableIcons.take(4).forEach { opt ->
                        IconSelectionPill(
                            option = opt,
                            isSelected = selectedIconKey == opt.key,
                            selectedColor = selectedColor,
                            onClick = { selectedIconKey = opt.key }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubjectVisuals.availableIcons.drop(4).take(4).forEach { opt ->
                        IconSelectionPill(
                            option = opt,
                            isSelected = selectedIconKey == opt.key,
                            selectedColor = selectedColor,
                            onClick = { selectedIconKey = opt.key }
                        )
                    }
                }
            }

            // Picker: Choose Color
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "اختر لون المادة المميز:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubjectVisuals.availableColors.take(4).forEach { opt ->
                        ColorSelectionBox(
                            option = opt,
                            isSelected = selectedColorHex.equals(opt.hex, ignoreCase = true),
                            onClick = { selectedColorHex = opt.hex }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SubjectVisuals.availableColors.drop(4).take(4).forEach { opt ->
                        ColorSelectionBox(
                            option = opt,
                            isSelected = selectedColorHex.equals(opt.hex, ignoreCase = true),
                            onClick = { selectedColorHex = opt.hex }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Save & Cancel
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
                        .testTag("cancel_subject_button")
                ) {
                    Text(
                        text = "إلغاء",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            isNameError = true
                            return@Button
                        }

                        viewModel.saveSubject(
                            id = subjectToEdit?.id ?: 0L,
                            name = name,
                            description = description,
                            icon = selectedIconKey,
                            color = selectedColorHex,
                            onSuccess = onSubjectSaved
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp)
                        .testTag("save_subject_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(
                            text = if (isEditing) "حفظ التعديلات" else "إضافة المادة",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IconSelectionPill(
    option: SubjectIconOption,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp, 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) selectedColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = option.title,
            tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ColorSelectionBox(
    option: SubjectColorOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp, 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(option.color.copy(alpha = 0.25f))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) option.color else option.color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(option.color),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
