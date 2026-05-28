package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workers")
data class Worker(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val dailySalary: Double = 300.0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ActiveWorksheet(
    val workerId: Int,
    val activeWorkedDays: Double = 1.0,
    val overtimeHours: Double = 0.0,
    val commission: Double = 0.0,
    val advance: Double = 0.0,
    val isPresent: Boolean = true,
    val isSettled: Boolean = false
)
