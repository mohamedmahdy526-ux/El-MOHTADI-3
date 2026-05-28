package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("almhtdi_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OVERTIME_RATE = "overtime_rate"
        private const val KEY_COMPANY_NAME = "company_name"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var overtimeRate: Double
        get() = prefs.getFloat(KEY_OVERTIME_RATE, 28.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_OVERTIME_RATE, value.toFloat()).apply()

    var companyName: String
        get() = prefs.getString(KEY_COMPANY_NAME, "المهتدي للمقاولات") ?: "المهتدي للمقاولات"
        set(value) = prefs.edit().putString(KEY_COMPANY_NAME, value).apply()

    var siteName: String
        get() = prefs.getString(KEY_SITE_NAME, "الموقِع الرئيسي") ?: "الموقِع الرئيسي"
        set(value) = prefs.edit().putString(KEY_SITE_NAME, value).apply()
}
