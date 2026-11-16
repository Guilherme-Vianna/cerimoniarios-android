package com.cerimoniarios.app

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _adsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADS_ENABLED, true))
    val adsEnabled: StateFlow<Boolean> = _adsEnabled.asStateFlow()

    /** Last supporter status confirmed by Google Play, used until Play answers again after a cold start. */
    var cachedSupporter: Boolean
        get() = prefs.getBoolean(KEY_SUPPORTER, false)
        set(value) = prefs.edit { putBoolean(KEY_SUPPORTER, value) }

    fun setAdsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ADS_ENABLED, enabled) }
        _adsEnabled.value = enabled
    }

    private companion object {
        const val KEY_ADS_ENABLED = "ads_enabled"
        const val KEY_SUPPORTER = "supporter"
    }
}
