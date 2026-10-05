package com.example.inknote.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import androidx.compose.ui.graphics.toArgb
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.model.CanvasMediaItem
import com.example.inknote.data.model.CanvasTextItem
import com.example.inknote.data.model.GeometricShape
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.ToolType
import com.example.inknote.data.model.VectorStroke
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

object PageImageRenderer {

    private const val CANVAS_BASE_WIDTH = 1000f
    private const val CANVAS_BASE_HEIGHT = 1414f

    /**
     * Determines whether a note page contains any user authored content
     * (handwritten ink, geometric shapes, typed text, or inserted images).
     */
    fun isPagePopulated(
        page: PageEntity?,
        strokes: List<VectorStroke>,
        shapes: List<GeometricShape>,
        textItems: List<CanvasTextItem>,
        mediaItems: List<CanvasMediaItem>
    ): Boolean {
        if (strokes.isNotEmpty()) return true
        if (shapes.isNotEmpty()) return true
        if (textItems.any { it.text.isNotBlank() && it.text.trim() != "Type here..." }) return true
        if (mediaItems.isNotEmpty()) return true
        val bgPath = page?.customBgPath
        if (bgPath != null && File(bgPath).exists()) return true
        return false
    }

    /**
     * Renders a note page (ink, shapes, typed text, media, background) to a crisp flattened Bitmap.
     */
    fun renderPageToBitmap(
        page: PageEntity?,
        strokes: List<VectorStroke>,
        shapes: List<GeometricShape>,
        textItems: List<CanvasTextItem>,
        mediaItems: List<CanvasMediaItem>,
        targetWidth: Int = 800,
        targetHeight: Int = 1131
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val scaleX = targetWidth.toFloat() / CANVAS_BASE_WIDTH
        val scaleY = targetHeight.toFloat() / CANVAS_BASE_HEIGHT

        // 1. Draw Page Background
        val customBg = page?.customBgPath
        if (customBg != null && File(customBg).exists()) {
            val bgBitmap = try {
                android.graphics.BitmapFactory.decodeFile(customBg)
            } catch (_: Exception) {
                null
            }
            if (bgBitmap != null) {
                val srcRect = Rect(0, 0, bgBitmap.width, bgBitmap.height)
                val dstRect = Rect(0, 0, targetWidth, targetHeight)
                canvas.drawBitmap(bgBitmap, srcRect, dstRect, null)
                bgBitmap.recycle()
            } else {
                canvas.drawColor(android.graphics.Color.WHITE)
            }
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
            drawTemplate(canvas, page?.templateType ?: "BLANK", targetWidth.toFloat(), targetHeight.toFloat(), scaleY)
        }

        // Scale canvas for all drawing items
        canvas.save()
        canvas.scale(scaleX, scaleY)

        // 2. Draw Shapes
        drawShapes(canvas, shapes)

        // 3. Draw Strokes (Pen & Highlighter - Laser pointer is excluded)
        drawStrokes(canvas, strokes)

        // 4. Draw Text items
        drawTextItems(canvas, textItems)

        // 5. Draw Media items
        drawMediaItems(canvas, mediaItems)

        canvas.restore()

        return bitmap
    }

