package com.example.inknote.data.preferences

import android.content.Context
import com.example.inknote.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide Theme State Provider to ensure theme changes apply instantly across all screens
 * and persist continuously.
 */
object ThemeStateHolder {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isDarkPaperEnabled = MutableStateFlow(true)
    val isDarkPaperEnabled: StateFlow<Boolean> = _isDarkPaperEnabled.asStateFlow()

    private val _isInvertInkInDarkMode = MutableStateFlow(true)
    val isInvertInkInDarkMode: StateFlow<Boolean> = _isInvertInkInDarkMode.asStateFlow()

    fun init(context: Context) {
        val settings = SettingsManager(context)
        _themeMode.value = settings.themeMode
        _isDarkPaperEnabled.value = settings.isDarkPaperEnabled
        _isInvertInkInDarkMode.value = settings.isInvertInkInDarkMode
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        SettingsManager(context).themeMode = mode
    }

    fun setDarkPaperEnabled(context: Context, enabled: Boolean) {
        _isDarkPaperEnabled.value = enabled
        SettingsManager(context).isDarkPaperEnabled = enabled
    }

    fun setInvertInkInDarkMode(context: Context, invert: Boolean) {
        _isInvertInkInDarkMode.value = invert
        SettingsManager(context).isInvertInkInDarkMode = invert
    }
}
