package com.example.data

import kotlinx.coroutines.flow.Flow

class PayrollRepository(
    private val workerDao: WorkerDao,
    private val receiptDao: ReceiptDao
) {
    val allWorkers: Flow<List<Worker>> = workerDao.getAllWorkers()
    val allReceipts: Flow<List<Receipt>> = receiptDao.getAllReceipts()

    fun getWorkerByIdFlow(id: Int): Flow<Worker?> = workerDao.getWorkerByIdFlow(id)
    
    suspend fun getWorkerById(id: Int): Worker? = workerDao.getWorkerById(id)

    suspend fun insertWorker(worker: Worker): Long {
        return workerDao.insertWorker(worker)
    }

    suspend fun updateWorker(worker: Worker) {
        workerDao.updateWorker(worker)
    }

    suspend fun deleteWorker(worker: Worker) {
        workerDao.deleteWorker(worker)
    }

    fun getReceiptsByWorker(workerId: Int): Flow<List<Receipt>> {
        return receiptDao.getReceiptsByWorker(workerId)
    }

    fun getWorkerStatsFlow(workerId: Int): Flow<WorkerReceiptStats?> {
        return receiptDao.getWorkerStatsFlow(workerId)
    }

    suspend fun getWorkerStats(workerId: Int): WorkerReceiptStats? {
        return receiptDao.getWorkerStats(workerId)
    }

    suspend fun settleAndGenerateReceipt(
        worker: Worker,
        worksheet: ActiveWorksheet,
        overtimeRate: Double,
        customDate: Long = System.currentTimeMillis()
    ): Long {
        // Calculate the exact net values
        val overtimeAmount = worksheet.overtimeHours * overtimeRate
        val workedDays = if (worksheet.isPresent) worksheet.activeWorkedDays else 0.0
        val dailyWage = workedDays * worker.dailySalary
        val netAmount = dailyWage + overtimeAmount + worksheet.commission - worksheet.advance
        
        // Build receipt number dynamically
        val stamp = customDate.toString().takeLast(4)
        val rndBytes = (10..99).random()
        val rNum = "W-$stamp$rndBytes" // W- prefix for work log (unpaid day)

        val receipt = Receipt(
            workerId = worker.id,
            workerName = worker.name,
            workerPhone = worker.phone,
            date = customDate,
            dailySalary = worker.dailySalary,
            workedDays = workedDays,
            overtimeHours = worksheet.overtimeHours,
            overtimeRate = overtimeRate,
            overtimeAmount = overtimeAmount,
            commission = worksheet.commission,
            advance = worksheet.advance,
            netAmount = netAmount,
            isPresent = worksheet.isPresent,
            receiptNumber = rNum,
            isPaid = false // Daily Work logs are UNPAID
        )

        val receiptId = receiptDao.insertReceipt(receipt)

        if (worker.dailySalary != receipt.dailySalary && receipt.dailySalary > 0) {
            workerDao.updateWorker(worker.copy(dailySalary = receipt.dailySalary))
        }

        return receiptId
    }

    suspend fun settleAndGeneratePaidReceipt(
        worker: Worker,
        worksheet: ActiveWorksheet,
        overtimeRate: Double,
        customDate: Long = System.currentTimeMillis()
    ): Long {
        val overtimeAmount = worksheet.overtimeHours * overtimeRate
        val workedDays = worksheet.activeWorkedDays
        val dailyWage = workedDays * worker.dailySalary
        val netAmount = dailyWage + overtimeAmount + worksheet.commission - worksheet.advance
        
        val stamp = customDate.toString().takeLast(4)
        val rndBytes = (10..99).random()
        val rNum = "R-$stamp$rndBytes" // R- prefix for Cash Payout Receipt (paid)

        val receipt = Receipt(
            workerId = worker.id,
            workerName = worker.name,
            workerPhone = worker.phone,
            date = customDate,
            dailySalary = worker.dailySalary,
            workedDays = workedDays,
            overtimeHours = worksheet.overtimeHours,
            overtimeRate = overtimeRate,
            overtimeAmount = overtimeAmount,
            commission = worksheet.commission,
            advance = worksheet.advance,
            netAmount = netAmount,
            isPresent = worksheet.isPresent,
            receiptNumber = rNum,
            isPaid = true // Settle payout is PAID
        )

        val receiptId = receiptDao.insertReceipt(receipt)

        if (worker.dailySalary != receipt.dailySalary && receipt.dailySalary > 0) {
            workerDao.updateWorker(worker.copy(dailySalary = receipt.dailySalary))
        }

        return receiptId
    }

    suspend fun markOldLogsAsSettled(
        workerId: Int,
        settledDays: Double,
        settledOvertime: Double,
        settledCommission: Double,
        settledAdvance: Double
    ) {
        val unpaidReceipts = receiptDao.getUnpaidReceiptsByWorker(workerId)
        
        var remainingDays = settledDays
        var remainingOvertime = settledOvertime
        var remainingCommission = settledCommission
        var remainingAdvance = settledAdvance
        
        for (receipt in unpaidReceipts) {
            if (remainingDays <= 0 && remainingOvertime <= 0 && remainingCommission <= 0 && remainingAdvance <= 0) {
                break
            }
            
            var updatedWorkedDays = receipt.workedDays
            var updatedOvertime = receipt.overtimeHours
            var updatedCommission = receipt.commission
            var updatedAdvance = receipt.advance
            
            // Settle days
            if (remainingDays > 0 && receipt.workedDays > 0) {
                if (receipt.workedDays <= remainingDays) {
                    remainingDays -= receipt.workedDays
                    updatedWorkedDays = 0.0
                } else {
                    updatedWorkedDays = receipt.workedDays - remainingDays
                    remainingDays = 0.0
                }
            }
            
            // Settle overtime
            if (remainingOvertime > 0 && receipt.overtimeHours > 0) {
                if (receipt.overtimeHours <= remainingOvertime) {
                    remainingOvertime -= receipt.overtimeHours
                    updatedOvertime = 0.0
                } else {
                    updatedOvertime = receipt.overtimeHours - remainingOvertime
                    remainingOvertime = 0.0
                }
            }
            
            // Settle commission
            if (remainingCommission > 0 && receipt.commission > 0) {
                if (receipt.commission <= remainingCommission) {
                    remainingCommission -= receipt.commission
                    updatedCommission = 0.0
                } else {
                    updatedCommission = receipt.commission - remainingCommission
                    remainingCommission = 0.0
                }
            }
            
            // Settle advance
            if (remainingAdvance > 0 && receipt.advance > 0) {
                if (receipt.advance <= remainingAdvance) {
                    remainingAdvance -= receipt.advance
                    updatedAdvance = 0.0
                } else {
                    updatedAdvance = receipt.advance - remainingAdvance
                    remainingAdvance = 0.0
                }
            }
            
            // If all attributes of the daily work log are fully paid, mark it as isPaid = true
            if (updatedWorkedDays == 0.0 && updatedOvertime == 0.0 && updatedCommission == 0.0 && updatedAdvance == 0.0) {
                receiptDao.updateReceipt(receipt.copy(isPaid = true))
            } else {
                // Update with remaining balances
                val dailyWage = updatedWorkedDays * receipt.dailySalary
                val overtimeAmt = updatedOvertime * receipt.overtimeRate
                val newNet = dailyWage + overtimeAmt + updatedCommission - updatedAdvance
                
                receiptDao.updateReceipt(receipt.copy(
                    workedDays = updatedWorkedDays,
                    overtimeHours = updatedOvertime,
                    commission = updatedCommission,
                    advance = updatedAdvance,
                    netAmount = newNet
                ))
            }
        }
    }

    suspend fun deleteReceipt(receipt: Receipt) {
        receiptDao.deleteReceipt(receipt)
    }
}
