package com.example.ui.screens.payments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.database.AppDatabase
import com.example.data.model.Payment
import com.example.data.model.PaymentWithSubject
import com.example.data.repository.PaymentRepository
import com.example.data.session.SessionManager
import com.example.ui.components.rememberAppSettings
import com.example.ui.components.resolveThemeColor
import com.example.ui.theme.CairoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PaymentUiItem(
    val id: Long,
    val subjectName: String,
    val subjectColor: String,
    val month: String,
    val formattedMonth: String,
    val amount: Double,
    val status: String, // "paid", "unpaid", "late"
    val paidAt: String?
)

data class PaymentSummaryUi(
    val totalDue: Double = 0.0,
    val totalPaid: Double = 0.0,
    val totalRemaining: Double = 0.0
)

@Composable
fun ChildPaymentsScreen(
    onBack: () -> Unit,
    studentName: String = "",
    parentName: String = "",
    studentIdOverride: Long? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val paymentRepository = remember { PaymentRepository(database.paymentDao()) }
    val sessionManager = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberAppSettings()
    val primaryColor = resolveThemeColor(settings.themeColor)

    var isLoading by remember { mutableStateOf(true) }
    var resolvedStudentName by remember { mutableStateOf(studentName) }
    var resolvedStudentId by remember { mutableLongStateOf(studentIdOverride ?: -1L) }
    var payments by remember { mutableStateOf<List<PaymentUiItem>>(emptyList()) }
    var summary by remember { mutableStateOf(PaymentSummaryUi()) }
    var selectedFilter by remember { mutableStateOf("الكل") } // "الكل", "مدفوع", "غير مدفوع"
    var refreshTrigger by remember { mutableIntStateOf(0) }

    fun loadData() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                // 1. Resolve studentId
                var targetStudentId = studentIdOverride ?: sessionManager.studentId

                if (targetStudentId <= 0) {
                    val userId = sessionManager.userId
                    if (userId > 0) {
                        val userCur = database.openHelper.readableDatabase.query(
                            SimpleSQLiteQuery("SELECT studentId FROM users WHERE id = ? LIMIT 1", arrayOf(userId))
                        )
                        userCur.use {
                            if (it.moveToNext()) {
                                val sIdIdx = it.getColumnIndex("studentId")
                                if (sIdIdx >= 0 && !it.isNull(sIdIdx)) {
                                    targetStudentId = it.getLong(sIdIdx)
                                }
                            }
                        }
                    }
                }

                if (targetStudentId <= 0) {
                    val pPhone = sessionManager.phone
                    if (pPhone.isNotBlank()) {
                        val pCur = database.openHelper.readableDatabase.query(
                            SimpleSQLiteQuery("SELECT id, name FROM students WHERE parentPhone = ? OR phone = ? LIMIT 1", arrayOf(pPhone, pPhone))
                        )
                        pCur.use {
                            if (it.moveToNext()) {
                                targetStudentId = it.getLong(0)
                                val name = it.getString(1)
                                if (!name.isNullOrBlank()) resolvedStudentName = name
                            }
                        }
                    }
                }

                if (targetStudentId <= 0 && studentName.isNotBlank() && studentName != "الطالب المسجل") {
                    val sCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT id, name FROM students WHERE name LIKE ? LIMIT 1", arrayOf("%$studentName%"))
                    )
                    sCur.use {
                        if (it.moveToNext()) {
                            targetStudentId = it.getLong(0)
                            val name = it.getString(1)
                            if (!name.isNullOrBlank()) resolvedStudentName = name
                        }
                    }
                }

                if (targetStudentId <= 0) {
                    val fallbackCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT id, name FROM students LIMIT 1")
                    )
                    fallbackCur.use {
                        if (it.moveToNext()) {
                            targetStudentId = it.getLong(0)
                            val name = it.getString(1)
                            if (!name.isNullOrBlank()) resolvedStudentName = name
                        }
                    }
                }

                // Resolve real student name if placeholder
                if (targetStudentId > 0 && (resolvedStudentName.isBlank() || resolvedStudentName == "الطالب المسجل")) {
                    val nameCur = database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery("SELECT name FROM students WHERE id = ? LIMIT 1", arrayOf(targetStudentId))
                    )
                    nameCur.use {
                        if (it.moveToNext()) {
                            val name = it.getString(0)
                            if (!name.isNullOrBlank()) resolvedStudentName = name
                        }
                    }
                }

                // 2. Ensure initial demo payments exist for this student if payments count for this student is 0
                if (targetStudentId > 0) {
                    val countForStudent = database.paymentDao().getCountForStudent(targetStudentId)
                    if (countForStudent == 0) {
                        // Find first subjectId
                        var firstSubjId = 1L
                        val sCur = database.openHelper.readableDatabase.query(
                            SimpleSQLiteQuery("SELECT id FROM subjects LIMIT 1")
                        )
                        sCur.use { if (it.moveToNext()) firstSubjId = it.getLong(0) }

                        database.paymentDao().insertPayments(
                            listOf(
                                Payment(
                                    studentId = targetStudentId,
                                    subjectId = firstSubjId,
                                    amount = 300.0,
                                    month = "2026-09",
                                    status = "paid",
                                    paidAt = "2026-09-05 04:15 م",
                                    createdAt = "2026-09-01"
                                ),
                                Payment(
                                    studentId = targetStudentId,
                                    subjectId = firstSubjId,
                                    amount = 300.0,
                                    month = "2026-10",
                                    status = "paid",
                                    paidAt = "2026-10-02 05:20 م",
                                    createdAt = "2026-10-01"
                                ),
                                Payment(
                                    studentId = targetStudentId,
                                    subjectId = firstSubjId,
                                    amount = 300.0,
                                    month = "2026-11",
                                    status = "unpaid",
                                    paidAt = null,
                                    createdAt = "2026-11-01"
                                )
                            )
                        )
                    }
                }

                // 3. Query all payments for student
                val loadedList = mutableListOf<PaymentUiItem>()
                if (targetStudentId > 0) {
                    val rawPayments = paymentRepository.getPaymentsWithSubjectForStudentSync(targetStudentId)
                    rawPayments.forEach { p ->
                        loadedList.add(
                            PaymentUiItem(
                                id = p.id,
                                subjectName = p.subjectName.ifBlank { "اشتراك المادة" },
                                subjectColor = p.subjectColor.ifBlank { "#3B82F6" },
                                month = p.month,
                                formattedMonth = formatArabicMonth(p.month),
                                amount = p.amount,
                                status = p.status.lowercase(),
                                paidAt = p.paidAt
                            )
                        )
                    }
                }

                // 4. Calculate summary
                val totalDue = loadedList.sumOf { it.amount }
                val totalPaid = loadedList.filter { it.status == "paid" }.sumOf { it.amount }
                val totalRemaining = totalDue - totalPaid

                withContext(Dispatchers.Main) {
                    resolvedStudentId = targetStudentId
                    payments = loadedList
                    summary = PaymentSummaryUi(
                        totalDue = totalDue,
                        totalPaid = totalPaid,
                        totalRemaining = totalRemaining.coerceAtLeast(0.0)
                    )
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(studentIdOverride, studentName, refreshTrigger) {
        loadData()
    }

    val displayStudentName = if (resolvedStudentName.isNotBlank() && resolvedStudentName != "الطالب المسجل") {
        resolvedStudentName
    } else if (studentName.isNotBlank() && studentName != "الطالب المسجل") {
        studentName
    } else {
        "الطالب"
    }

    val filteredPayments = remember(payments, selectedFilter) {
        when (selectedFilter) {
            "مدفوع" -> payments.filter { it.status == "paid" }
            "غير مدفوع" -> payments.filter { it.status != "paid" }
            else -> payments
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("child_payments_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar: "اشتراكات [اسم الطالب]" + زر رجوع
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .size(42.dp)
                        .testTag("child_payments_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "اشتراكات $displayStudentName",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "سجل المصروفات والرسوم الشهرية",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CairoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                IconButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .size(42.dp)
                        .testTag("child_payments_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث",
                        tint = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "جاري تحميل سجل الاشتراكات...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                // Financial Summary Card: المستحق، المدفوع، المتبقي
                PaymentsSummaryCard(
                    summary = summary,
                    primaryColor = primaryColor,
                    modifier = Modifier.testTag("child_payments_summary_card")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Filter & Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "سجل الاشتراكات الشهرية",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${payments.size} اشتراكات",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "الكل" to payments.size,
                        "مدفوع" to payments.count { it.status == "paid" },
                        "غير مدفوع" to payments.count { it.status != "paid" }
                    )

                    filterOptions.forEach { (label, count) ->
                        val isSelected = selectedFilter == label
                        val chipColor = when (label) {
                            "مدفوع" -> Color(0xFF10B981)
                            "غير مدفوع" -> Color(0xFFEF4444)
                            else -> primaryColor
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) chipColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable { selectedFilter = label }
                                .testTag("payment_filter_$label")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (count > 0) {
                                    Text(
                                        text = "($count)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = CairoFontFamily,
                                            color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payments List or Empty State
                if (filteredPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("child_payments_empty_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    text = if (payments.isEmpty()) "لا توجد اشتراكات أو مدفوعات مسجلة" else "لا توجد نتائج مطابقة للفلتر",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = if (payments.isEmpty()) "لم يتم تسجيل أي مطالبات مالية أو اشتراكات شهرية لهذا الطالب حتى الآن" else "جرّب اختيار فلتر آخر لعرض باقي الاشتراكات",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = CairoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("child_payments_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(filteredPayments, key = { it.id }) { item ->
                            PaymentRecordCard(
                                payment = item,
                                modifier = Modifier.testTag("payment_item_${item.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsSummaryCard(
    summary: PaymentSummaryUi,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "الملخص المالي للاشتراكات",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "متابعة سداد الرسوم والمصروفات",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (summary.totalRemaining == 0.0 && summary.totalDue > 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (summary.totalRemaining == 0.0 && summary.totalDue > 0) "خالص السداد ✅" else "مطلوب سداد ⚠️",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.totalRemaining == 0.0 && summary.totalDue > 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Progress Bar of Payment
            val progressFraction = if (summary.totalDue > 0) (summary.totalPaid / summary.totalDue).toFloat().coerceIn(0f, 1f) else 1f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF10B981),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            // 3 Stat Tiles: إجمالي المستحق شهريًا / إجمالي المدفوع / المتبقي
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PaymentStatTile(
                    title = "إجمالي المستحق",
                    amount = formatCurrency(summary.totalDue),
                    color = primaryColor,
                    icon = Icons.Default.ReceiptLong,
                    modifier = Modifier.weight(1f)
                )

                PaymentStatTile(
                    title = "إجمالي المدفوع",
                    amount = formatCurrency(summary.totalPaid),
                    color = Color(0xFF10B981),
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )

                PaymentStatTile(
                    title = "المتبقي",
                    amount = formatCurrency(summary.totalRemaining),
                    color = if (summary.totalRemaining > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    icon = if (summary.totalRemaining > 0) Icons.Default.Cancel else Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PaymentStatTile(
    title: String,
    amount: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                ),
                maxLines = 1
            )

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PaymentRecordCard(
    payment: PaymentUiItem,
    modifier: Modifier = Modifier
) {
    val isPaid = payment.status == "paid"
    val isLate = payment.status == "late"

    val statusColor = when {
        isPaid -> Color(0xFF10B981) // أخضر: مدفوع ✅
        isLate -> Color(0xFFF59E0B) // أصفر: متأخر ⚠️
        else -> Color(0xFFEF4444)   // أحمر: غير مدفوع ❌
    }

    val statusBg = statusColor.copy(alpha = 0.12f)

    val statusText = when {
        isPaid -> "مدفوع ✅"
        isLate -> "متأخر ⚠️"
        else -> "غير مدفوع ❌"
    }

    val statusIcon = when {
        isPaid -> Icons.Default.CheckCircle
        isLate -> Icons.Default.WarningAmber
        else -> Icons.Default.Cancel
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            statusColor.copy(alpha = 0.28f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Subject Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = payment.subjectName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "شهر ${payment.formattedMonth}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Row 2: Amount & Paid Date Info
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "المبلغ المطلوب",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CairoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = formatCurrency(payment.amount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    if (isPaid && !payment.paidAt.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "تاريخ السداد",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = payment.paidAt,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "بانتظار السداد",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val rounded = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
    return "$rounded ج.م"
}

private fun formatArabicMonth(monthStr: String): String {
    return try {
        val parts = monthStr.split("-")
        if (parts.size == 2) {
            val year = parts[0]
            val m = parts[1].toInt()
            val monthName = when (m) {
                1 -> "يناير"
                2 -> "فبراير"
                3 -> "مارس"
                4 -> "أبريل"
                5 -> "مايو"
                6 -> "يونيو"
                7 -> "يوليو"
                8 -> "أغسطس"
                9 -> "سبتمبر"
                10 -> "أكتوبر"
                11 -> "نوفمبر"
                12 -> "ديسمبر"
                else -> monthStr
            }
            "$monthName $year"
        } else {
            monthStr
        }
    } catch (_: Exception) {
        monthStr
    }
}
