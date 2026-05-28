package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class WorkerReceiptStats(
    val workedDaysCount: Double = 0.0,
    val totalOvertimeHours: Double = 0.0,
    val totalCommission: Double = 0.0,
    val totalAdvance: Double = 0.0,
    val totalPaid: Double = 0.0,
    val lastPaymentDate: Long? = null,
    val paidDaysCount: Double = 0.0
)

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY date DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE workerId = :workerId ORDER BY date DESC")
    fun getReceiptsByWorker(workerId: Int): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE workerId = :workerId AND isPaid = 0 ORDER BY date ASC")
    suspend fun getUnpaidReceiptsByWorker(workerId: Int): List<Receipt>

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getReceiptById(id: Int): Receipt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt): Long

    @Update
    suspend fun updateReceipt(receipt: Receipt)

    @Delete
    suspend fun deleteReceipt(receipt: Receipt)

    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN workedDays ELSE 0.0 END), 0.0) as workedDaysCount,
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN overtimeHours ELSE 0.0 END), 0.0) as totalOvertimeHours,
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN commission ELSE 0.0 END), 0.0) as totalCommission,
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN advance ELSE 0.0 END), 0.0) as totalAdvance,
            COALESCE(SUM(CASE WHEN isPaid = 1 THEN netAmount ELSE 0.0 END), 0.0) as totalPaid,
            MAX(CASE WHEN isPaid = 1 THEN date ELSE NULL END) as lastPaymentDate,
            COALESCE(SUM(CASE WHEN isPaid = 1 THEN workedDays ELSE 0.0 END), 0.0) as paidDaysCount
        FROM receipts 
        WHERE workerId = :workerId
    """)
    fun getWorkerStatsFlow(workerId: Int): Flow<WorkerReceiptStats?>

    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN workedDays ELSE 0.0 END), 0.0) as workedDaysCount,
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN overtimeHours ELSE 0.0 END), 0.0) as totalOvertimeHours,
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN commission ELSE 0.0 END), 0.0) as totalCommission,
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN advance ELSE 0.0 END), 0.0) as totalAdvance,
            COALESCE(SUM(CASE WHEN isPaid = 1 THEN netAmount ELSE 0.0 END), 0.0) as totalPaid,
            MAX(CASE WHEN isPaid = 1 THEN date ELSE NULL END) as lastPaymentDate,
            COALESCE(SUM(CASE WHEN isPaid = 1 THEN workedDays ELSE 0.0 END), 0.0) as paidDaysCount
        FROM receipts 
        WHERE workerId = :workerId
    """)
    suspend fun getWorkerStats(workerId: Int): WorkerReceiptStats?
}
