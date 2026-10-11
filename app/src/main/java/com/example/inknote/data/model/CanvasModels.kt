package com.example.inknote.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ToolType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    LASER,
    TEXT,
    SHAPE,
    RULER,
    MEDIA,
    HAND
}

enum class EraserMode {
    STROKE,
    AREA;

    companion object {
        val WHOLE_STROKE = STROKE
        val PARTIAL = AREA
        val PARTIAL_PRECISION = AREA
    }
}

enum class PalmRejectionMode {
    STYLUS_ONLY,
    AUTO,
    OFF
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class TemplateType {
    BLANK,
    LINED_NARROW,
    LINED_WIDE,
    GRID_SMALL,
    GRID_LARGE,
    DOTTED,
    CORNELL,
    ISOMETRIC,
    MUSIC,
    HABIT_TRACKER,
    DAILY_GOALS,
    WEEKLY_PLANNER,
    DAILY_PLANNER,
    GOAL_SETTING,
    ASSIGNMENT_PLANNER,
    MIND_MAP,
    SEMESTER_OVERVIEW,
    MONTHLY_PLANNER
}

enum class ShapeType {
    LINE,
    ARROW,
    RECTANGLE,
    SQUARE,
    CIRCLE,
    ELLIPSE,
    TRIANGLE,
    POLYGON,
    STAR
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class VectorStroke(
    val id: Long = System.currentTimeMillis(),
    val pageId: Long = 0,
    val toolType: ToolType = ToolType.PEN,
    val color: Color = Color.Black,
    val strokeWidth: Float = 4f,
    val alpha: Float = 1.0f,
    val points: List<StrokePoint> = emptyList(),
    val audioTimestampMs: Long? = null,
    val isGeometricSnap: Boolean = false,
    val isSelected: Boolean = false
) {
    val colorArgb: Int get() = color.toArgb()

    fun toSerializedPoints(): String {
        return points.joinToString(";") { "${it.x},${it.y},${it.pressure},${it.timestamp}" }
    }

    companion object {
        fun parseSerializedPoints(serialized: String): List<StrokePoint> {
            if (serialized.isBlank()) return emptyList()
            return serialized.split(";").mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size >= 2) {
                    val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
                    val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
                    val p = if (parts.size >= 3) parts[2].toFloatOrNull() ?: 1.0f else 1.0f
                    val ts = if (parts.size >= 4) parts[3].toLongOrNull() ?: 0L else 0L
                    StrokePoint(x, y, p, ts)
                } else null
            }
        }
    }
}

data class GeometricShape(
    val id: Long = System.currentTimeMillis(),
    val pageId: Long = 0,
    val shapeType: ShapeType = ShapeType.RECTANGLE,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 100f,
    val endY: Float = 100f,
    val strokeColor: Color = Color.Black,
    val fillColor: Color = Color.Transparent,
    val strokeWidth: Float = 3f,
    val rotationDegrees: Float = 0f,
    val isFilled: Boolean = false,
    val sideCount: Int = 5,
    val audioTimestampMs: Long? = null,
    val isSelected: Boolean = false
) {
    val strokeColorArgb: Int get() = strokeColor.toArgb()
    val fillColorArgb: Int get() = fillColor.toArgb()

    val width: Float get() = kotlin.math.abs(endX - startX)
    val height: Float get() = kotlin.math.abs(endY - startY)
    val left: Float get() = kotlin.math.min(startX, endX)
    val top: Float get() = kotlin.math.min(startY, endY)
    val centerX: Float get() = (startX + endX) / 2f
    val centerY: Float get() = (startY + endY) / 2f
}

data class CanvasTextItem(
    val id: Long = System.currentTimeMillis(),
    val pageId: Long = 0,
    val text: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 220f,
    val height: Float = 80f,
    val fontSize: Float = 16f,
    val fontColor: Color = Color.Black,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val backgroundColor: Color = Color.Transparent,
    val audioTimestampMs: Long? = null,
    val isEditing: Boolean = false,
    val isSelected: Boolean = false,
    val isMathFormula: Boolean = false,
    val linkNotebookId: Long? = null,
    val linkNotebookTitle: String? = null
) {
    val colorArgb: Int get() = fontColor.toArgb()
}

typealias TextItem = CanvasTextItem

data class CanvasMediaItem(
    val id: Long = System.currentTimeMillis(),
    val pageId: Long = 0,
    val localUri: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 260f,
    val height: Float = 200f,
    val rotationDegrees: Float = 0f,
    val audioTimestampMs: Long? = null,
    val isSelected: Boolean = false
)

typealias MediaItem = CanvasMediaItem

data class RulerState(
    val isVisible: Boolean = false,
    val centerX: Float = 400f,
    val centerY: Float = 600f,
    val length: Float = 450f,
    val width: Float = 70f,
    val angleDegrees: Float = 0f
)