    /**
     * Renders a page to a compressed Base64 JPEG string ready for Gemini multimodal input.
     */
    fun renderPageToBase64Jpeg(
        page: PageEntity?,
        strokes: List<VectorStroke>,
        shapes: List<GeometricShape>,
        textItems: List<CanvasTextItem>,
        mediaItems: List<CanvasMediaItem>,
        targetWidth: Int = 800,
        targetHeight: Int = 1131,
        quality: Int = 82
    ): String {
        val bitmap = renderPageToBitmap(page, strokes, shapes, textItems, mediaItems, targetWidth, targetHeight)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        bitmap.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun drawTemplate(canvas: Canvas, templateType: String, width: Float, height: Float, scaleFactor: Float) {
        val paint = Paint().apply {
            color = android.graphics.Color.argb(40, 100, 116, 139)
            strokeWidth = 1.0f * scaleFactor
            style = Paint.Style.STROKE
        }

        when (templateType) {
            "LINED_NARROW", "LINED_WIDE" -> {
                val spacing = (if (templateType == "LINED_NARROW") 24f else 36f) * scaleFactor
                var y = spacing * 2
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, paint)
                    y += spacing
                }
                paint.color = android.graphics.Color.argb(50, 239, 68, 68)
                val marginX = 70f * scaleFactor
                canvas.drawLine(marginX, 0f, marginX, height, paint)
            }
            "GRID_SMALL", "GRID_LARGE" -> {
                val spacing = (if (templateType == "GRID_SMALL") 20f else 32f) * scaleFactor
                var x = 0f
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, paint)
                    x += spacing
                }
                var y = 0f
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, paint)
                    y += spacing
                }
            }
            "DOTTED" -> {
                val spacing = 28f * scaleFactor
                paint.style = Paint.Style.FILL
                var x = spacing
                while (x < width) {
                    var y = spacing
                    while (y < height) {
                        canvas.drawCircle(x, y, 1.5f * scaleFactor, paint)
                        y += spacing
                    }
                    x += spacing
                }
            }
            "CORNELL" -> {
                val headerY = 120f * scaleFactor
                val leftCueX = 240f * scaleFactor
                val bottomSummaryY = height - (160f * scaleFactor)

                canvas.drawLine(0f, headerY, width, headerY, paint)
                canvas.drawLine(leftCueX, headerY, leftCueX, bottomSummaryY, paint)
                canvas.drawLine(0f, bottomSummaryY, width, bottomSummaryY, paint)

                val lineSpacing = 36f * scaleFactor
                var y = headerY + lineSpacing
                while (y < bottomSummaryY) {
                    canvas.drawLine(leftCueX, y, width, y, paint)
                    y += lineSpacing
                }
            }
        }
    }

    private fun drawStrokes(canvas: Canvas, strokes: List<VectorStroke>) {
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val paint = Paint().apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
                if (stroke.toolType == ToolType.HIGHLIGHTER) {
                    alpha = 85
                    strokeWidth = stroke.strokeWidth * 2.5f
                } else {
                    alpha = (stroke.alpha * 255).toInt().coerceIn(0, 255)
                }
            }

            val path = Path()
            val points = stroke.points
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                path.quadTo(prev.x, prev.y, midX, midY)
            }
            val last = points.last()
            path.lineTo(last.x, last.y)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawShapes(canvas: Canvas, shapes: List<GeometricShape>) {
        shapes.forEach { shape ->
            val strokePaint = Paint().apply {
                color = shape.strokeColor.toArgb()
                strokeWidth = shape.strokeWidth
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val fillPaint = Paint().apply {
                color = shape.fillColor.toArgb()
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            canvas.save()
            if (shape.rotationDegrees != 0f) {
                canvas.rotate(shape.rotationDegrees, shape.centerX, shape.centerY)
            }

            when (shape.shapeType) {
                ShapeType.LINE -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
                }
                ShapeType.ARROW -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, strokePaint)
                    val angle = Math.atan2((shape.endY - shape.startY).toDouble(), (shape.endX - shape.startX).toDouble())
                    val arrowLength = 20.0
                    val arrowAngle = Math.toRadians(25.0)
                    val x1 = shape.endX - arrowLength * cos(angle - arrowAngle)
                    val y1 = shape.endY - arrowLength * sin(angle - arrowAngle)
                    val x2 = shape.endX - arrowLength * cos(angle + arrowAngle)
                    val y2 = shape.endY - arrowLength * sin(angle + arrowAngle)
                    val arrowPath = Path().apply {
                        moveTo(shape.endX, shape.endY)
                        lineTo(x1.toFloat(), y1.toFloat())
                        lineTo(x2.toFloat(), y2.toFloat())
                        close()
                    }
                    strokePaint.style = Paint.Style.FILL_AND_STROKE
                    canvas.drawPath(arrowPath, strokePaint)
                }
                ShapeType.RECTANGLE, ShapeType.SQUARE -> {
                    val rect = RectF(shape.left, shape.top, shape.left + shape.width, shape.top + shape.height)
                    if (shape.isFilled) canvas.drawRect(rect, fillPaint)
                    canvas.drawRect(rect, strokePaint)
                }
                ShapeType.CIRCLE, ShapeType.ELLIPSE -> {
                    val rect = RectF(shape.left, shape.top, shape.left + shape.width, shape.top + shape.height)
                    if (shape.isFilled) canvas.drawOval(rect, fillPaint)
                    canvas.drawOval(rect, strokePaint)
                }
                ShapeType.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(shape.centerX, shape.top)
                        lineTo(shape.left, shape.top + shape.height)
                        lineTo(shape.left + shape.width, shape.top + shape.height)
                        close()
                    }
                    if (shape.isFilled) canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, strokePaint)
                }
                ShapeType.POLYGON, ShapeType.STAR -> {
                    val path = Path()
                    val cx = shape.centerX
                    val cy = shape.centerY
                    val r = shape.width / 2f
                    val sides = shape.sideCount.coerceAtLeast(3)
                    for (i in 0 until sides) {
                        val theta = (i * 2 * Math.PI / sides) - Math.PI / 2
                        val px = (cx + r * cos(theta)).toFloat()
                        val py = (cy + r * sin(theta)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    if (shape.isFilled) canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, strokePaint)
                }
            }
            canvas.restore()
        }
    }

    private fun drawTextItems(canvas: Canvas, items: List<CanvasTextItem>) {
        items.forEach { item ->
            val paint = Paint().apply {
                color = item.fontColor.toArgb()
                textSize = item.fontSize * 1.8f
                isAntiAlias = true
                typeface = if (item.isBold && item.isItalic) {
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                } else if (item.isBold) {
                    Typeface.DEFAULT_BOLD
                } else if (item.isItalic) {
                    Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                } else {
                    Typeface.DEFAULT
                }
            }

            val lines = item.text.lines()
            var currentY = item.y + (item.fontSize * 1.8f)
            val lineHeight = item.fontSize * 2.2f

            lines.forEach { line ->
                canvas.drawText(line, item.x, currentY, paint)
                currentY += lineHeight
            }
        }
    }

    private fun drawMediaItems(canvas: Canvas, items: List<CanvasMediaItem>) {
        items.forEach { item ->
            if (item.localUri.isNotBlank()) {
                val file = File(item.localUri)
                if (file.exists()) {
                    val bmp = try {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    } catch (_: Exception) {
                        null
                    }
                    if (bmp != null) {
                        val src = Rect(0, 0, bmp.width, bmp.height)
                        val dst = RectF(item.x, item.y, item.x + item.width, item.y + item.height)
                        canvas.drawBitmap(bmp, src, dst, null)
                        bmp.recycle()
                    }
                }
            }
        }
    }
}
