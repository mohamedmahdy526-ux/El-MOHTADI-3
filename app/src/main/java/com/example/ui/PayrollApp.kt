package com.example.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.provider.ContactsContract
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import com.example.data.Receipt
import com.example.data.Worker
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollApp(viewModel: PayrollViewModel) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsState()
    val workers by viewModel.allWorkers.collectAsState()
    val receipts by viewModel.allReceipts.collectAsState()
    val activeWorksheets by viewModel.activeWorksheets.collectAsState()

    val selectedDetailsWorker by viewModel.selectedWorkerForDetails.collectAsState()
    val selectedCalculatorWorker by viewModel.selectedWorkerForCalculator.collectAsState()
    val viewingReceipt by viewModel.viewingReceiptPdf.collectAsState()
    val isAddingNewWorker by viewModel.isAddingNewWorker.collectAsState()

    val overtimeRate by viewModel.overtimeRate.collectAsState()
    val companyName by viewModel.companyName.collectAsState()
    val siteName by viewModel.siteName.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    var showContactPicker by remember { mutableStateOf(false) }
    var showNextDayConfirmation by remember { mutableStateOf(false) }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
        if (isGranted) {
            showContactPicker = true
        } else {
            Toast.makeText(context, "يجب منح صلاحية الوصول لقراءة جهات الاتصال من إعدادات الهاتف", Toast.LENGTH_LONG).show()
        }
    }

    // Handle system back press beautifully
    BackHandler(
        enabled = selectedDetailsWorker != null || selectedCalculatorWorker != null || viewingReceipt != null || isAddingNewWorker
    ) {
        when {
            viewingReceipt != null -> viewModel.viewReceiptPdf(null)
            selectedCalculatorWorker != null -> viewModel.selectWorkerForCalculator(null)
            selectedDetailsWorker != null -> viewModel.selectWorkerForDetails(null)
            isAddingNewWorker -> viewModel.setAddingNewWorker(false)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg),
        bottomBar = {
            if (selectedCalculatorWorker == null && selectedDetailsWorker == null && viewingReceipt == null && !isAddingNewWorker) {
                ArabicBottomBar(activeTab = activeTab, onTabSelect = { viewModel.setActiveTab(it) })
            }
        },
        containerColor = CharcoalBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // PDF PREVIEW OVERLAY
                viewingReceipt != null -> {
                    val currentReceipt = viewingReceipt
                    if (currentReceipt != null) {
                        PdfPreviewScreen(
                            receipt = currentReceipt,
                            onBack = { viewModel.viewReceiptPdf(null) },
                            onShare = { viewModel.shareReceiptOnWhatsApp(context, currentReceipt) },
                            onDelete = {
                                viewModel.deleteReceipt(currentReceipt)
                                viewModel.viewReceiptPdf(null)
                            }
                        )
                    }
                }

                // CALCULATOR SHEET OVERLAY
                selectedCalculatorWorker != null -> {
                    val currentCalcWorker = selectedCalculatorWorker
                    if (currentCalcWorker != null) {
                        val initialWorksheet = activeWorksheets[currentCalcWorker.id] ?: com.example.data.ActiveWorksheet(currentCalcWorker.id)
                        
                        // Calculate accumulated unpaid balances of the worker
                        val workerReceipts = receipts.filter { it.workerId == currentCalcWorker.id }
                        val unpaidWorkedDays = workerReceipts.filter { !it.isPaid }.sumOf { it.workedDays }
                        val unpaidOvertime = workerReceipts.filter { !it.isPaid }.sumOf { it.overtimeHours }
                        val unpaidCommission = workerReceipts.filter { !it.isPaid }.sumOf { it.commission }
                        val unpaidAdvance = workerReceipts.filter { !it.isPaid }.sumOf { it.advance }

                        PayrollCalculatorScreen(
                            worker = currentCalcWorker,
                            initialWorksheet = initialWorksheet,
                            fixedOvertimeRate = overtimeRate,
                            unpaidDays = unpaidWorkedDays,
                            unpaidOvertime = unpaidOvertime,
                            unpaidCommission = unpaidCommission,
                            unpaidAdvance = unpaidAdvance,
                            onBack = { viewModel.selectWorkerForCalculator(null) },
                            onSaveDraft = { updatedWorksheet ->
                                viewModel.updateActiveWorksheet(updatedWorksheet)
                                viewModel.selectWorkerForCalculator(null)
                            },
                            onSettle = { finalWorker, finalWorksheet ->
                                viewModel.settleAndGenerateReceipt(finalWorker, finalWorksheet) {
                                    viewModel.selectWorkerForCalculator(null)
                                    viewModel.viewReceiptPdf(it)
                                }
                            }
                        )
                    }
                }

                // WORKER HISTORY / DETAILS SHEET OVERLAY
                selectedDetailsWorker != null -> {
                    val currentDetWorker = selectedDetailsWorker
                    if (currentDetWorker != null) {
                        val workerReceipts = receipts.filter { it.workerId == currentDetWorker.id }
                        WorkerDetailsScreen(
                            worker = currentDetWorker,
                            viewModel = viewModel,
                            history = workerReceipts,
                            onBack = { viewModel.selectWorkerForDetails(null) },
                            onViewReceipt = { viewModel.viewReceiptPdf(it) },
                            onDeleteWorker = {
                                viewModel.deleteWorker(currentDetWorker)
                                viewModel.selectWorkerForDetails(null)
                            }
                        )
                    }
                }

                // PRIMARY TABS SWITCHER
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title header
                        DashboardHeader(companyName = companyName, siteName = siteName)

                        when (activeTab) {
                            "workers" -> {
                                WorkersTabContent(
                                    workers = workers,
                                    activeWorksheets = activeWorksheets,
                                    overtimeRate = overtimeRate,
                                    onWorkerClick = { viewModel.selectWorkerForCalculator(it) },
                                    onWorkerDetails = { viewModel.selectWorkerForDetails(it) },
                                    onAddNewWorkerClick = { viewModel.setAddingNewWorker(true) },
                                    onPrepareNextDayClick = { showNextDayConfirmation = true }
                                )
                            }
                            "receipts" -> {
                                ReceiptsTabContent(
                                    receipts = receipts.filter { it.isPaid },
                                    onReceiptClick = { viewModel.viewReceiptPdf(it) },
                                    onReceiptShare = { viewModel.shareReceiptOnWhatsApp(context, it) }
                                )
                            }
                            "settings" -> {
                                SettingsTabContent(
                                    overtimeRate = overtimeRate,
                                    companyName = companyName,
                                    siteName = siteName,
                                    isDarkMode = isDarkMode,
                                    onUpdateDarkMode = { viewModel.updateDarkMode(it) },
                                    onUpdateRate = { viewModel.updateOvertimeRate(it) },
                                    onUpdateCompany = { viewModel.updateCompanyName(it) },
                                    onUpdateSite = { viewModel.updateSiteName(it) },
                                    onResetCalculations = {
                                        viewModel.setActiveTab("workers")
                                        viewModel.resetAllDailyCalculations()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ADD WORKER OVERLAY DIALOG
            if (isAddingNewWorker) {
                AddNewWorkerDialog(
                    onDismiss = { viewModel.setAddingNewWorker(false) },
                    onSave = { name, phone, dailySalary ->
                        viewModel.addWorker(name, phone, dailySalary)
                        viewModel.setAddingNewWorker(false)
                    },
                    onImportClick = {
                        if (hasContactsPermission) {
                            showContactPicker = true
                        } else {
                            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                        }
                    }
                )
            }

            if (showContactPicker) {
                ContactSelectionDialog(
                    onDismissRequest = { showContactPicker = false },
                    onImportContacts = { selected ->
                        selected.forEach { contact ->
                            viewModel.addWorker(contact.name, contact.phone, 300.0)
                        }
                        showContactPicker = false
                        viewModel.setAddingNewWorker(false)
                    }
                )
            }

            if (showNextDayConfirmation) {
                var isProcessingNextDay by remember { mutableStateOf(false) }

                Dialog(onDismissRequest = { if (!isProcessingNextDay) showNextDayConfirmation = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "ترحيل كشوف عمال اليوم وتحضير الغد",
                                color = TextWhite,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "سيتم إجراء الخطوات التالية تلقائياً لجميع مسجلي الموقع:\n\n• حفظ وتوثيق جميع يوميات وساعات الإضافي والسلف والعمولات الحالية في ملف كل عامل وتاريخ اليوم.\n• إعادة ضبط وتحضير كشوف الغد بحالة افتراضية (حاضر مع صفر إضافي وسلف).\n\nهل تود تأكيد حفظ وترحيل اليوم وسد السجلات تلقائياً؟",
                                color = TextGray,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isProcessingNextDay = true
                                        viewModel.autoSaveCurrentDayAndPrepareNextDay {
                                            isProcessingNextDay = false
                                            showNextDayConfirmation = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrightGreen),
                                    enabled = !isProcessingNextDay
                                ) {
                                    if (isProcessingNextDay) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("تأكيد وحفظ تلقائي", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showNextDayConfirmation = false },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, SmoothBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                    enabled = !isProcessingNextDay
                                ) {
                                    Text("إلغاء")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- USER INTERFACES COMPONENTS ----------------

@Composable
fun DashboardHeader(companyName: String, siteName: String) {
    val dateStr = remember {
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        sdf.format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(LightGreenBox)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = siteName,
                    color = BrightGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = companyName,
                color = TextWhite,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = dateStr,
                color = TextGray,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "التاريخ",
                tint = BrightGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ArabicBottomBar(activeTab: String, onTabSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = CardSurface,
        border = BorderStroke(1.dp, SmoothBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                label = "الإعدادات",
                icon = Icons.Default.Settings,
                selected = activeTab == "settings",
                onClick = { onTabSelect("settings") },
                testTag = "tab_settings"
            )
            BottomBarItem(
                label = "الرسيتات",
                icon = Icons.Default.Receipt,
                selected = activeTab == "receipts",
                onClick = { onTabSelect("receipts") },
                testTag = "tab_receipts"
            )
            BottomBarItem(
                label = "العمال",
                icon = Icons.Default.People,
                selected = activeTab == "workers",
                onClick = { onTabSelect("workers") },
                testTag = "tab_workers"
            )
        }
    }
}

@Composable
fun BottomBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(min = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) TransparentGreen else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) BrightGreen else TextGray,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) BrightGreen else TextGray,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ---------------- WORKERS LIST SCREEN ----------------

@Composable
fun WorkersTabContent(
    workers: List<Worker>,
    activeWorksheets: Map<Int, com.example.data.ActiveWorksheet>,
    overtimeRate: Double,
    onWorkerClick: (Worker) -> Unit,
    onWorkerDetails: (Worker) -> Unit,
    onAddNewWorkerClick: () -> Unit,
    onPrepareNextDayClick: () -> Unit
) {
    // Calculators total analytics of day on-the-fly (deducts already settled workers)
    val totalActivePay = workers.sumOf { w ->
        val worksheet = activeWorksheets[w.id] ?: com.example.data.ActiveWorksheet(w.id)
        if (worksheet.isSettled) {
            0.0
        } else {
            val dailyBase = if (worksheet.isPresent) w.dailySalary else 0.0
            val overtimeVal = worksheet.overtimeHours * overtimeRate
            dailyBase + overtimeVal + worksheet.commission - worksheet.advance
        }
    }
    val presentCount = workers.count { w ->
        val worksheet = activeWorksheets[w.id] ?: com.example.data.ActiveWorksheet(w.id)
        worksheet.isPresent
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick high-level summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "إجمالي الصافي اليومي",
                value = "${String.format(Locale("en"), "%,.0f", totalActivePay)} ج.م",
                subtitle = "باقي الصرف المستحق",
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.weight(1.3f)
            )
            SummaryCard(
                title = "حضور عمال اليوم",
                value = "$presentCount / ${workers.size}",
                subtitle = "عامل حاضر حالياً",
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dual-action row for adding worker and preparing the next day
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Action 1: Add New Worker
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_worker_card_trigger")
                    .clickable(onClick = onAddNewWorkerClick),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BrightGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إضافة عامل",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة عامل",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Action 2: Save Today & Prepare Next Day
            Card(
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("prepare_next_day_trigger")
                    .clickable(onClick = onPrepareNextDayClick),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, BrightGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PremiumCardGradient)
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تحضير اليوم التالي",
                        color = BrightGreen,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.NextPlan,
                        contentDescription = "تحضير اليوم التالي",
                        tint = BrightGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "قائمة عمال اليوم",
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            textAlign = TextAlign.Right
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (workers.isEmpty()) {
            EmptyListPlaceholder(
                title = "لا يوجد عمال مضافين حالياً",
                subtitle = "اضغط على إضافة عامل جديد بالمنصة لتبدأ تسجيل اليوميات والحسابات!"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(workers, key = { it.id }) { worker ->
                    val worksheet = activeWorksheets[worker.id] ?: com.example.data.ActiveWorksheet(worker.id)
                    WorkerListItem(
                        worker = worker,
                        worksheet = worksheet,
                        overtimeRate = overtimeRate,
                        onClick = { onWorkerClick(worker) },
                        onDetails = { onWorkerDetails(worker) }
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, SmoothBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PremiumCardGradient)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrightGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    color = TextGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = BrightGreen,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun WorkerListItem(
    worker: Worker,
    worksheet: com.example.data.ActiveWorksheet,
    overtimeRate: Double,
    onClick: () -> Unit,
    onDetails: () -> Unit
) {
    // Current calculation totals
    val dailyBase = if (worksheet.isPresent) worker.dailySalary else 0.0
    val overtimeVal = worksheet.overtimeHours * overtimeRate
    val totalNet = dailyBase + overtimeVal + worksheet.commission - worksheet.advance

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("worker_item_${worker.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, SmoothBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PremiumCardGradient)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Net Amount visually dominating (Left)
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (worksheet.isSettled) "تم الصرف ✓" else "${String.format(Locale("en"), "%.0f", totalNet)} ج.م",
                    color = if (worksheet.isSettled) PresentColor else BrightGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (worksheet.isSettled) "تمت التسوية بنجاح" else "الصافي المستحق",
                    color = TextGray,
                    fontSize = 10.sp
                )
            }

            // Info and controls (Right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                // Calculation middle parameters
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (worker.phone.isNotBlank()) {
                            Text(
                                  text = worker.phone,
                                  color = TextGray,
                                  style = MaterialTheme.typography.labelSmall,
                                  modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Text(
                            text = "👷 ${worker.name}",
                            color = TextWhite,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "سلفة ${worksheet.advance.toInt()}",
                            color = TextGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        DividerBullet()
                        Text(
                            text = "عمولة ${worksheet.commission.toInt()}",
                            color = TextGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        DividerBullet()
                        Text(
                            text = "${worksheet.overtimeHours.toInt()} س إضافي",
                            color = TextGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        DividerBullet()
                        Text(
                            text = if (worksheet.isSettled) "تم الصرف" else if (worksheet.isPresent) "حاضر" else "غائب",
                            color = if (worksheet.isSettled) BrightGreen else if (worksheet.isPresent) PresentColor else AbsentColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Info action buttons to trigger histories details
                IconButton(
                    onClick = onDetails,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SmoothBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "التفاصيل التاريخية",
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DividerBullet() {
    Text(
        text = " • ",
        color = SmoothBorder,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

// ---------------- WORKER PAYROLL CALCULATOR SCREEN ----------------

@Composable
fun PayrollCalculatorScreen(
    worker: Worker,
    initialWorksheet: com.example.data.ActiveWorksheet,
    fixedOvertimeRate: Double,
    unpaidDays: Double,
    unpaidOvertime: Double,
    unpaidCommission: Double,
    unpaidAdvance: Double,
    onBack: () -> Unit,
    onSaveDraft: (com.example.data.ActiveWorksheet) -> Unit,
    onSettle: (Worker, com.example.data.ActiveWorksheet) -> Unit
) {
    // Dynamic calculation states prefilled from unpaid balances if available
    val initialDays = if (unpaidDays > 0.0) unpaidDays else if (initialWorksheet.activeWorkedDays == 0.0) 1.0 else initialWorksheet.activeWorkedDays
    val initialOvertime = if (unpaidOvertime > 0.0) unpaidOvertime else initialWorksheet.overtimeHours
    val initialCommission = if (unpaidCommission > 0.0) unpaidCommission else initialWorksheet.commission
    val initialAdvance = if (unpaidAdvance > 0.0) unpaidAdvance else initialWorksheet.advance

    var workedDaysStr by remember { mutableStateOf(if (initialDays % 1.0 == 0.0) initialDays.toInt().toString() else initialDays.toString()) }
    var dailySalaryStr by remember { mutableStateOf(worker.dailySalary.toInt().toString()) }
    var overtimeHoursStr by remember { mutableStateOf(if (initialOvertime % 1.0 == 0.0) initialOvertime.toInt().toString() else initialOvertime.toString()) }
    val isPresentInitial = initialWorksheet.isPresent
    var commissionStr by remember { mutableStateOf(if (initialCommission % 1.0 == 0.0) initialCommission.toInt().toString() else initialCommission.toString()) }
    var advanceStr by remember { mutableStateOf(if (initialAdvance % 1.0 == 0.0) initialAdvance.toInt().toString() else initialAdvance.toString()) }
    var isPresentState by remember { mutableStateOf(isPresentInitial) }

    // Helpers to compute live outputs
    val workedDaysVal = workedDaysStr.toDoubleOrNull() ?: 1.0
    val dailyWageVal = dailySalaryStr.toDoubleOrNull() ?: 0.0
    val overtimeHrsVal = overtimeHoursStr.toDoubleOrNull() ?: 0.0
    val commissionVal = commissionStr.toDoubleOrNull() ?: 0.0
    val advanceVal = advanceStr.toDoubleOrNull() ?: 0.0

    val liveOvertimeAmount = overtimeHrsVal * fixedOvertimeRate
    val liveBaseWageApplied = if (isPresentState) (workedDaysVal * dailyWageVal) else 0.0
    val liveNetAmount = liveBaseWageApplied + liveOvertimeAmount + commissionVal - advanceVal

    val textDirectionModifier = Modifier.fillMaxWidth()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .verticalScroll(rememberScrollState())
    ) {
        // Simple elegant top action bar
        ScreenHeader(
            title = "حساب مستحقات: ${worker.name}",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Worker detail mini headers
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, SmoothBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isPresentState) TransparentGreen else Color(0x1AFF5252))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isPresentState) "حاضر بالموقع" else "غائب اليوم",
                            color = if (isPresentState) PresentColor else AbsentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "هاتف: ${if (worker.phone.isNotBlank()) worker.phone else "غير مسجل"}",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Unpaid Balance Alert Banner
            if (unpaidDays > 0.0 || unpaidOvertime > 0.0 || unpaidCommission > 0.0 || unpaidAdvance > 0.0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2513)),
                    border = BorderStroke(1.dp, Color(0xFF6C571C))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "💡 مستحقات غير مصفاة لهذا العامل",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "الأيام غير المصروفة: ${unpaidDays.toInt()} يوم | الإضافي: ${unpaidOvertime.toInt()} س | حوافز: ${unpaidCommission.toInt()} ج.م | سلف معلقة: ${unpaidAdvance.toInt()} ج.م",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تم ملء الحقول تلقائياً بكامل الرصيد المتراكم. يمكنك تعديل الحقول لتصفية وصرف جزء من المدة (مثال: صرف 7 أيام فقط من أصل ${unpaidDays.toInt()}).",
                            color = Color(0xFFB0BEC5),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Attendance Toggles (Arabic styled)
            Text(
                text = "حالة الحضور والعمل اليوم",
                color = TextWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isPresentState = false },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isPresentState) AbsentColor else CardSurface,
                        contentColor = if (!isPresentState) Color.White else TextGray
                    ),
                    border = if (isPresentState) BorderStroke(1.dp, SmoothBorder) else null
                ) {
                    Text("غائب اليوم", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { isPresentState = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPresentState) PresentColor else CardSurface,
                        contentColor = if (isPresentState) Color.White else TextGray
                    ),
                    border = if (!isPresentState) BorderStroke(1.dp, SmoothBorder) else null
                ) {
                    Text("حاضر بالموقع", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calculator Parameters inputs
            Text(
                text = "بيانات تسعير البنود (ج.م / ساعة)",
                color = TextWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(10.dp))

            StyledInputField(
                label = "عدد يوميات العمل المستحقة (يوم)",
                value = workedDaysStr,
                onValueChange = { workedDaysStr = it },
                icon = Icons.Default.DateRange
            )

            Spacer(modifier = Modifier.height(10.dp))

            StyledInputField(
                label = "سعر اليومية الأساسي (ج.م)",
                value = dailySalaryStr,
                onValueChange = { dailySalaryStr = it },
                icon = Icons.Default.AttachMoney
            )

            Spacer(modifier = Modifier.height(10.dp))

            StyledInputField(
                label = "ساعات العمل الإضافية اليوم (ساعة)",
                value = overtimeHoursStr,
                onValueChange = { overtimeHoursStr = it },
                icon = Icons.Default.Schedule
            )

            Spacer(modifier = Modifier.height(10.dp))

            StyledInputField(
                label = "مكافآت وحوافز إضافية (عمولة ج.م)",
                value = commissionStr,
                onValueChange = { commissionStr = it },
                icon = Icons.Default.AddCircle
            )

            Spacer(modifier = Modifier.height(10.dp))

            StyledInputField(
                label = "مستقطعات مالية سابقة (سلفة مدفوعة ج.م)",
                value = advanceStr,
                onValueChange = { advanceStr = it },
                icon = Icons.Default.RemoveCircle
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Net breakdown Calculation Visual Board
            Text(
                text = "طريقة وتفاصيل الحساب المالي",
                color = TextWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = LightGreenBox,
                border = BorderStroke(1.dp, Color(0xFF1B3D2B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${liveBaseWageApplied.toInt()} ج.م",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أجر اليوميات الأساسية (${workedDaysVal} يوم × ${dailyWageVal.toInt()} ج.م):",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${liveOvertimeAmount.toInt()} ج.م",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "حساب العمل الإضافي (${overtimeHrsVal.toInt()} ساعة × $fixedOvertimeRate):",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${commissionVal.toInt()} ج.م",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "العمولات المستحقة للعمال:",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "-${advanceVal.toInt()} ج.م",
                            color = AbsentColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "إجمالي السلفة المستقطعة:",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF1B3D2B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${liveNetAmount.toInt()} ج.م",
                            color = BrightGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "الصافي النهائي المستحق:",
                            color = TextWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Payout Settlers
            val updatedWorker = worker.copy(dailySalary = dailyWageVal)
            val updatedWorksheet = com.example.data.ActiveWorksheet(
                workerId = worker.id,
                activeWorkedDays = workedDaysVal,
                overtimeHours = overtimeHrsVal,
                commission = commissionVal,
                advance = advanceVal,
                isPresent = isPresentState
            )

            Button(
                onClick = { onSettle(updatedWorker, updatedWorksheet) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_and_print_receipt_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrightGreen)
            ) {
                Text(
                    text = "صرف المستحقات وإنشاء إيصال دفع",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { onSaveDraft(updatedWorksheet) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_worker_draft_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SmoothBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
            ) {
                Text(
                    text = "حفظ كمسودة حساب بالموقع",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun StyledInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag("input_" + label.take(5)),
        label = {
            Text(
                text = label,
                color = TextGray,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrightGreen,
                modifier = Modifier.size(18.dp)
            )
        },
        singleLine = true,
        textStyle = TextStyle(
            color = TextWhite,
            textAlign = TextAlign.Right,
            fontSize = 16.sp
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CardSurface,
            unfocusedContainerColor = CardSurface,
            disabledContainerColor = CardSurface,
            focusedIndicatorColor = BrightGreen,
            unfocusedIndicatorColor = SmoothBorder,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

// ---------------- WORKER DETAILS & ACCOUNT STATEMENT HISTORY ----------------

@Composable
fun WorkerDetailsScreen(
    worker: Worker,
    viewModel: PayrollViewModel,
    history: List<Receipt>,
    onBack: () -> Unit,
    onViewReceipt: (Receipt) -> Unit,
    onDeleteWorker: () -> Unit
) {
    val stats by viewModel.selectedWorkerStats.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
    ) {
        ScreenHeader(
            title = "كشف حساب: ${worker.name}",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Info board card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, SmoothBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "👷 سجل مبيعات وتفاصيل العمل",
                            color = BrightGreen,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val overtimeRate by viewModel.overtimeRate.collectAsState()
                        val unpaidNet = (stats.workedDaysCount * worker.dailySalary) + (stats.totalOvertimeHours * overtimeRate) + stats.totalCommission - stats.totalAdvance

                        DetailBlockRow(label = "الأجر الأساسي باليومية", value = "${worker.dailySalary.toInt()} ج.م")
                        DetailBlockRow(label = "أيام عمـل حضور جديدة (لم تصرف)", value = String.format(Locale("en"), "%.1f يوم", stats.workedDaysCount))
                        DetailBlockRow(label = "ساعات عمل إضافية تراكمية معلقة", value = "${stats.totalOvertimeHours.toInt()} ساعة")
                        DetailBlockRow(label = "إجمالي عمولات معلقة مستحقة", value = "${stats.totalCommission.toInt()} ج.م")
                        DetailBlockRow(label = "خصومات أو سُلف حالية معلقة", value = "${stats.totalAdvance.toInt()} ج.م")
                        
                        // Show Unpaid Accumulated Net
                        DetailBlockRow(
                            label = "المستحقات المتراكمة (غير المصفاة اليوم)",
                            value = "${String.format(Locale("en"), "%,.0f", unpaidNet)} ج.م",
                            highlight = false
                        )

                        HorizontalDivider(color = SmoothBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                        
                        DetailBlockRow(
                            label = "إجمالي المبالغ المصروفة والمسواة",
                            value = "${String.format(Locale("en"), "%,.0f", stats.totalPaid)} ج.م",
                            highlight = true
                        )
                    }
                }
            }

            // Quick danger controls to clear / archive workers
            item {
                if (!showDeleteConfirm) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("delete_worker_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AbsentColor),
                        border = BorderStroke(1.dp, Color(0x3BFF5252)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = AbsentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف وإنهاء ملف هذا العامل", fontWeight = FontWeight.Bold, color = AbsentColor)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1616)),
                        border = BorderStroke(1.dp, Color(0xFF6B1E1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("هل أنت متأكد من رغبتك بحذف ملف العامل وتصفية حسابه نهائياً بالشركة؟", color = TextWhite, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Right)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                    border = BorderStroke(1.dp, SmoothBorder),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("تراجع")
                                }
                                Button(
                                    onClick = onDeleteWorker,
                                    modifier = Modifier.weight(1.3f).testTag("confirm_delete_worker"),
                                    colors = ButtonDefaults.buttonColors(containerColor = AbsentColor),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("نعم، احذف نهائياً", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Receipt history header
            item {
                Text(
                    text = "سجل إيصالات الصرف والمسودات",
                    color = TextWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }

            if (history.isEmpty()) {
                item {
                    EmptyListPlaceholder(
                        title = "لا توجد إيصالات ودفوعات سابقة",
                        subtitle = "عند قيامك بصرف رصيد العامل، ستظهر الأوراق هنا بالتاريخ الموثق!"
                    )
                }
            } else {
                items(history) { receipt ->
                    ReceiptListItem(receipt = receipt, onClick = { onViewReceipt(receipt) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun DetailBlockRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            color = if (highlight) BrightGreen else TextWhite,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Medium
        )
        Text(
            text = label,
            color = if (highlight) TextWhite else TextGray,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------------- BILLS & RECEIPTS TAB CONTENT ----------------

@Composable
fun ReceiptsTabContent(
    receipts: List<Receipt>,
    onReceiptClick: (Receipt) -> Unit,
    onReceiptShare: (Receipt) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "إيصالات الصرف الموثقة والمطبوعة",
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            textAlign = TextAlign.Right
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (receipts.isEmpty()) {
            EmptyListPlaceholder(
                title = "سجل الإيصالات فارغ",
                subtitle = "قم بصرف المستحقات من تبويب العمال ليتم إصدار أوراق كاش إلكترونية ملونة!"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(receipts) { receipt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_item_${receipt.id}")
                            .clickable { onReceiptClick(receipt) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, SmoothBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PremiumCardGradient)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side - Net Amount & Action sharing
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                IconButton(
                                    onClick = { onReceiptShare(receipt) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TransparentGreen)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "مشاركة",
                                        tint = BrightGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "${receipt.netAmount.toInt()} ج.م",
                                        color = BrightGreen,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "الصافي المستلم",
                                        color = TextGray,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Right side - Worker name & Date
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = receipt.workerName,
                                    color = TextWhite,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val dtStr = remember(receipt.date) {
                                    val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
                                    sdf.format(Date(receipt.date))
                                }
                                Text(
                                    text = "الرقم: ${receipt.receiptNumber} • $dtStr",
                                    color = TextGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptListItem(receipt: Receipt, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, if (receipt.isPaid) Color(0xFF1B3D2B) else SmoothBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PremiumCardGradient)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Net Amount showing with status badge
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "${receipt.netAmount.toInt()} ج.م",
                    color = if (receipt.isPaid) BrightGreen else TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (receipt.isPaid) TransparentGreen else Color(0x1AEEFF41))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (receipt.isPaid) "إيصال صرف نقدي ✓" else "يومية عمل (غير مصروفة)",
                        color = if (receipt.isPaid) PresentColor else Color(0xFFEEFF41),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Right side - ID and Date
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (receipt.isPaid) "إيصال دفعة: ${receipt.receiptNumber}" else "يومية: ${receipt.receiptNumber}",
                    color = TextWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val str = remember(receipt.date) {
                    SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date(receipt.date))
                }
                Text(text = str, color = TextGray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---------------- PREMIUM PDF PRINT PREVIEW SCREEN ----------------

@Composable
fun PdfPreviewScreen(
    receipt: Receipt,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
    ) {
        ScreenHeader(
            title = "معاينة إيصال الدفع والطباعة",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant replica card styled exactly as the generated PDF layout (RTL Arabic)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(2.dp, Color.LightGray)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Double margin accent line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray)
                            .padding(15.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "المهتدي للمقاولات",
                                color = Color(0xFF007A3E),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "إدارة وتنفيذ أعمال المقاولات العامة والتشطيبات",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF007A3E), thickness = 2.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "إيصال استلام نقدية للعمال",
                                    color = Color(0xFF004D40),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الرقم: ${receipt.receiptNumber}",
                                    color = Color.Black,
                                    fontSize = 11.sp
                                )
                                val dt = remember {
                                    SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(receipt.date))
                                }
                                Text(
                                    text = "التاريخ: $dt",
                                    color = Color.Black,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Customer Info
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFAFAFA))
                                    .border(1.dp, Color(0xFFEEEEEE))
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                    Text(text = "اسم العامل: ${receipt.workerName}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "الهاتف: ${if (receipt.workerPhone.isNotBlank()) receipt.workerPhone else "لا يوجد"}", color = Color.DarkGray, fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Grid Items
                            PdfReplicaRow(label = "البند التفصيلي", value = "المبلغ (ج.م)", header = true)
                            PdfReplicaRow(label = "عدد يوميات العمل المعتمدة", value = String.format(Locale("en"), "%.1f يوم", if (receipt.isPresent) receipt.workedDays else 0.0))
                            PdfReplicaRow(label = "أجر اليومية الأساسي المتفق عليه", value = String.format(Locale("en"), "%.2f ج.م", receipt.dailySalary))
                            PdfReplicaRow(label = "إجمالي مستحقات اليوميات الأساسية", value = String.format(Locale("en"), "%.2f", if (receipt.isPresent) (receipt.workedDays * receipt.dailySalary) else 0.0))
                            PdfReplicaRow(label = "ساعات الإضافي (${receipt.overtimeHours.toInt()} ساعة × ${receipt.overtimeRate})", value = String.format(Locale("en"), "%.2f", receipt.overtimeAmount))
                            PdfReplicaRow(label = "عمولة الموقع اليومية", value = String.format(Locale("en"), "%.2f", receipt.commission))
                            PdfReplicaRow(label = "السلفة المخصومة من الحساب", value = String.format(Locale("en"), "-%.2f", receipt.advance))

                            Spacer(modifier = Modifier.height(10.dp))

                            // Total Highlight
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF007A3E))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${String.format(Locale("en"), "%,.2f", receipt.netAmount)} ج.م",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "الصافي النهائي المستحق:",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Fake signatures & Royal Blue Stamp Circle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "توقيع المستلم للراتب", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    HorizontalDivider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.width(80.dp))
                                }

                                // Interactive paid token seal on the replica center
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .border(1.5.dp, Color(0xFF0D47A1), CircleShape)
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(0.5.dp, Color(0xFF0D47A1), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "المهتدي\nدفع كاش",
                                                color = Color(0xFF0D47A1),
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 9.sp
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "توقيع المحاسب والمراجع", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    HorizontalDivider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.width(80.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Delete receipt audit
            if (!showDelConfirm) {
                OutlinedButton(
                    onClick = { showDelConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(44.dp)
                        .testTag("delete_receipt_button"),
                    border = BorderStroke(1.dp, Color(0x3BFF5252)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AbsentColor)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = AbsentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حذف وإلغاء مستند الإيصال", color = AbsentColor)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1616)),
                    border = BorderStroke(1.dp, Color(0xFF6B1E1E)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { showDelConfirm = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "تراجع", tint = TextWhite)
                        }
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = AbsentColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("confirm_delete_receipt")
                        ) {
                            Text("نعم، احذف المعاملة", color = Color.White, fontSize = 11.sp)
                        }
                        Text("متأكد من الحذف؟", color = TextWhite, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        // Shared controls at page footers
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardSurface,
            border = BorderStroke(1.dp, SmoothBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_whatsapp_large_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrightGreen)
                ) {
                    Text("مشاركة ملف PDF عبر واتساب", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun PdfReplicaRow(label: String, value: String, header: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color.LightGray)
            .background(if (header) Color(0xFFEEEEEE) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            color = if (header) Color.Black else if (value.startsWith("-")) Color.Red else Color.Black,
            fontSize = 11.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            text = label,
            color = if (header) Color.Black else Color.DarkGray,
            fontSize = 11.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ---------------- SETTINGS CONTROLS TAB ----------------

@Composable
fun SettingsTabContent(
    overtimeRate: Double,
    companyName: String,
    siteName: String,
    isDarkMode: Boolean,
    onUpdateDarkMode: (Boolean) -> Unit,
    onUpdateRate: (Double) -> Unit,
    onUpdateCompany: (String) -> Unit,
    onUpdateSite: (String) -> Unit,
    onResetCalculations: () -> Unit
) {
    var rateStr by remember { mutableStateOf(overtimeRate.toInt().toString()) }
    var companyStr by remember { mutableStateOf(companyName) }
    var siteStr by remember { mutableStateOf(siteName) }

    var showResetConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "إعدادات منصة اليومية والموقع",
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic theme control switch card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, SmoothBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onUpdateDarkMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrightGreen,
                        checkedTrackColor = SoftGreenAccent.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("theme_mode_switch")
                )
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مظهر التطبيق (فاتح / داكن)",
                        color = TextWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = if (isDarkMode) "الوضع الداكن الفخم مفعّل" else "الوضع الفاتح المريح مفعّل",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Fixed Overtime hourly rate inputs
        SettingsInputField(
            label = "سعر ساعة العمل الإضافية الموحد (ج.م/ساعة)",
            value = rateStr,
            onValueChange = {
                rateStr = it
                it.toDoubleOrNull()?.let { d -> onUpdateRate(d) }
            },
            keyboardType = KeyboardType.Number
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Company customized brand name
        SettingsInputField(
            label = "اسم المقاول / جهة التشغيل (الشركة)",
            value = companyStr,
            onValueChange = {
                companyStr = it
                onUpdateCompany(it)
            },
            keyboardType = KeyboardType.Text
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Branch main working site details
        SettingsInputField(
            label = "اسم موقع العمل الحالي للعمال",
            value = siteStr,
            onValueChange = {
                siteStr = it
                onUpdateSite(it)
            },
            keyboardType = KeyboardType.Text
        )

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider(color = SmoothBorder, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // ADVANCED UTILITIES: Start new day action
        Text(
            text = "أدوات تصفية كشوفات الموقع",
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "اضغط للتصفية السريعة والبدء بيوم عمل مالي جديد مريح في غضون ثانية واحدة.",
            color = TextGray,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (!showResetConfirmation) {
            Button(
                onClick = { showResetConfirmation = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_all_workers_daily_pills"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x23EF5350)),
                border = BorderStroke(1.dp, Color(0xFF6B1E1E)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "إعادة ضبط لتسجيل يومية جديدة بالموقع 🆕",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1616)),
                border = BorderStroke(1.dp, Color(0xFF6B1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "تنبيه هام جداً ⚠️",
                        color = AbsentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "هذا الإجراء سيقوم بتصفير جميع حقول الساعات، العمولات، والسلف المؤقتة لكافة العمال لبدء يوم جديد ببيانات فارغة. هل تريد المتابعة؟",
                        color = TextWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showResetConfirmation = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, SmoothBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("تراجع")
                        }
                        Button(
                            onClick = {
                                showResetConfirmation = false
                                onResetCalculations()
                            },
                            modifier = Modifier.weight(1.3f).testTag("confirm_reset_all_daily_calculations"),
                            colors = ButtonDefaults.buttonColors(containerColor = AbsentColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("تأكيد، صفر الحسابات", color = Color.White)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun SettingsInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = label,
            color = TextWhite,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            textStyle = TextStyle(
                color = TextWhite,
                textAlign = TextAlign.Right,
                fontSize = 15.sp
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedIndicatorColor = BrightGreen,
                unfocusedIndicatorColor = SmoothBorder,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

// ---------------- GENERAL DIALOGS & OVERLAY SCREENS ----------------

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(48.dp)) // Equalizer back size balance
        
        Text(
            text = title,
            color = TextWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CardSurface)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "الرجوع لكشف الحساب",
                tint = TextWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun EmptyListPlaceholder(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = null,
            tint = SmoothBorder,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = TextWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = TextGray,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun AddNewWorkerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit,
    onImportClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dialSalary by remember { mutableStateOf("300") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, SmoothBorder),
            modifier = Modifier.fillMaxWidth().testTag("add_worker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "تسجيل ملف عامل جديد 👷",
                    color = TextWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onImportClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("import_contacts_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("استيراد من جهات الاتصال 📱", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                StyledInputField(label = "اسم العامل كامل", value = name, onValueChange = { name = it }, icon = Icons.Default.Person, keyboardType = KeyboardType.Text)
                Spacer(modifier = Modifier.height(10.dp))
                StyledInputField(label = "رقم الهاتف لشSharing", value = phone, onValueChange = { phone = it }, icon = Icons.Default.Phone, keyboardType = KeyboardType.Phone)
                Spacer(modifier = Modifier.height(10.dp))
                StyledInputField(label = "سعر اليومية الافتراضي (ج.م)", value = dialSalary, onValueChange = { dialSalary = it }, icon = Icons.Default.AttachMoney, keyboardType = KeyboardType.Number)

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, SmoothBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("إلغاء المعاملة")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val sValue = dialSalary.toDoubleOrNull() ?: 300.0
                                onSave(name, phone, sValue)
                            }
                        },
                        modifier = Modifier.weight(1.3f).testTag("save_dialog_worker_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrightGreen),
                        shape = RoundedCornerShape(8.dp),
                        enabled = name.isNotBlank()
                    ) {
                        Text("حفظ وتسجيل العامل", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- DEVICE CONTACTS SELECTOR ----------------

data class ContactItem(
    val name: String,
    val phone: String,
    val isSelected: Boolean = false
)

fun fetchDeviceContacts(context: Context): List<ContactItem> {
    val contacts = mutableListOf<ContactItem>()
    try {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) return emptyList()
        
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameIdx != -1) it.getString(nameIdx) else ""
                val phone = if (phoneIdx != -1) it.getString(phoneIdx) else ""
                if (name.isNotBlank()) {
                    contacts.add(ContactItem(name, phone))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return contacts.distinctBy { it.name.lowercase() + "_" + it.phone.replace(" ", "") }
}

@Composable
fun ContactSelectionDialog(
    onDismissRequest: () -> Unit,
    onImportContacts: (List<ContactItem>) -> Unit
) {
    val context = LocalContext.current
    var contactsList by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val hasPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                fetchDeviceContacts(context)
            }
            contactsList = loaded
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, SmoothBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("contact_selection_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "استيراد عمال من جهات الاتصال 📱",
                    color = TextWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم جهة الاتصال...", color = TextGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contacts_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CharcoalBg,
                        unfocusedContainerColor = CharcoalBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true,
                    textStyle = TextStyle(textAlign = TextAlign.Right)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val filteredContacts = remember(searchQuery, contactsList) {
                    if (searchQuery.isBlank()) {
                        contactsList
                    } else {
                        contactsList.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.phone.contains(searchQuery)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = BrightGreen)
                    } else if (filteredContacts.isEmpty()) {
                        Text(
                            text = if (hasPermission) "لا توجد جهات اتصال مطابقة للبحث" else "جاري تحميل قائمة جهات الاتصال...",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredContacts) { contact ->
                                val isSelected = contact.isSelected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0x1F00E676) else Color.Transparent)
                                        .clickable {
                                            contactsList = contactsList.map {
                                                if (it.name == contact.name && it.phone == contact.phone) {
                                                    it.copy(isSelected = !it.isSelected)
                                                } else {
                                                    it
                                                }
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            contactsList = contactsList.map {
                                                if (it.name == contact.name && it.phone == contact.phone) {
                                                    it.copy(isSelected = checked)
                                                } else {
                                                    it
                                                }
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = BrightGreen)
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = contact.name,
                                            color = TextWhite,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (contact.phone.isNotBlank()) {
                                            Text(
                                                text = contact.phone,
                                                color = TextGray,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val selectedCount = contactsList.count { it.isSelected }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, SmoothBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            val selected = contactsList.filter { it.isSelected }
                            onImportContacts(selected)
                        },
                        modifier = Modifier.weight(1.3f).testTag("confirm_import_contacts_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrightGreen),
                        shape = RoundedCornerShape(8.dp),
                        enabled = selectedCount > 0
                    ) {
                        Text(
                            text = "استيراد المحددين ($selectedCount)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
