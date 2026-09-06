package com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.AppearanceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistence seam for the language setting (SharedPreferences in prod). */
interface LanguageStore {
    fun load(): String
    fun save(tag: String)
}

interface AppearanceStore {
    fun load(): String
    fun save(mode: String)
}

class PrefsAppearanceStore(context: Context) : AppearanceStore {
    private val appContext = context.applicationContext
    override fun load(): String = AppearanceManager.load(appContext)
    override fun save(mode: String) = AppearanceManager.save(appContext, mode)
}

class PrefsLanguageStore(context: Context) : LanguageStore {
    private val appContext = context.applicationContext
    override fun load(): String = LocaleManager.persistedTag(appContext)
    override fun save(tag: String) = LocaleManager.persistTag(appContext, tag)
}

/**
 * Settings tab state: in-app language + static rows.
 * Everything here works offline. Language change is persisted
 * synchronously and applied by recreating the Activity.
 */
class SettingsViewModel(
    private val store: LanguageStore,
    val appVersion: String = "1.0",
    private val appearanceStore: AppearanceStore? = null
) : ViewModel() {

    private val _language = MutableStateFlow(LocaleManager.normalizeTag(store.load()))
    val language: StateFlow<String> = _language.asStateFlow()

    private val _appearance = MutableStateFlow(AppearanceManager.normalize(appearanceStore?.load()))
    val appearance: StateFlow<String> = _appearance.asStateFlow()

    fun setLanguage(tag: String) {
        val normalized = LocaleManager.normalizeTag(tag)
        store.save(normalized)
        _language.value = normalized
    }

    fun setAppearance(mode: String) {
        val normalized = AppearanceManager.normalize(mode)
        appearanceStore?.save(normalized)
        _appearance.value = normalized
    }
}
