package com.example.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.model.AttendanceRecord
import com.example.ui.components.rememberAppSettings
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScanMode {
    CAMERA,
    EXTERNAL
}

data class WhatsAppAttendanceDialogData(
    val studentName: String,
    val parentPhone: String,
    val time: String,
    val date: String
)

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = viewModel()
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var selectedMode by remember { mutableStateOf(ScanMode.CAMERA) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedMode) {
        if (selectedMode == ScanMode.CAMERA && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val last5Attendances by viewModel.last5Attendances.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val bannerNotice by viewModel.bannerNotice.collectAsStateWithLifecycle()
    val unknownBarcodeDialog by viewModel.unknownBarcodeDialog.collectAsStateWithLifecycle()

    val settings = rememberAppSettings()
    val database = remember { AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    var whatsAppDialogData by remember { mutableStateOf<WhatsAppAttendanceDialogData?>(null) }
    var lastHandledBannerForWhatsApp by remember { mutableStateOf<BannerNotice?>(null) }

    LaunchedEffect(bannerNotice) {
        val notice = bannerNotice
        if (notice != null && notice.isSuccess && notice !== lastHandledBannerForWhatsApp) {
            lastHandledBannerForWhatsApp = notice
            val barcode = notice.barcode
            val student = withContext(Dispatchers.IO) {
                if (!barcode.isNullOrBlank()) {
                    database.studentDao().getStudentByBarcode(barcode)
                } else {
                    null
                }
            }
            val currentTimeStr = SimpleDateFormat("hh:mm a", Locale("ar")).format(Date())
            val currentDateStr = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date())

            whatsAppDialogData = WhatsAppAttendanceDialogData(
                studentName = student?.name ?: notice.studentName ?: "الطالب",
                parentPhone = student?.parentPhone ?: "",
                time = currentTimeStr,
                date = currentDateStr
            )
        }
    }

    var showManualInputDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("scanner_screen")
    ) {
        // 1. Top Bar: Title + RTL Back + Flash Button + Manual Input
        ScannerTopBar(
            onBack = onBack,
            isFlashOn = isFlashOn,
            isCameraMode = selectedMode == ScanMode.CAMERA,
            onToggleFlash = { viewModel.toggleFlash() },
            onOpenManualInput = { showManualInputDialog = true }
        )

        // 1.5 Scan Mode Toggle: Camera vs External Scanner
        ScanModeToggleRow(
            selectedMode = selectedMode,
            onModeSelected = { selectedMode = it }
        )

        // Banner Status Notification (Animated)
        AnimatedVisibility(
            visible = bannerNotice != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            bannerNotice?.let { notice ->
                ScanBannerView(notice = notice)
            }
        }

        // 2. Camera Preview OR External Scanner in Center (Fixed height)
        if (selectedMode == ScanMode.CAMERA) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreviewView(
                        isFlashEnabled = isFlashOn,
                        onBarcodeDetected = { barcode ->
                            lastScannedBarcode = barcode
                            viewModel.onBarcodeScanned(barcode)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Custom Framing Target Overlay with Corner Brackets
                    ScannerFramingOverlay(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Permission Denied / Prompt UI
                    CameraPermissionPrompt(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }
            }

            // Instruction Text under Camera
            Text(
                text = "وجّه الكاميرا نحو الباركود (Code128 / QR Code)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("camera_instruction_text")
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                ExternalScannerView(
                    lastScannedBarcode = lastScannedBarcode,
                    onBarcodeReceived = { barcode ->
                        lastScannedBarcode = barcode
                        viewModel.onBarcodeScanned(barcode)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Instruction Text under External Scanner
            Text(
                text = "وجّه الماسح نحو الكارت أو امسح الباركود مباشرة",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("external_instruction_text")
            )
        }

        // 3. Bottom Section: Last 5 Attendance Records
        LastAttendanceSection(
            records = last5Attendances,
            onWhatsAppClick = { record ->
                coroutineScope.launch {
                    val student = withContext(Dispatchers.IO) {
                        database.studentDao().getStudentById(record.studentId)
                    }
                    whatsAppDialogData = WhatsAppAttendanceDialogData(
                        studentName = record.studentName,
                        parentPhone = student?.parentPhone ?: "",
                        time = record.time,
                        date = record.date
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    // WhatsApp Notification Dialog for Parent
    whatsAppDialogData?.let { data ->
        WhatsAppAttendanceDialog(
            data = data,
            onDismiss = { whatsAppDialogData = null },
            onSendWhatsApp = {
                sendAttendanceWhatsAppMessage(
                    context = context,
                    studentName = data.studentName,
                    parentPhone = data.parentPhone,
                    date = data.date,
                    time = data.time,
                    centerName = settings.centerName
                )
                whatsAppDialogData = null
            }
        )
    }

    // Dialog for Unknown Barcode: Quick Register Student
    unknownBarcodeDialog?.let { barcode ->
        QuickAddStudentDialog(
            barcode = barcode,
            onDismiss = { viewModel.dismissUnknownDialog() },
            onConfirm = { name, group, phone ->
                viewModel.registerNewStudent(
                    name = name,
                    barcode = barcode,
                    groupName = group,
                    parentPhone = phone
                )
            }
        )
    }

    // Manual Barcode Input Dialog (for Testing / Emulator / Damaged Cards)
    if (showManualInputDialog) {
        ManualBarcodeInputDialog(
            onDismiss = { showManualInputDialog = false },
            onSubmit = { code ->
                viewModel.onBarcodeScanned(code)
                showManualInputDialog = false
            }
        )
    }
}

@Composable
private fun ScannerTopBar(
    onBack: () -> Unit,
    isFlashOn: Boolean,
    isCameraMode: Boolean,
    onToggleFlash: () -> Unit,
    onOpenManualInput: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("scanner_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = "تسجيل الحضور",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = if (isCameraMode) "مسح الباركود بالكاميرا" else "مسح عبر ماسح خارجي (USB / BT)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Action Buttons: Flash & Keyboard Input
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Manual barcode key-in button
            IconButton(
                onClick = onOpenManualInput,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(42.dp)
                    .testTag("manual_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "إدخال يدوي",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Flash / Torch Button (Shown in camera mode)
            if (isCameraMode) {
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isFlashOn) Color(0xFFFBBF24) else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .size(42.dp)
                        .testTag("flash_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (isFlashOn) "إيقاف الإضاءة" else "تشغيل الإضاءة",
                        tint = if (isFlashOn) Color(0xFF78350F) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanModeToggleRow(
    selectedMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Camera Button
            val isCamera = selectedMode == ScanMode.CAMERA
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onModeSelected(ScanMode.CAMERA) }
                    .testTag("scan_mode_camera"),
                shape = RoundedCornerShape(12.dp),
                color = if (isCamera) MaterialTheme.colorScheme.primary else Color.Transparent,
                shadowElevation = if (isCamera) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = if (isCamera) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📷 كاميرا",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = if (isCamera) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCamera) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // External Scanner Button
            val isExternal = selectedMode == ScanMode.EXTERNAL
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onModeSelected(ScanMode.EXTERNAL) }
                    .testTag("scan_mode_external"),
                shape = RoundedCornerShape(12.dp),
                color = if (isExternal) MaterialTheme.colorScheme.primary else Color.Transparent,
                shadowElevation = if (isExternal) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (isExternal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📡 ماسح خارجي",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = if (isExternal) FontWeight.Bold else FontWeight.Medium,
                            color = if (isExternal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalScannerView(
    lastScannedBarcode: String?,
    onBarcodeReceived: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var textInput by remember { mutableStateOf("") }

    // Ensure hidden text field is focused automatically when external scanner view is active
    LaunchedEffect(Unit) {
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun submitBarcode(rawCode: String) {
        val clean = rawCode.trim()
        if (clean.isNotEmpty()) {
            onBarcodeReceived(clean)
        }
        textInput = ""
    }

    val pulseTransition = rememberInfiniteTransition(label = "scanner_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanner_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            .padding(16.dp)
            .testTag("external_scanner_container"),
        contentAlignment = Alignment.Center
    ) {
        // Hidden transparent text field that always captures keystrokes and Enter action from the barcode scanner
        BasicTextField(
            value = textInput,
            onValueChange = { newValue ->
                if (newValue.contains('\n') || newValue.contains('\r')) {
                    val code = newValue.replace("\n", "").replace("\r", "").trim()
                    if (code.isNotEmpty()) {
                        submitBarcode(code)
                    } else {
                        textInput = ""
                    }
                } else {
                    textInput = newValue
                }
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if ((event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                        event.type == KeyEventType.KeyUp
                    ) {
                        submitBarcode(textInput)
                        true
                    } else {
                        false
                    }
                }
                .testTag("external_scanner_hidden_field"),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Ascii
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    submitBarcode(textInput)
                }
            ),
            singleLine = true
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Big Scanner Icon with pulse effect
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(
                        2.dp,
                        Brush.radialGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                        ),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "ماسح خارجي",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier
                        .size(52.dp)
                        .scale(pulseScale)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Prompt Text
            Text(
                text = "وجّه الماسح نحو الكارت",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("external_scanner_prompt_text")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Scanner Status Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF16A34A).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Text(
                        text = "الماسح متصل وجاهز للاستقبال (USB / BT)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4ADE80),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Display Last Scanned Barcode Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(
                    1.dp,
                    if (lastScannedBarcode != null) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .testTag("external_last_barcode_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "آخر باركود تم استقباله:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = lastScannedBarcode ?: "لم يتم مسح أي كارت بعد",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (lastScannedBarcode != null) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        )
                    }

                    if (lastScannedBarcode != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF16A34A).copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "انقر على الشاشة في أي وقت لإعادة تركيز الماسح (Focus)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = CairoFontFamily,
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanBannerView(notice: BannerNotice) {
    val bgColor = when {
        notice.isSuccess -> Color(0xFF16A34A)
        notice.isWarning -> Color(0xFFEA580C)
        else -> Color(0xFFDC2626)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("scanner_banner_notice")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when {
                    notice.isSuccess -> Icons.Default.CheckCircle
                    notice.isWarning -> Icons.Default.Warning
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Composable
private fun ScannerFramingOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan_laser")
    val laserY by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = modifier) {
        val boxWidth = size.width * 0.76f
        val boxHeight = size.height * 0.62f
        val left = (size.width - boxWidth) / 2f
        val top = (size.height - boxHeight) / 2f
        val right = left + boxWidth
        val bottom = top + boxHeight

        // Dark dimming around viewfinder box
        // Top rect
        drawRect(Color(0x77000000), Offset(0f, 0f), Size(size.width, top))
        // Bottom rect
        drawRect(Color(0x77000000), Offset(0f, bottom), Size(size.width, size.height - bottom))
        // Left rect
        drawRect(Color(0x77000000), Offset(0f, top), Size(left, boxHeight))
        // Right rect
        drawRect(Color(0x77000000), Offset(right, top), Size(size.width - right, boxHeight))

        // Center Viewfinder Border (Subtle white)
        drawRect(
            color = Color(0x33FFFFFF),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )

        // 4 Colored Corner Brackets (Vibrant Cyan / Blue / Gold)
        val cornerLen = 28.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val cornerColor = Color(0xFF38BDF8) // Vibrant sky blue

        // Top-Left Corner
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), cornerStroke)

        // Top-Right Corner
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerLen, top), cornerStroke)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), cornerStroke)

        // Bottom-Left Corner
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLen), cornerStroke)

        // Bottom-Right Corner
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLen, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), cornerStroke)

        // Animated Scanning Red/Cyan Laser Line
        val currentLaserY = top + (boxHeight * laserY)
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(left + 8.dp.toPx(), currentLaserY),
            end = Offset(right - 8.dp.toPx(), currentLaserY),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = Color(0xFF60A5FA),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "إذن الكاميرا مطلوب",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "يرجى السماح بالوصول للكاميرا لقراءة باركود بطاقات الطلاب تلقائيًا",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = CairoFontFamily,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "منح الإذن الآن",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
    }
}

@Composable
private fun LastAttendanceSection(
    records: List<AttendanceRecord>,
    onWhatsAppClick: (AttendanceRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "آخر 5 عمليات تسجيل",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${records.size} مسجلين",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "لا توجد عمليات حضور مسجلة حتى الآن",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "قم بتوجيه الكاميرا إلى كارت الطالب أو استخدم الإدخال اليدوي",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(records, key = { it.attendanceId }) { record ->
                        AttendanceItemCard(
                            record = record,
                            onWhatsAppClick = { onWhatsAppClick(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceItemCard(
    record: AttendanceRecord,
    onWhatsAppClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16A34A).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "حاضر",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = record.studentName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "${record.groupName} • كود: ${record.barcode}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Right side: WhatsApp trigger + Time and status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366).copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "إرسال واتساب لولي الأمر",
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = record.time,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = record.status,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddStudentDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, group: String, phone: String) -> Unit
) {
    var studentName by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("مجموعة 3 ثانوي - عام") }
    var parentPhone by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "إضافة طالب جديد للسنتر",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "تم رصد باركود غير مسجل: $barcode",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = studentName,
                    onValueChange = {
                        studentName = it
                        isError = false
                    },
                    label = { Text("اسم الطالب ثلاثي", fontFamily = CairoFontFamily) },
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("المجموعة / الصف الدراسي", fontFamily = CairoFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = parentPhone,
                    onValueChange = { parentPhone = it },
                    label = { Text("رقم واتساب ولي الأمر", fontFamily = CairoFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (studentName.trim().isNotEmpty()) {
                        onConfirm(studentName.trim(), groupName.trim(), parentPhone.trim())
                    } else {
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("حفظ وتسجيل الحضور", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = CairoFontFamily)
            }
        }
    )
}

@Composable
private fun ManualBarcodeInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var inputCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إدخال رقم الباركود يدويًا",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "أدخل رقم كارت الطالب أو اختر من الأكواد التجريبية الجاهزة:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("رقم الباركود (مثال: STU101 أو 1001)", fontFamily = CairoFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("STU101", "STU102", "1001").forEach { sampleCode ->
                        Surface(
                            onClick = { inputCode = sampleCode },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = sampleCode,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputCode.trim().isNotEmpty()) {
                        onSubmit(inputCode.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("تسجيل", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", fontFamily = CairoFontFamily)
            }
        }
    )
}

@Composable
private fun WhatsAppAttendanceDialog(
    data: WhatsAppAttendanceDialogData,
    onDismiss: () -> Unit,
    onSendWhatsApp: () -> Unit
) {
    val whatsAppGreen = Color(0xFF25D366)
    val hasPhone = data.parentPhone.filter { it.isDigit() }.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("whatsapp_attendance_dialog"),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = whatsAppGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "واتساب",
                            tint = whatsAppGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "تسجيل حضور بنجاح",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "إشعار ولي الأمر عبر واتساب",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            color = whatsAppGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card with Student Name & Time
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Student Name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = data.studentName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        // Time
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "الوقت: ${data.time}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        // Date
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "التاريخ: ${data.date}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // Parent Phone status
                if (hasPhone) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                whatsAppGreen.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = whatsAppGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "رقم واتساب ولي الأمر: ${data.parentPhone}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = whatsAppGreen,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFFF59E0B).copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "رقم واتساب ولي الأمر غير مسجل",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Text(
                    text = "هل ترغب في إرسال رسالة وصول إلى السنتر لولي الأمر الآن؟",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = CairoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSendWhatsApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = whatsAppGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("send_whatsapp_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "إرسال واتساب",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B7280),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dismiss_whatsapp_button")
            ) {
                Text(
                    text = "تجاهل",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    )
}

/**
 * Format Egyptian phone number into international format (20xxxxx)
 */
private fun formatEgyptianWhatsAppNumber(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    return when {
        digits.startsWith("0020") -> digits.substring(2)
        digits.startsWith("20") -> digits
        digits.startsWith("0") -> "20" + digits.substring(1)
        digits.length == 10 && digits.startsWith("1") -> "20$digits"
        else -> if (digits.startsWith("20")) digits else "20$digits"
    }
}

/**
 * Dispatches an ACTION_VIEW intent to open WhatsApp (or browser if not installed)
 */
private fun sendAttendanceWhatsAppMessage(
    context: Context,
    studentName: String,
    parentPhone: String,
    date: String,
    time: String,
    centerName: String
) {
    val cleanDigits = parentPhone.filter { it.isDigit() }
    if (cleanDigits.isBlank()) {
        Toast.makeText(context, "رقم واتساب ولي الأمر غير مسجل", Toast.LENGTH_SHORT).show()
        return
    }

    val formattedPhone = formatEgyptianWhatsAppNumber(cleanDigits)

    val cleanCenter = centerName.trim()
    val centerDisplay = when {
        cleanCenter.isBlank() -> "سنتر لوبيتال"
        cleanCenter.startsWith("سنتر") -> cleanCenter
        else -> "سنتر $cleanCenter"
    }

    val message = """
مرحباً،
نود إعلامكم بأن ابنكم/ابنتكم $studentName قد وصل إلى السنتر اليوم $date الساعة $time.

مع تحيات،
$centerDisplay
    """.trimIndent()

    val encodedMessage = try {
        URLEncoder.encode(message, "UTF-8")
    } catch (e: Exception) {
        URLEncoder.encode(message, StandardCharsets.UTF_8.name())
    }

    val url = "https://wa.me/$formattedPhone?text=$encodedMessage"
    val uri = Uri.parse(url)

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: Open in web browser
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "تعذر فتح واتساب أو المتصفح", Toast.LENGTH_SHORT).show()
        }
    }
}

