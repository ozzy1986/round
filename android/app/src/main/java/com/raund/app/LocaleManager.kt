package com.raund.app

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object LocaleManager {
    private const val PREFS = "raund_prefs"
    private const val KEY_LANG = "app_language"

    fun getStoredLanguage(context: Context): String {
        return prefs(context).getString(KEY_LANG, "") ?: ""
    }

    fun setLanguage(context: Context, langTag: String) {
        prefs(context).edit().putString(KEY_LANG, langTag).apply()
    }

    fun currentLanguageTag(context: Context): String {
        val stored = getStoredLanguage(context)
        if (stored.isNotEmpty()) return stored
        return context.resources.configuration.locales[0]?.language ?: "en"
    }

    fun applyLocale(baseContext: Context): Context {
        val lang = getStoredLanguage(baseContext)
        if (lang.isEmpty()) return baseContext
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
