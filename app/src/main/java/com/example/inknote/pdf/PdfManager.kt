package com.example.inknote.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.toArgb
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.model.*
import com.example.inknote.data.repository.NoteRepository
import com.example.inknote.viewmodel.PageDataState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

data class PdfImportProgressState(
    val notebookId: Long,
    val current: Int,
    val total: Int,
    val message: String
)

object PdfManager {

    private val _activeImportProgress = MutableStateFlow<PdfImportProgressState?>(null)
    val activeImportProgress = _activeImportProgress.asStateFlow()

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result ?: "Document.pdf"
    }

    fun copyUriToTempFile(context: Context, uri: Uri): Uri {
        return try {
            val fileName = getFileNameFromUri(context, uri)
            val cleanName = com.example.inknote.util.FileNameSanitizer.formatExportFileName(fileName, "pdf")
            val tempFile = File(context.cacheDir, cleanName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            Uri.fromFile(tempFile)
        } catch (_: Exception) {
            uri
        }
    }

    suspend fun importPdfToNotebook(
        context: Context,
        pdfUri: Uri,
        notebookId: Long,
        repository: NoteRepository,
        isAppending: Boolean = false,
        onFirstPageReady: (suspend (firstPageId: Long) -> Unit)? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val fileDescriptor: ParcelFileDescriptor? = try {
            contentResolver.openFileDescriptor(pdfUri, "r")
        } catch (_: Exception) {
            null
        }
        if (fileDescriptor == null) return@withContext 0

        val renderer = try {
            PdfRenderer(fileDescriptor)
        } catch (_: Exception) {
            fileDescriptor.close()
            return@withContext 0
        }

        val pageCount = renderer.pageCount
        if (pageCount == 0) {
            renderer.close()
            fileDescriptor.close()
            return@withContext 0
        }

        val actionPrefix = if (isAppending) "Appending" else "Importing"
        _activeImportProgress.value = PdfImportProgressState(
            notebookId = notebookId,
            current = 0,
            total = pageCount,
            message = "$actionPrefix page 1 of $pageCount..."
        )

        val pdfDir = File(context.filesDir, "pdf_pages_$notebookId")
        if (!pdfDir.exists()) pdfDir.mkdirs()

        // Unique batch ID to avoid collisions when multiple PDFs are appended over time
        val batchId = System.currentTimeMillis()

        // Standard screen-friendly max dimension for fast loading and crisp display
        val targetMaxDimension = 1400f

        for (i in 0 until pageCount) {
            try {
                val page = renderer.openPage(i)
                val pageMax = maxOf(page.width, page.height).toFloat()
                val scale = if (pageMax > 0f) (targetMaxDimension / pageMax).coerceIn(1.0f, 1.8f) else 1.2f
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageFile = File(pdfDir, "page_${batchId}_${i + 1}.jpg")
                FileOutputStream(pageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    fos.flush()
                }
                bitmap.recycle()

                // Add page to database
                val newPageId = repository.addPage(
                    notebookId = notebookId,
                    templateType = "BLANK",
                    customBgPath = pageFile.absolutePath,
                    pdfPageNum = i + 1
                )

                // Progressive progress update (e.g. "Importing page 3 of 40")
                val msg = "$actionPrefix page ${i + 1} of $pageCount"
                _activeImportProgress.value = PdfImportProgressState(
                    notebookId = notebookId,
                    current = i + 1,
                    total = pageCount,
                    message = msg
                )
                onProgress?.invoke(i + 1, pageCount)

                // When the first page is ready, notify immediately so the note can open
                if (i == 0 && onFirstPageReady != null) {
                    onFirstPageReady(newPageId)
                }
            } catch (e: Exception) {
                // Continue with remaining pages if a single page fails
                onProgress?.invoke(i + 1, pageCount)
            }
        }

        try {
            renderer.close()
            fileDescriptor.close()
        } catch (_: Exception) {}

        _activeImportProgress.value = null

        return@withContext pageCount
    }

    suspend fun exportNotebookToPdf(
        context: Context,
        notebookTitle: String,
        pages: List<PageEntity>,
        repository: NoteRepository,
        pagesDataMap: Map<Long, PageDataState> = emptyMap(),
        pageNumber: Int? = null
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val fileName = com.example.inknote.util.FileNameSanitizer.formatExportFileName(
            rawTitle = notebookTitle,
            extension = "pdf",
            pageNumber = pageNumber
        )
        val outputFile = File(exportDir, fileName)

        pages.forEachIndexed { index, pageEntity ->
            val pageWidth = 1000
            val pageHeight = 1414
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas

            // 1. Draw Page Background
            if (pageEntity.customBgPath != null && File(pageEntity.customBgPath).exists()) {
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                val bgBitmap = try {
                    android.graphics.BitmapFactory.decodeFile(pageEntity.customBgPath, opts)
                } catch (e: Exception) {
                    null
                }
                if (bgBitmap != null) {
                    val srcRect = android.graphics.Rect(0, 0, bgBitmap.width, bgBitmap.height)
                    val dstRect = android.graphics.Rect(0, 0, pageWidth, pageHeight)
                    canvas.drawBitmap(bgBitmap, srcRect, dstRect, null)
                    bgBitmap.recycle()
                }
            } else {
                // Draw background color and template pattern
                canvas.drawColor(android.graphics.Color.WHITE)
                drawTemplate(canvas, pageEntity.templateType, pageWidth.toFloat(), pageHeight.toFloat())
            }

            val cachedData = pagesDataMap[pageEntity.id]

            // 2. Draw Shapes
            val shapesList = cachedData?.shapes ?: repository.getShapesListForExport(pageEntity.id)
            drawShapesOnCanvas(canvas, shapesList)

            // 3. Draw Strokes (Pen & Highlighter - Laser pointer is never included)
            val strokesList = cachedData?.strokes ?: repository.getStrokesListForExport(pageEntity.id)
            drawStrokesOnCanvas(canvas, strokesList)

            // 4. Draw Text items
            val textList = cachedData?.textItems ?: repository.getTextListForExport(pageEntity.id)
            drawTextItemsOnCanvas(canvas, textList)

            // 5. Draw Media items
            val mediaList = cachedData?.mediaItems ?: repository.getMediaListForExport(pageEntity.id)
            drawMediaItemsOnCanvas(canvas, mediaList)

            pdfDocument.finishPage(pdfPage)
        }

        val fos = FileOutputStream(outputFile)
        pdfDocument.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDocument.close()

        return@withContext outputFile
    }

    suspend fun exportIndividualPagesToPdf(
        context: Context,
        notebookTitle: String,
        pages: List<PageEntity>,
        repository: NoteRepository,
        pagesDataMap: Map<Long, PageDataState> = emptyMap()
    ): List<File> = withContext(Dispatchers.IO) {
        pages.mapIndexed { index, pageEntity ->
            val pageNum = pageEntity.pageNumber.takeIf { it > 0 } ?: (index + 1)
            exportNotebookToPdf(
                context = context,
                notebookTitle = notebookTitle,
                pages = listOf(pageEntity),
                repository = repository,
                pagesDataMap = pagesDataMap,
                pageNumber = pageNum
            )
        }
    }

    private fun drawTemplate(canvas: Canvas, templateType: String, width: Float, height: Float) {
        val paint = Paint().apply {
            color = android.graphics.Color.argb(45, 100, 116, 139)
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }

        when (templateType) {
            "LINED_NARROW", "LINED_WIDE" -> {
                val spacing = if (templateType == "LINED_NARROW") 24f else 36f
                var y = spacing * 2
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, paint)
                    y += spacing
                }
                // Margin line
                paint.color = android.graphics.Color.argb(60, 239, 68, 68)
                canvas.drawLine(70f, 0f, 70f, height, paint)
            }
            "GRID_SMALL", "GRID_LARGE" -> {
                val spacing = if (templateType == "GRID_SMALL") 20f else 32f
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
                val spacing = 28f
                paint.style = Paint.Style.FILL
                var x = spacing
                while (x < width) {
                    var y = spacing
                    while (y < height) {
                        canvas.drawCircle(x, y, 1.8f, paint)
                        y += spacing
                    }
                    x += spacing
                }
            }
            "CORNELL" -> {
                // Header line
                canvas.drawLine(0f, 120f, width, 120f, paint)
                // Left Cue column line
                canvas.drawLine(240f, 120f, 240f, height - 160f, paint)
                // Bottom Summary line
                canvas.drawLine(0f, height - 160f, width, height - 160f, paint)
                // Notes lines in main area
                var y = 156f
                while (y < height - 160f) {
                    canvas.drawLine(240f, y, width, y, paint)
                    y += 36f
                }
            }
            "MUSIC" -> {
                val staveSpacing = 8f
                var startY = 100f
                while (startY < height - 100f) {
                    for (i in 0..4) {
                        canvas.drawLine(40f, startY + i * staveSpacing, width - 40f, startY + i * staveSpacing, paint)
                    }
                    startY += 70f
                }
            }
        }
    }

    private fun drawStrokesOnCanvas(canvas: Canvas, strokes: List<VectorStroke>) {
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

            val path = android.graphics.Path()
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

    private fun drawShapesOnCanvas(canvas: Canvas, shapes: List<GeometricShape>) {
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
                    // Draw arrowhead
                    val angle = Math.atan2((shape.endY - shape.startY).toDouble(), (shape.endX - shape.startX).toDouble())
                    val arrowLength = 20.0
                    val arrowAngle = Math.toRadians(25.0)
                    val x1 = shape.endX - arrowLength * Math.cos(angle - arrowAngle)
                    val y1 = shape.endY - arrowLength * Math.sin(angle - arrowAngle)
                    val x2 = shape.endX - arrowLength * Math.cos(angle + arrowAngle)
                    val y2 = shape.endY - arrowLength * Math.sin(angle + arrowAngle)
                    val arrowPath = android.graphics.Path().apply {
                        moveTo(shape.endX, shape.endY)
                        lineTo(x1.toFloat(), y1.toFloat())
                        lineTo(x2.toFloat(), y2.toFloat())
                        close()
                    }
                    strokePaint.style = Paint.Style.FILL_AND_STROKE
                    canvas.drawPath(arrowPath, strokePaint)
                }
                ShapeType.RECTANGLE, ShapeType.SQUARE -> {
                    val rect = android.graphics.RectF(shape.left, shape.top, shape.left + shape.width, shape.top + shape.height)
                    if (shape.isFilled) canvas.drawRect(rect, fillPaint)
                    canvas.drawRect(rect, strokePaint)
                }
                ShapeType.CIRCLE, ShapeType.ELLIPSE -> {
                    val rect = android.graphics.RectF(shape.left, shape.top, shape.left + shape.width, shape.top + shape.height)
                    if (shape.isFilled) canvas.drawOval(rect, fillPaint)
                    canvas.drawOval(rect, strokePaint)
                }
                ShapeType.TRIANGLE -> {
                    val path = android.graphics.Path().apply {
                        moveTo(shape.centerX, shape.top)
                        lineTo(shape.left, shape.top + shape.height)
                        lineTo(shape.left + shape.width, shape.top + shape.height)
                        close()
                    }
                    if (shape.isFilled) canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, strokePaint)
                }
                ShapeType.POLYGON, ShapeType.STAR -> {
                    val path = android.graphics.Path()
                    val cx = shape.centerX
                    val cy = shape.centerY
                    val r = shape.width / 2f
                    val sides = shape.sideCount.coerceAtLeast(3)
                    for (i in 0 until sides) {
                        val theta = (i * 2 * Math.PI / sides) - Math.PI / 2
                        val px = (cx + r * Math.cos(theta)).toFloat()
                        val py = (cy + r * Math.sin(theta)).toFloat()
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

    private fun drawTextItemsOnCanvas(canvas: Canvas, items: List<CanvasTextItem>) {
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

            if (item.backgroundColor != androidx.compose.ui.graphics.Color.Transparent) {
                val bgPaint = Paint().apply {
                    color = item.backgroundColor.toArgb()
                    style = Paint.Style.FILL
                }
                canvas.drawRect(item.x, item.y, item.x + item.width, item.y + item.height, bgPaint)
            }

            val lines = item.text.split("\n")
            var lineY = item.y + item.fontSize * 1.8f
            lines.forEach { line ->
                canvas.drawText(line, item.x + 8f, lineY, paint)
                lineY += item.fontSize * 2.2f
            }
        }
    }

    private fun drawMediaItemsOnCanvas(canvas: Canvas, items: List<CanvasMediaItem>) {
        items.forEach { item ->
            if (item.localUri.isNotBlank()) {
                val file = File(item.localUri)
                if (file.exists()) {
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                    val bitmap = try {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                    } catch (e: Exception) {
                        null
                    }
                    if (bitmap != null) {
                        canvas.save()
                        val cx = item.x + item.width / 2f
                        val cy = item.y + item.height / 2f
                        if (item.rotationDegrees != 0f) {
                            canvas.rotate(item.rotationDegrees, cx, cy)
                        }
                        val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                        val dst = android.graphics.RectF(item.x, item.y, item.x + item.width, item.y + item.height)
                        canvas.drawBitmap(bitmap, src, dst, null)
                        canvas.restore()
                        bitmap.recycle()
                    }
                }
            }
        }
    }
}
