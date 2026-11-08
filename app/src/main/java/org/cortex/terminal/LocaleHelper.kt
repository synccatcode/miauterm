package org.cortex.terminal

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {
    const val PREF_KEY = "app_language"
    const val SYSTEM = LanguageTags.SYSTEM

    fun applySaved(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val tag = try {
            PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, SYSTEM)
        } catch (_: Exception) {
            SYSTEM
        }
        AppCompatDelegate.setApplicationLocales(localesFor(tag))
    }

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(localesFor(tag))
    }

    fun currentSelection(available: List<String>): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        return LanguageTags.match(locales[0]?.toLanguageTag(), available)
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tag = try {
            PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, SYSTEM)
        } catch (_: Exception) {
            SYSTEM
        }
        if (tag.isNullOrEmpty() || tag == SYSTEM) return context
        return try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            context.createConfigurationContext(config)
        } catch (_: Exception) {
            context
        }
    }

    internal fun localesFor(tag: String?): LocaleListCompat =
        if (tag.isNullOrEmpty() || tag == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
}
