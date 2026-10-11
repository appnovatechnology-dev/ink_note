package com.example.inknote.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.inknote.data.model.PalmRejectionMode
import com.example.inknote.viewmodel.EraserMode

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "inknote_settings"

        private const val KEY_PALM_REJECTION_MODE = "palm_rejection_mode"
        private const val KEY_PEN_COLOR = "pen_color"
        private const val KEY_PEN_WIDTH = "pen_width"
        private const val KEY_HIGHLIGHTER_COLOR = "highlighter_color"
        private const val KEY_HIGHLIGHTER_WIDTH = "highlighter_width"
        private const val KEY_ERASER_MODE = "eraser_mode"
        private const val KEY_ERASER_WIDTH = "eraser_width"
        private const val KEY_MAGIC_INK = "magic_ink_enabled"
        private const val KEY_LASER_COLOR = "laser_color"
        private const val KEY_LASER_TAIL = "laser_tail_enabled"
        private const val KEY_TOOLBAR_COLLAPSED = "toolbar_collapsed"
        private const val KEY_TOOLBAR_POS_X = "toolbar_pos_x"
        private const val KEY_TOOLBAR_POS_Y = "toolbar_pos_y"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_DARK_PAPER = "dark_paper_enabled"
        private const val KEY_INVERT_INK_DARK_MODE = "invert_ink_in_dark_mode"
    }

    var themeMode: com.example.inknote.data.model.ThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, com.example.inknote.data.model.ThemeMode.SYSTEM.name)
            return try {
                com.example.inknote.data.model.ThemeMode.valueOf(name ?: com.example.inknote.data.model.ThemeMode.SYSTEM.name)
            } catch (e: Exception) {
                com.example.inknote.data.model.ThemeMode.SYSTEM
            }
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    var isDarkPaperEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_PAPER, true) // Enabled by default when in dark mode
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_PAPER, value).apply()
        }

    var isInvertInkInDarkMode: Boolean
        get() = prefs.getBoolean(KEY_INVERT_INK_DARK_MODE, true) // Legibility auto-adjust on dark paper
        set(value) {
            prefs.edit().putBoolean(KEY_INVERT_INK_DARK_MODE, value).apply()
        }

    var palmRejectionMode: PalmRejectionMode
        get() {
            val name = prefs.getString(KEY_PALM_REJECTION_MODE, PalmRejectionMode.STYLUS_ONLY.name)
            return try {
                PalmRejectionMode.valueOf(name ?: PalmRejectionMode.STYLUS_ONLY.name)
            } catch (e: Exception) {
                PalmRejectionMode.STYLUS_ONLY
            }
        }
        set(value) {
            prefs.edit().putString(KEY_PALM_REJECTION_MODE, value.name).apply()
        }

    var penColor: Color
        get() {
            val argb = prefs.getInt(KEY_PEN_COLOR, 0xFF1E293B.toInt())
            return Color(argb)
        }
        set(value) {
            prefs.edit().putInt(KEY_PEN_COLOR, value.toArgb()).apply()
        }

    var penWidth: Float
        get() = prefs.getFloat(KEY_PEN_WIDTH, 4.0f)
        set(value) {
            prefs.edit().putFloat(KEY_PEN_WIDTH, value).apply()
        }

    var highlighterColor: Color
        get() {
            val argb = prefs.getInt(KEY_HIGHLIGHTER_COLOR, 0xFFFBBF24.toInt())
            return Color(argb)
        }
        set(value) {
            prefs.edit().putInt(KEY_HIGHLIGHTER_COLOR, value.toArgb()).apply()
        }

    var highlighterWidth: Float
        get() = prefs.getFloat(KEY_HIGHLIGHTER_WIDTH, 24.0f)
        set(value) {
            prefs.edit().putFloat(KEY_HIGHLIGHTER_WIDTH, value).apply()
        }

    var eraserMode: EraserMode
        get() {
            val name = prefs.getString(KEY_ERASER_MODE, EraserMode.STROKE.name)
            return try {
                EraserMode.valueOf(name ?: EraserMode.STROKE.name)
            } catch (e: Exception) {
                EraserMode.STROKE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_ERASER_MODE, value.name).apply()
        }

    var eraserWidth: Float
        get() = prefs.getFloat(KEY_ERASER_WIDTH, 30.0f)
        set(value) {
            prefs.edit().putFloat(KEY_ERASER_WIDTH, value).apply()
        }

    var isMagicInkEnabled: Boolean
        get() = prefs.getBoolean(KEY_MAGIC_INK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MAGIC_INK, value).apply()
        }

    var laserColor: Color
        get() {
            val argb = prefs.getInt(KEY_LASER_COLOR, 0xFFEF4444.toInt())
            return Color(argb)
        }
        set(value) {
            prefs.edit().putInt(KEY_LASER_COLOR, value.toArgb()).apply()
        }

    var isLaserTailEnabled: Boolean
        get() = prefs.getBoolean(KEY_LASER_TAIL, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LASER_TAIL, value).apply()
        }

    var isToolbarCollapsed: Boolean
        get() = prefs.getBoolean(KEY_TOOLBAR_COLLAPSED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TOOLBAR_COLLAPSED, value).apply()
        }

    var toolbarOffsetX: Float
        get() = prefs.getFloat(KEY_TOOLBAR_POS_X, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_TOOLBAR_POS_X, value).apply()
        }

    var toolbarOffsetY: Float
        get() = prefs.getFloat(KEY_TOOLBAR_POS_Y, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_TOOLBAR_POS_Y, value).apply()
        }

    fun resetToolbarPosition() {
        prefs.edit()
            .putFloat(KEY_TOOLBAR_POS_X, 0f)
            .putFloat(KEY_TOOLBAR_POS_Y, 0f)
            .putBoolean(KEY_TOOLBAR_COLLAPSED, false)
            .apply()
    }
}
