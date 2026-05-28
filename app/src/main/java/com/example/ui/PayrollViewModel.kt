package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainApplication
import com.example.data.Receipt
import com.example.data.Worker
import com.example.data.ActiveWorksheet
import com.example.data.WorkerReceiptStats
import com.example.util.PdfGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PayrollViewModel(application: Application) : AndroidViewModel(application) {

    private val mainApp = application as? MainApplication
    private val database = mainApp?.database ?: com.example.data.AppDatabase.getDatabase(application)
    private val repository = mainApp?.repository ?: com.example.data.PayrollRepository(database.workerDao(), database.receiptDao())
    private val settingsManager = mainApp?.settingsManager ?: com.example.data.SettingsManager(application)

    // UI state flows
    val allWorkers: StateFlow<List<Worker>> = repository.allWorkers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allReceipts: StateFlow<List<Receipt>> = repository.allReceipts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeTab = MutableStateFlow("workers")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    private val _selectedWorkerForDetails = MutableStateFlow<Worker?>(null)
    val selectedWorkerForDetails: StateFlow<Worker?> = _selectedWorkerForDetails.asStateFlow()

    private val _selectedWorkerForCalculator = MutableStateFlow<Worker?>(null)
    val selectedWorkerForCalculator: StateFlow<Worker?> = _selectedWorkerForCalculator.asStateFlow()

    private val _viewingReceiptPdf = MutableStateFlow<Receipt?>(null)
    val viewingReceiptPdf: StateFlow<Receipt?> = _viewingReceiptPdf.asStateFlow()

    private val _isAddingNewWorker = MutableStateFlow(false)
    val isAddingNewWorker: StateFlow<Boolean> = _isAddingNewWorker.asStateFlow()

    // Configured Active Worksheet States (In-Memory for ultra-fast, safe rendering)
    private val _activeWorksheets = MutableStateFlow<Map<Int, ActiveWorksheet>>(
        mapOf(
            1 to ActiveWorksheet(workerId = 1, activeWorkedDays = 1.0, overtimeHours = 2.0, commission = 50.0, advance = 20.0, isPresent = true),
            2 to ActiveWorksheet(workerId = 2, activeWorkedDays = 1.0, overtimeHours = 1.0, commission = 30.0, advance = 10.0, isPresent = true),
            4 to ActiveWorksheet(workerId = 4, activeWorkedDays = 1.0, overtimeHours = 3.0, commission = 60.0, advance = 15.0, isPresent = true),
            5 to ActiveWorksheet(workerId = 5, activeWorkedDays = 1.0, overtimeHours = 0.0, commission = 0.0, advance = 0.0, isPresent = false)
        )
    )
    val activeWorksheets: StateFlow<Map<Int, ActiveWorksheet>> = _activeWorksheets.asStateFlow()

    // Aggregate Stats Flow on Selected Worker calculated reactively from receipts table
    val selectedWorkerStats: StateFlow<WorkerReceiptStats> = _selectedWorkerForDetails
        .flatMapLatest { worker ->
            if (worker != null) {
                repository.getWorkerStatsFlow(worker.id)
            } else {
                flowOf(null)
            }
        }
        .map { it ?: WorkerReceiptStats() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkerReceiptStats()
        )

    // Configuration Settings
    private val _overtimeRate = MutableStateFlow(settingsManager.overtimeRate)
    val overtimeRate: StateFlow<Double> = _overtimeRate.asStateFlow()

    private val _companyName = MutableStateFlow(settingsManager.companyName)
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    private val _siteName = MutableStateFlow(settingsManager.siteName)
    val siteName: StateFlow<String> = _siteName.asStateFlow()

    private val _isDarkMode = MutableStateFlow(settingsManager.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        // Pre-populate database with the elegant mockup workers on first launch
        viewModelScope.launch {
            val emptyCheck = repository.allWorkers.firstOrNull()
            if (emptyCheck.isNullOrEmpty()) {
                val initialWorkers = listOf(
                    Worker(
                        name = "أحمد محمود",
                        phone = "01023456789",
                        dailySalary = 300.0
                    ),
                    Worker(
                        name = "محمد علي",
                        phone = "01123456789",
                        dailySalary = 300.0
                    ),
                    Worker(
                        name = "سعيد عبد الله",
                        phone = "01223456789",
                        dailySalary = 300.0
                    ),
                    Worker(
                        name = "كريم حسن",
                        phone = "01523456789",
                        dailySalary = 300.0
                    ),
                    Worker(
                        name = "عماد رمضان",
                        phone = "01098765432",
                        dailySalary = 280.0
                    )
                )
                for (worker in initialWorkers) {
                    repository.insertWorker(worker)
                }
            }
        }
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun selectWorkerForDetails(worker: Worker?) {
        _selectedWorkerForDetails.value = worker
    }

    fun selectWorkerForCalculator(worker: Worker?) {
         _selectedWorkerForCalculator.value = worker
    }

    fun setAddingNewWorker(adding: Boolean) {
        _isAddingNewWorker.value = adding
    }

    fun viewReceiptPdf(receipt: Receipt?) {
        _viewingReceiptPdf.value = receipt
    }

    // Settings adjustments
    fun updateOvertimeRate(rate: Double) {
        settingsManager.overtimeRate = rate
        _overtimeRate.value = rate
    }

    fun updateCompanyName(name: String) {
        settingsManager.companyName = name
        _companyName.value = name
    }

    fun updateSiteName(name: String) {
        settingsManager.siteName = name
        _siteName.value = name
    }

    fun updateDarkMode(enabled: Boolean) {
        settingsManager.isDarkMode = enabled
        _isDarkMode.value = enabled
    }

    // CRUD database updates
    fun addWorker(name: String, phone: String, dailySalary: Double) {
        viewModelScope.launch {
            val newWorker = Worker(
                name = name,
                phone = phone,
                dailySalary = dailySalary
            )
            repository.insertWorker(newWorker)
        }
    }

    fun updateActiveWorksheet(worksheet: ActiveWorksheet) {
        _activeWorksheets.value = _activeWorksheets.value.toMutableMap().apply {
            put(worksheet.workerId, worksheet)
        }
    }

    fun resetAllDailyCalculations() {
        _activeWorksheets.value = emptyMap()
    }

    fun autoSaveCurrentDayAndPrepareNextDay(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val workersList = allWorkers.value
            val currentWorksheets = _activeWorksheets.value
            val rate = overtimeRate.value
            val now = System.currentTimeMillis()

            for (worker in workersList) {
                val existingWorksheet = currentWorksheets[worker.id]
                if (existingWorksheet != null && existingWorksheet.isSettled) {
                    continue // Skip workers already settled today
                }
                val worksheet = existingWorksheet ?: ActiveWorksheet(
                    workerId = worker.id,
                    activeWorkedDays = 1.0,
                    overtimeHours = 0.0,
                    commission = 0.0,
                    advance = 0.0,
                    isPresent = true
                )
                repository.settleAndGenerateReceipt(worker, worksheet, rate, now)
            }

            // Auto prepare next day's worksheet for all workers (Default present, 0 extras)
            val nextDayWorksheets = workersList.associate { worker ->
                worker.id to ActiveWorksheet(
                    workerId = worker.id,
                    activeWorkedDays = 1.0,
                    overtimeHours = 0.0,
                    commission = 0.0,
                    advance = 0.0,
                    isPresent = true
                )
            }
            _activeWorksheets.value = nextDayWorksheets

            withContext(Dispatchers.Main) {
                onSuccess()
                Toast.makeText(getApplication(), "تم ترحيل وحفظ يوميات عمال اليوم بنجاح في ملفاتهم، وتجهيز تحضير العمال لليوم التالي!", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteWorker(worker: Worker) {
        viewModelScope.launch {
            repository.deleteWorker(worker)
            _activeWorksheets.value = _activeWorksheets.value.toMutableMap().apply {
                remove(worker.id)
            }
            // If deleting selected worker, clear detail selections
            if (_selectedWorkerForDetails.value?.id == worker.id) {
                _selectedWorkerForDetails.value = null
            }
            if (_selectedWorkerForCalculator.value?.id == worker.id) {
                _selectedWorkerForCalculator.value = null
            }
        }
    }

    fun settleAndGenerateReceipt(worker: Worker, worksheet: ActiveWorksheet, onSuccess: (Receipt) -> Unit) {
        viewModelScope.launch {
            val rate = overtimeRate.value
            val now = System.currentTimeMillis()
            
            // Reconcile and deduct from oldest unpaid daily logs
            repository.markOldLogsAsSettled(
                workerId = worker.id,
                settledDays = worksheet.activeWorkedDays,
                settledOvertime = worksheet.overtimeHours,
                settledCommission = worksheet.commission,
                settledAdvance = worksheet.advance
            )

            // Generate physical payout receipt record
            val receiptId = repository.settleAndGeneratePaidReceipt(worker, worksheet, rate, now)
            
            // Query the newly inserted receipt from DB to send to view
            val insertedReceipt = repository.allReceipts.firstOrNull()?.find { it.id == receiptId.toInt() }
            if (insertedReceipt != null) {
                // Mark the active worksheet as settled upon payment
                _activeWorksheets.value = _activeWorksheets.value.toMutableMap().apply {
                    put(worker.id, worksheet.copy(isSettled = true))
                }

                // If daily wage was edited, let's keep VM list updated
                if (worker.dailySalary != worksheet.activeWorkedDays) {
                    val freshWorker = repository.getWorkerById(worker.id)
                    if (freshWorker != null && _selectedWorkerForCalculator.value?.id == worker.id) {
                        _selectedWorkerForCalculator.value = freshWorker
                    }
                }

                onSuccess(insertedReceipt)
                Toast.makeText(getApplication(), "تم إصدار وتوثيق إيصال الدفع بنجاح وتسوية السجلات!", Toast.LENGTH_SHORT).show()
                
                // Switch back active tab to Receipts list
                _activeTab.value = "receipts"
            }
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            repository.deleteReceipt(receipt)
            if (_viewingReceiptPdf.value?.id == receipt.id) {
                _viewingReceiptPdf.value = null
            }
        }
    }

    // Share features
    fun shareReceiptOnWhatsApp(context: Context, receipt: Receipt) {
        viewModelScope.launch {
            try {
                // Generate the PDF file on the IO dispatcher to completely avoid blocking the main UI thread (ANR prevention)
                val pdfFile = withContext(Dispatchers.IO) {
                    PdfGenerator.generateReceiptPdf(context, receipt)
                }

                val message = buildArabicWhatsAppMessage(receipt)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TEXT, message)
                }

                if (pdfFile != null && pdfFile.exists()) {
                    val authority = "${context.packageName}.fileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, pdfFile)
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    // Fallback to plain text sharing if PDF failed to generate
                    intent.type = "text/plain"
                }

                // Target WhatsApp specifically if possible, or support universal chooser
                val whatsappIntent = Intent.createChooser(intent, "مشاركة الإيصال عبر:")
                whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(whatsappIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Gracefully toast the issue back on the Main thread
                Toast.makeText(context, "عذراً، لم نتمكن من مشاركة مستند الإيصال: ${e.localizedMessage ?: "حدث خطأ غير متوقع"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildArabicWhatsAppMessage(receipt: Receipt): String {
        val isPresentText = if (receipt.isPresent) "حاضر" else "غائب"
        return """
*المهتدي للمقاولات*
إيصال استلام مستحقات العمال
----------------------------------
*اسم العامل:* ${receipt.workerName}
*حالة الحضور:* $isPresentText
*يومية العامل:* ${receipt.dailySalary} ج.م
*ساعات الإضافي:* ${receipt.overtimeHours} ساعة (بقيمة ${receipt.overtimeAmount} ج.م)
*العمولات/المكافآت:* ${receipt.commission} ج.م
*الخصومات/السلفة:* -${receipt.advance} ج.م
----------------------------------
*الصافي المستحق:* *${receipt.netAmount} ج.م*

تم إصدار المعاملة وتحمير المستحقات بنجاح.
    """.trimIndent()
    }
}
