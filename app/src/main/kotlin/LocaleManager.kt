package com.healthify.app

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * In-app locale manager.
 *
 * Holds the current language as Compose state so any composable that reads
 * [currentLanguage] (directly or via [localized]) recomposes automatically
 * when the language changes — no Activity restart required.
 *
 * Usage:
 *   1. Call [init] once in Application.onCreate.
 *   2. In MainActivity.setContent, wrap content with
 *        CompositionLocalProvider(LocalContext provides LocaleManager.localized(ctx)) { … }
 *   3. Call [setLanguage] from the language picker or profile settings.
 */
object LocaleManager {

    // ISO 639-1 codes — must match values-xx resource-directory suffixes.
    val SUPPORTED: List<String> = listOf("en", "ru", "hy")

    // Language names in their OWN script — never pulled through stringResource.
    val NATIVE_NAME: Map<String, String> = mapOf(
        "en" to "English",
        "ru" to "Русский",
        "hy" to "Հայерեն"   // Hayerên — Armenian Unicode: Հ+ա+յ+ե+ր+ե+ն
    )

    val FLAG: Map<String, String> = mapOf(
        "en" to "🇬🇧",
        "ru" to "🇷🇺",
        "hy" to "🇦🇲"
    )

    /** Compose-observable current language code ("en" | "ru" | "hy"). */
    var currentLanguage by mutableStateOf("en")
        private set

    private const val PREFS_KEY = "app_language"

    /** Restore persisted language. Call once from Application.onCreate. */
    fun init(context: Context) {
        val saved = context
            .getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
        if (saved != null && saved in SUPPORTED) currentLanguage = saved
    }

    /** True once the user has made an explicit language choice. */
    fun hasBeenSelected(context: Context): Boolean =
        context
            .getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)
            .contains(PREFS_KEY)

    /**
     * Persist and apply a new language.
     * Updating [currentLanguage] (mutableStateOf) triggers Compose recomposition;
     * the CompositionLocalProvider in MainActivity re-creates the localized context,
     * causing all stringResource() calls to re-resolve without restarting the app.
     */
    fun setLanguage(context: Context, lang: String) {
        if (lang !in SUPPORTED) return
        currentLanguage = lang
        context
            .getSharedPreferences(HealthifyApp.PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, lang)
            .apply()
    }

    /**
     * Return a locale-aware copy of [context].
     * Called inside remember(currentLanguage) { … } in MainActivity so the
     * CompositionLocal swap happens on the same frame as the state change.
     */
    fun localized(context: Context): Context {
        val locale = Locale(currentLanguage)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
