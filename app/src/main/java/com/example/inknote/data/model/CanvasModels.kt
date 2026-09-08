package com.example.inknote.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.atomic.AtomicLong

object IdGenerator {
    private val counter = AtomicLong(System.currentTimeMillis().coerceAtLeast(1L) * 1000L)
    fun nextId(): Long = counter.incrementAndGet()
}

enum class ToolType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    TEXT,
    SHAPE,
    RULER,
    PROTRACTOR,
    COMPASS,
    MEDIA
}

enum class TemplateType {
    BLANK,
    LINED_NARROW,
    LINED_WIDE,
    GRID_SMALL,
    GRID_LARGE,
    DOTTED,
    CORNELL,
    MUSIC
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
    val id: Long = IdGenerator.nextId(),
    val pageId: Long = 0,
    val toolType: ToolType = ToolType.PEN,
    val color: Color = Color(0xFF1E293B),
    val strokeWidth: Float = 3.5f,
    val alpha: Float = 1.0f,
    val points: List<StrokePoint> = emptyList(),
    val audioTimestampMs: Long? = null,
    val isGeometricSnap: Boolean = false,
    val isSelected: Boolean = false
) {
    fun toSerializedPoints(): String {
        return points.joinToString(";") { "${it.x},${it.y},${it.pressure},${it.timestamp}" }
    }

    companion object {
        fun parseSerializedPoints(data: String): List<StrokePoint> {
            if (data.isBlank()) return emptyList()
            return data.split(";").mapNotNull { part ->
                val tokens = part.split(",")
                if (tokens.size >= 2) {
                    val x = tokens[0].toFloatOrNull() ?: return@mapNotNull null
                    val y = tokens[1].toFloatOrNull() ?: return@mapNotNull null
                    val pressure = tokens.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                    val timestamp = tokens.getOrNull(3)?.toLongOrNull() ?: 0L
                    StrokePoint(x, y, pressure, timestamp)
                } else null
            }
        }

        fun toJsonList(strokes: List<VectorStroke>): String {
            val array = org.json.JSONArray()
            for (s in strokes) {
                val obj = org.json.JSONObject().apply {
                    put("toolType", s.toolType.name)
                    put("color", s.color.toArgb())
                    put("strokeWidth", s.strokeWidth.toDouble())
                    put("alpha", s.alpha.toDouble())
                    put("points", s.toSerializedPoints())
                    put("audioTimestampMs", s.audioTimestampMs ?: -1L)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun parseJsonList(json: String): List<VectorStroke> {
            if (json.isBlank()) return emptyList()
            val list = mutableListOf<VectorStroke>()
            try {
                val array = org.json.JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val toolType = try { ToolType.valueOf(obj.getString("toolType")) } catch (e: Exception) { ToolType.PEN }
                    val colorArgb = obj.getLong("color").toInt()
                    val strokeWidth = obj.getDouble("strokeWidth").toFloat()
                    val alpha = obj.getDouble("alpha").toFloat()
                    val points = parseSerializedPoints(obj.getString("points"))
                    val audioTimestampMs = obj.optLong("audioTimestampMs", -1L).takeIf { it >= 0 }
                    list.add(
                        VectorStroke(
                            toolType = toolType,
                            color = Color(colorArgb),
                            strokeWidth = strokeWidth,
                            alpha = alpha,
                            points = points,
                            audioTimestampMs = audioTimestampMs
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
            return list
        }
    }
}

data class GeometricShape(
    val id: Long = IdGenerator.nextId(),
    val pageId: Long = 0,
    val shapeType: ShapeType = ShapeType.RECTANGLE,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 100f,
    val endY: Float = 100f,
    val strokeColor: Color = Color(0xFF1E293B),
    val fillColor: Color = Color.Transparent,
    val strokeWidth: Float = 3.5f,
    val rotationDegrees: Float = 0f,
    val isFilled: Boolean = false,
    val sideCount: Int = 5,
    val audioTimestampMs: Long? = null,
    val isSelected: Boolean = false
) {
    val width: Float get() = kotlin.math.abs(endX - startX)
    val height: Float get() = kotlin.math.abs(endY - startY)
    val left: Float get() = kotlin.math.min(startX, endX)
    val top: Float get() = kotlin.math.min(startY, endY)
    val centerX: Float get() = (startX + endX) / 2f
    val centerY: Float get() = (startY + endY) / 2f

    companion object {
        fun toJsonList(shapes: List<GeometricShape>): String {
            val array = org.json.JSONArray()
            for (s in shapes) {
                val obj = org.json.JSONObject().apply {
                    put("shapeType", s.shapeType.name)
                    put("startX", s.startX.toDouble())
                    put("startY", s.startY.toDouble())
                    put("endX", s.endX.toDouble())
                    put("endY", s.endY.toDouble())
                    put("strokeColor", s.strokeColor.toArgb())
                    put("fillColor", s.fillColor.toArgb())
                    put("strokeWidth", s.strokeWidth.toDouble())
                    put("rotationDegrees", s.rotationDegrees.toDouble())
                    put("isFilled", s.isFilled)
                    put("sideCount", s.sideCount)
                    put("audioTimestampMs", s.audioTimestampMs ?: -1L)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun parseJsonList(json: String): List<GeometricShape> {
            if (json.isBlank()) return emptyList()
            val list = mutableListOf<GeometricShape>()
            try {
                val array = org.json.JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val shapeType = try { ShapeType.valueOf(obj.getString("shapeType")) } catch (e: Exception) { ShapeType.RECTANGLE }
                    list.add(
                        GeometricShape(
                            shapeType = shapeType,
                            startX = obj.getDouble("startX").toFloat(),
                            startY = obj.getDouble("startY").toFloat(),
                            endX = obj.getDouble("endX").toFloat(),
                            endY = obj.getDouble("endY").toFloat(),
                            strokeColor = Color(obj.getLong("strokeColor").toInt()),
                            fillColor = Color(obj.getLong("fillColor").toInt()),
                            strokeWidth = obj.getDouble("strokeWidth").toFloat(),
                            rotationDegrees = obj.optDouble("rotationDegrees", 0.0).toFloat(),
                            isFilled = obj.optBoolean("isFilled", false),
                            sideCount = obj.optInt("sideCount", 5),
                            audioTimestampMs = obj.optLong("audioTimestampMs", -1L).takeIf { it >= 0 }
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
            return list
        }
    }
}

data class CanvasTextItem(
    val id: Long = IdGenerator.nextId(),
    val pageId: Long = 0,
    val text: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 220f,
    val height: Float = 80f,
    val fontSize: Float = 16f,
    val fontColor: Color = Color(0xFF1E293B),
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
    companion object {
        fun toJsonList(texts: List<CanvasTextItem>): String {
            val array = org.json.JSONArray()
            for (t in texts) {
                val obj = org.json.JSONObject().apply {
                    put("text", t.text)
                    put("x", t.x.toDouble())
                    put("y", t.y.toDouble())
                    put("width", t.width.toDouble())
                    put("height", t.height.toDouble())
                    put("fontSize", t.fontSize.toDouble())
                    put("fontColor", t.fontColor.toArgb())
                    put("isBold", t.isBold)
                    put("isItalic", t.isItalic)
                    put("backgroundColor", t.backgroundColor.toArgb())
                    put("isMathFormula", t.isMathFormula)
                    put("audioTimestampMs", t.audioTimestampMs ?: -1L)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun parseJsonList(json: String): List<CanvasTextItem> {
            if (json.isBlank()) return emptyList()
            val list = mutableListOf<CanvasTextItem>()
            try {
                val array = org.json.JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CanvasTextItem(
                            text = obj.getString("text"),
                            x = obj.getDouble("x").toFloat(),
                            y = obj.getDouble("y").toFloat(),
                            width = obj.getDouble("width").toFloat(),
                            height = obj.getDouble("height").toFloat(),
                            fontSize = obj.optDouble("fontSize", 16.0).toFloat(),
                            fontColor = Color(obj.getLong("fontColor").toInt()),
                            isBold = obj.optBoolean("isBold", false),
                            isItalic = obj.optBoolean("isItalic", false),
                            backgroundColor = Color(obj.optLong("backgroundColor", 0L).toInt()),
                            isMathFormula = obj.optBoolean("isMathFormula", false),
                            audioTimestampMs = obj.optLong("audioTimestampMs", -1L).takeIf { it >= 0 }
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
            return list
        }
    }
}

data class CanvasMediaItem(
    val id: Long = IdGenerator.nextId(),
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

data class RulerState(
    val isVisible: Boolean = false,
    val centerX: Float = 400f,
    val centerY: Float = 600f,
    val length: Float = 450f,
    val width: Float = 70f,
    val angleDegrees: Float = 0f
)

data class ProtractorState(
    val isVisible: Boolean = false,
    val centerX: Float = 400f,
    val centerY: Float = 600f,
    val radius: Float = 180f,
    val rotationDegrees: Float = 0f,
    val currentSweepAngle: Float = 0f
)

data class CompassState(
    val isVisible: Boolean = false,
    val pivotX: Float = 400f,
    val pivotY: Float = 600f,
    val radius: Float = 120f,
    val currentAngleDegrees: Float = 0f,
    val isDrawingArc: Boolean = false
)
