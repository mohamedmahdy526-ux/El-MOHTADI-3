package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.PayrollRepository
import com.example.data.SettingsManager

class MainApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { PayrollRepository(database.workerDao(), database.receiptDao()) }
    val settingsManager by lazy { SettingsManager(this) }
}
