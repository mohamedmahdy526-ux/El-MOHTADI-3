package com.example.util

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintDocumentAdapterHelper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object PdfGenerator {

    suspend fun generateReceiptPdf(context: Context, receipt: Receipt): File? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            fun safeResume(result: File?) {
                if (!resumed) {
                    resumed = true
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }

            val webView = WebView(context)
            webView.settings.defaultTextEncodingName = "UTF-8"
            webView.settings.javaScriptEnabled = true

            val htmlContent = buildHtmlContent(receipt)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view == null) {
                        safeResume(null)
                        return
                    }

                    // Create print adapter
                    val printAdapter = view.createPrintDocumentAdapter("Receipt_${receipt.receiptNumber}")

                    // Configure print attributes (A4 print setup)
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("id", "print", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    // Ensure target output file directory exists
                    val outputDirectory = File(context.cacheDir, "receipts_shared").apply {
                        if (!exists()) mkdirs()
                    }
                    val safeFileName = "Receipt_${receipt.workerName.replace(" ", "_")}_${receipt.id}.pdf"
                    val file = File(outputDirectory, safeFileName)

                    try {
                        val descriptor = ParcelFileDescriptor.open(
                            file,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
                        )

                        val cancellationSignal = CancellationSignal()

                        PrintDocumentAdapterHelper.runLayout(
                            printAdapter,
                            null,
                            printAttributes,
                            cancellationSignal,
                            object : PrintDocumentAdapterHelper.LayoutCallback {
                                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                                    PrintDocumentAdapterHelper.runWrite(
                                        printAdapter,
                                        arrayOf(PageRange.ALL_PAGES),
                                        descriptor,
                                        cancellationSignal,
                                        object : PrintDocumentAdapterHelper.WriteCallback {
                                            override fun onWriteFinished(pages: Array<out PageRange>?) {
                                                try {
                                                    descriptor.close()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                safeResume(file)
                                            }

                                            override fun onWriteFailed(error: CharSequence?) {
                                                try {
                                                    descriptor.close()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                safeResume(null)
                                            }

                                            override fun onWriteCancelled() {
                                                try {
                                                    descriptor.close()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                safeResume(null)
                                            }
                                        }
                                    )
                                }

                                override fun onLayoutFailed(error: CharSequence?) {
                                    try {
                                        descriptor.close()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    safeResume(null)
                                }

                                override fun onLayoutCancelled() {
                                    try {
                                        descriptor.close()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    safeResume(null)
                                }
                            },
                            Bundle()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        safeResume(null)
                    }
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun buildHtmlContent(receipt: Receipt): String {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))
        val formattedDate = dateFormat.format(Date(receipt.date))

        val receiptNumber = receipt.receiptNumber
        val workerName = receipt.workerName
        val workedDaysFormat = String.format(Locale("en"), "%.1f", receipt.workedDays)
        val dailySalaryFormat = String.format(Locale("en"), "%,.2f", receipt.dailySalary)
        
        val baseSalaryTotalVal = if (receipt.isPresent) (receipt.workedDays * receipt.dailySalary) else 0.0
        val baseSalaryFormat = String.format(Locale("en"), "%,.2f", baseSalaryTotalVal)
        
        val overtimeHoursFormat = String.format(Locale("en"), "%.1f", receipt.overtimeHours)
        val overtimeRateFormat = String.format(Locale("en"), "%,.2f", receipt.overtimeRate)
        val overtimeAmountFormat = String.format(Locale("en"), "%,.2f", receipt.overtimeAmount)
        val commissionFormat = String.format(Locale("en"), "%,.2f", receipt.commission)
        val advanceFormat = String.format(Locale("en"), "%,.2f", receipt.advance)
        val netAmountFormat = String.format(Locale("en"), "%,.2f", receipt.netAmount)

        val phoneText = if (receipt.workerPhone.isNotBlank()) receipt.workerPhone else "غير مدرج"
        val presenceText = if (receipt.isPresent) "حاضر بالعمل" else "غائب"
        val presenceClass = if (receipt.isPresent) "present" else "absent"

        return """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 0;
                        padding: 30px;
                        color: #2D3748;
                        background-color: #FFFFFF;
                        direction: rtl;
                    }
                    .receipt-card {
                        border: 3px double #007A3E;
                        padding: 24px;
                        border-radius: 12px;
                        max-width: 650px;
                        margin: 0 auto;
                        position: relative;
                        background-color: #FFFFFF;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 20px;
                    }
                    .header h1 {
                        color: #007A3E;
                        font-size: 24px;
                        margin: 0;
                        font-weight: bold;
                    }
                    .header p {
                        color: #718096;
                        font-size: 13px;
                        margin: 5px 0 0 0;
                    }
                    .divider {
                        height: 3px;
                        background-color: #007A3E;
                        margin: 15px 0;
                    }
                    .doc-title-container {
                        text-align: center;
                        margin: 20px 0;
                    }
                    .doc-title {
                        background-color: #E6F5EE;
                        color: #005028;
                        padding: 8px 30px;
                        border-radius: 30px;
                        font-size: 16px;
                        font-weight: bold;
                        display: inline-block;
                    }
                    .meta-info {
                        display: flex;
                        justify-content: space-between;
                        margin-bottom: 20px;
                        font-size: 13px;
                        color: #4A5568;
                    }
                    .meta-left {
                        text-align: left;
                    }
                    .worker-info-box {
                        background-color: #F7FAFC;
                        border: 1px solid #E2E8F0;
                        border-radius: 8px;
                        padding: 15px;
                        margin-bottom: 25px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        font-size: 14px;
                    }
                    .worker-details {
                        display: flex;
                        flex-direction: column;
                        gap: 6px;
                    }
                    .worker-badge {
                        background-color: #007A3E;
                        color: white;
                        padding: 4px 12px;
                        border-radius: 12px;
                        font-size: 11px;
                        font-weight: bold;
                    }
                    .worker-badge.absent {
                        background-color: #E53E3E;
                    }
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 25px;
                        font-size: 13px;
                    }
                    .data-table th {
                        background-color: #EDF2F7;
                        color: #4A5568;
                        text-align: right;
                        padding: 10px;
                        font-weight: bold;
                        border-bottom: 2px solid #CBD5E0;
                    }
                    .data-table td {
                        padding: 10px;
                        border-bottom: 1px solid #E2E8F0;
                        color: #2D3748;
                    }
                    .data-table tr:last-child td {
                        border-bottom: none;
                    }
                    .total-box {
                        background-color: #005028;
                        color: #FFFFFF;
                        border-radius: 8px;
                        padding: 12px 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        font-size: 16px;
                        font-weight: bold;
                        margin-bottom: 30px;
                    }
                    .total-left {
                        font-size: 20px;
                    }
                    .signatures {
                        display: flex;
                        justify-content: space-between;
                        margin-top: 40px;
                        padding: 0 10px;
                    }
                    .sig-col {
                        text-align: center;
                        width: 40%;
                        font-size: 13px;
                        color: #4A5568;
                    }
                    .sig-line {
                        margin-top: 40px;
                        border-top: 1px dashed #A0AEC0;
                        width: 100%;
                    }
                    .stamp-container {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        margin-top: 20px;
                    }
                    .stamp-box {
                        width: 110px;
                        height: 110px;
                        border: 3px double rgba(0, 102, 204, 0.4);
                        border-radius: 50%;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        align-items: center;
                        font-size: 10px;
                        color: rgb(0, 102, 204);
                        font-weight: bold;
                        text-align: center;
                    }
                    .stamp-title {
                        font-size: 11px;
                        margin-bottom: 2px;
                    }
                    .stamp-badge {
                        border: 1px solid rgb(0, 102, 204);
                        padding: 1px 4px;
                        border-radius: 3px;
                        font-size: 8px;
                        margin-top: 2px;
                    }
                </style>
            </head>
            <body>
                <div class="receipt-card">
                    <div class="header">
                        <h1>المهتدي للمقاولات العامة</h1>
                        <p>إدارة وتشغيل وتنفيذ المقاولات العامة والتشطيبات والتوريدات الكلية</p>
                    </div>
                    <div class="divider"></div>
                    <div class="doc-title-container">
                        <span class="doc-title">إيصال استلام مستحقات العمال</span>
                    </div>

                    <div style="display: flex; justify-content: space-between; margin-bottom: 15px; font-size: 13px;">
                        <div>
                            <strong>رقم الإيصال:</strong> $receiptNumber
                        </div>
                        <div style="text-align: left;">
                            <strong>تاريخ الإصدار:</strong> $formattedDate
                        </div>
                    </div>

                    <div class="worker-info-box">
                        <div class="worker-details">
                            <div><strong>اسم العامل:</strong> $workerName</div>
                            <div><strong>رقم الهاتف:</strong> $phoneText</div>
                        </div>
                        <div>
                            <span class="worker-badge $presenceClass">$presenceText</span>
                        </div>
                    </div>

                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>البند والبيان بالتفصيل</th>
                                <th style="text-align: left;">القيمة والعملة</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>يومية العامل الأساسية المتفق عليها بالشركة</td>
                                <td style="text-align: left;">$dailySalaryFormat ج.م</td>
                            </tr>
                            <tr>
                                <td>عدد أيام الحضور والعمل المعتمدة بالوردية</td>
                                <td style="text-align: left;">$workedDaysFormat يوم</td>
                            </tr>
                            <tr>
                                <td>إجمالي مستحقات اليوميات الأساسية المسجلة</td>
                                <td style="text-align: left;">$baseSalaryFormat ج.م</td>
                            </tr>
                            <tr>
                                <td>ساعات كسر الإضافي المقررة ($overtimeHoursFormat ساعة × $overtimeRateFormat ج.م)</td>
                                <td style="text-align: left;">$overtimeAmountFormat ج.م</td>
                            </tr>
                            <tr>
                                <td>إجمالي البدلات والعمولات والمكافآت التقديرية للوردية</td>
                                <td style="text-align: left;">$commissionFormat ج.م</td>
                            </tr>
                            <tr style="color: #E53E3E;">
                                <td>الخصومات والمسحوبات والسُّلفة المستقطعة</td>
                                <td style="text-align: left;">-$advanceFormat ج.م</td>
                            </tr>
                        </tbody>
                    </table>

                    <div class="total-box">
                        <div>الصافي الفعلي المدفوع صرفه للعامل:</div>
                        <div class="total-left">$netAmountFormat ج.م</div>
                    </div>

                    <div class="signatures">
                        <div class="sig-col">
                            <div>توقيع مراجع الحسابات</div>
                            <div class="sig-line"></div>
                        </div>
                        <div class="sig-col">
                            <div class="stamp-container">
                                <div class="stamp-box">
                                    <div class="stamp-title">المهتدي للمقاولات</div>
                                    <div style="font-size: 7px;">موقع الإنشاء والتشييد</div>
                                    <div class="stamp-badge">مسدد بالكامل</div>
                                </div>
                            </div>
                        </div>
                        <div class="sig-col">
                            <div>توقيع المستلم (العامل)</div>
                            <div class="sig-line"></div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
