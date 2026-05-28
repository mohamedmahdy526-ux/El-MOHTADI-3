package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workerId: Int,
    val workerName: String,
    val workerPhone: String = "",
    val date: Long = System.currentTimeMillis(),
    
    // Exact settlement numbers (for permanent record)
    val dailySalary: Double,
    val workedDays: Double = 1.0,
    val overtimeHours: Double,
    val overtimeRate: Double,
    val overtimeAmount: Double,
    val commission: Double,
    val advance: Double,
    val netAmount: Double,
    val isPresent: Boolean,
    
    val receiptNumber: String = "",
    val isPaid: Boolean = false
)
