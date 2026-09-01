package com.example.inknote.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.inknote.data.db.AppDatabase
import com.example.inknote.data.db.entities.*
import com.example.inknote.data.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NoteRepository(private val database: AppDatabase) {

    fun getSubjects(userId: String): Flow<List<SubjectEntity>> =
        database.subjectDao().getAllSubjects(userId)

    fun getNotebooks(userId: String): Flow<List<NotebookEntity>> =
        database.notebookDao().getAllNotebooks(userId)

    fun getFavoriteNotebooks(userId: String): Flow<List<NotebookEntity>> =
        database.notebookDao().getFavoriteNotebooks(userId)

    fun getNotebooksBySubject(userId: String, subjectId: Long): Flow<List<NotebookEntity>> =
        database.notebookDao().getNotebooksBySubject(userId, subjectId)

    fun getNotebookFlow(userId: String, notebookId: Long): Flow<NotebookEntity?> =
        database.notebookDao().getNotebookFlowByIdForUser(userId, notebookId)

    fun getNotebookFlow(notebookId: Long): Flow<NotebookEntity?> =
        database.notebookDao().getNotebookFlowById(notebookId)

    suspend fun getNotebook(notebookId: Long): NotebookEntity? =
        database.notebookDao().getNotebookById(notebookId)

    suspend fun getNotebook(userId: String, notebookId: Long): NotebookEntity? =
        database.notebookDao().getNotebookByIdForUser(userId, notebookId)

    fun searchNotebooks(userId: String, query: String): Flow<List<NotebookEntity>> =
        database.notebookDao().searchNotebooks(userId, query)

    suspend fun createSubject(userId: String, name: String, colorHex: String): Long {
        return database.subjectDao().insertSubject(
            SubjectEntity(userId = userId, name = name, colorHex = colorHex)
        )
    }

    suspend fun updateSubject(subject: SubjectEntity) {
        database.subjectDao().updateSubject(subject)
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        database.subjectDao().deleteSubject(subject)
    }

    suspend fun createNotebook(
        userId: String,
        title: String,
        subjectId: Long? = null,
        coverColorHex: String = "#6366F1",
        templateType: String = "BLANK"
    ): Long {
        val now = System.currentTimeMillis()
        val notebookId = database.notebookDao().insertNotebook(
            NotebookEntity(
                userId = userId,
                subjectId = subjectId,
                title = title,
                coverColorHex = coverColorHex,
                createdAt = now,
                updatedAt = now,
                pageCount = 1
            )
        )
        // Create initial page
        database.pageDao().insertPage(
            PageEntity(
                userId = userId,
                notebookId = notebookId,
                pageNumber = 1,
                templateType = templateType
            )
        )
        return notebookId
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        database.notebookDao().updateNotebook(notebook.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) {
        database.notebookDao().deleteNotebook(notebook)
    }

    suspend fun toggleFavorite(notebookId: Long) {
        val nb = database.notebookDao().getNotebookById(notebookId) ?: return
        database.notebookDao().updateNotebook(nb.copy(isFavorite = !nb.isFavorite))
    }

    // Pages
    fun getPagesForNotebook(notebookId: Long): Flow<List<PageEntity>> =
        database.pageDao().getPagesForNotebook(notebookId)

    suspend fun getPagesList(notebookId: Long): List<PageEntity> =
        database.pageDao().getPagesListForNotebook(notebookId)

    suspend fun addPage(
        notebookId: Long,
        templateType: String = "BLANK",
        customBgPath: String? = null,
        pdfPageNum: Int? = null,
        userId: String = ""
    ): Long {
        val nb = database.notebookDao().getNotebookById(notebookId)
        val resolvedUserId = if (userId.isNotEmpty()) userId else (nb?.userId ?: "")
        val pages = database.pageDao().getPagesListForNotebook(notebookId)
        val nextNumber = (pages.maxOfOrNull { it.pageNumber } ?: 0) + 1
        val newPageId = database.pageDao().insertPage(
            PageEntity(
                userId = resolvedUserId,
                notebookId = notebookId,
                pageNumber = nextNumber,
                templateType = templateType,
                customBgPath = customBgPath,
                pdfPageNumber = pdfPageNum
            )
        )
        if (nb != null) {
            database.notebookDao().updateNotebook(nb.copy(pageCount = pages.size + 1, updatedAt = System.currentTimeMillis()))
        }
        return newPageId
    }

    suspend fun updatePage(page: PageEntity) {
        database.pageDao().updatePage(page.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePage(page: PageEntity) {
        database.pageDao().deletePage(page)
        val remaining = database.pageDao().getPagesListForNotebook(page.notebookId)
        val nb = database.notebookDao().getNotebookById(page.notebookId)
        if (nb != null) {
            database.notebookDao().updateNotebook(nb.copy(pageCount = remaining.size, updatedAt = System.currentTimeMillis()))
        }
    }

    // Strokes Flow & Batch Operations
    fun getStrokesForPage(pageId: Long): Flow<List<VectorStroke>> {
        return database.strokeDao().getStrokesForPage(pageId).map { entities ->
            entities.map { entity ->
                VectorStroke(
                    id = entity.id,
                    pageId = entity.pageId,
                    toolType = try { ToolType.valueOf(entity.toolType) } catch (e: Exception) { ToolType.PEN },
                    color = Color(entity.colorArgb.toInt()),
                    strokeWidth = entity.strokeWidth,
                    alpha = entity.alpha,
                    points = VectorStroke.parseSerializedPoints(entity.pointsJson),
                    audioTimestampMs = entity.audioTimestampMs
                )
            }
        }
    }

    suspend fun saveStrokesForPage(pageId: Long, strokes: List<VectorStroke>, userId: String = "") = withContext(Dispatchers.IO) {
        database.strokeDao().clearStrokesForPage(pageId)
        val resolvedUserId = if (userId.isNotEmpty()) {
            userId
        } else {
            database.pageDao().getPageById(pageId)?.userId ?: ""
        }
        val entities = strokes.mapIndexed { index, stroke ->
            StrokeEntity(
                userId = resolvedUserId,
                pageId = pageId,
                toolType = stroke.toolType.name,
                colorArgb = stroke.color.toArgb().toLong() and 0xFFFFFFFFL,
                strokeWidth = stroke.strokeWidth,
                alpha = stroke.alpha,
                pointsJson = stroke.toSerializedPoints(),
                audioTimestampMs = stroke.audioTimestampMs,
                orderIndex = index
            )
        }
        database.strokeDao().insertStrokes(entities)
    }

    // Shapes Flow & Batch Operations
    fun getShapesForPage(pageId: Long): Flow<List<GeometricShape>> {
        return database.shapeDao().getShapesForPage(pageId).map { entities ->
            entities.map { entity ->
                GeometricShape(
                    id = entity.id,
                    pageId = entity.pageId,
                    shapeType = try { ShapeType.valueOf(entity.shapeType) } catch (e: Exception) { ShapeType.RECTANGLE },
                    startX = entity.startX,
                    startY = entity.startY,
                    endX = entity.endX,
                    endY = entity.endY,
                    strokeColor = Color(entity.strokeColorArgb.toInt()),
                    fillColor = Color(entity.fillColorArgb.toInt()),
                    strokeWidth = entity.strokeWidth,
                    rotationDegrees = entity.rotationDegrees,
                    isFilled = entity.isFilled,
                    sideCount = entity.sideCount,
                    audioTimestampMs = entity.audioTimestampMs
                )
            }
        }
    }

    suspend fun saveShapesForPage(pageId: Long, shapes: List<GeometricShape>, userId: String = "") = withContext(Dispatchers.IO) {
        database.shapeDao().clearShapesForPage(pageId)
        val resolvedUserId = if (userId.isNotEmpty()) {
            userId
        } else {
            database.pageDao().getPageById(pageId)?.userId ?: ""
        }
        val entities = shapes.mapIndexed { index, shape ->
            ShapeEntity(
                userId = resolvedUserId,
                pageId = pageId,
                shapeType = shape.shapeType.name,
                startX = shape.startX,
                startY = shape.startY,
                endX = shape.endX,
                endY = shape.endY,
                strokeColorArgb = shape.strokeColor.toArgb().toLong() and 0xFFFFFFFFL,
                fillColorArgb = shape.fillColor.toArgb().toLong() and 0xFFFFFFFFL,
                strokeWidth = shape.strokeWidth,
                rotationDegrees = shape.rotationDegrees,
                isFilled = shape.isFilled,
                sideCount = shape.sideCount,
                audioTimestampMs = shape.audioTimestampMs,
                orderIndex = index
            )
        }
        database.shapeDao().insertShapes(entities)
    }

    // Text Items
    fun getTextItemsForPage(pageId: Long): Flow<List<CanvasTextItem>> {
        return database.textItemDao().getTextItemsForPage(pageId).map { entities ->
            entities.map { entity ->
                CanvasTextItem(
                    id = entity.id,
                    pageId = entity.pageId,
                    text = entity.text,
                    x = entity.x,
                    y = entity.y,
                    width = entity.width,
                    height = entity.height,
                    fontSize = entity.fontSize,
                    fontColor = Color(entity.fontColorArgb.toInt()),
                    isBold = entity.isBold,
                    isItalic = entity.isItalic,
                    backgroundColor = Color(entity.backgroundColorArgb.toInt()),
                    audioTimestampMs = entity.audioTimestampMs,
                    isMathFormula = entity.isMathFormula,
                    linkNotebookId = entity.linkNotebookId,
                    linkNotebookTitle = entity.linkNotebookTitle
                )
            }
        }
    }

    suspend fun saveTextItemsForPage(pageId: Long, items: List<CanvasTextItem>, userId: String = "") = withContext(Dispatchers.IO) {
        database.textItemDao().clearTextForPage(pageId)
        val resolvedUserId = if (userId.isNotEmpty()) {
            userId
        } else {
            database.pageDao().getPageById(pageId)?.userId ?: ""
        }
        items.forEach { item ->
            database.textItemDao().insertTextItem(
                TextItemEntity(
                    userId = resolvedUserId,
                    pageId = pageId,
                    text = item.text,
                    x = item.x,
                    y = item.y,
                    width = item.width,
                    height = item.height,
                    fontSize = item.fontSize,
                    fontColorArgb = item.fontColor.toArgb().toLong() and 0xFFFFFFFFL,
                    isBold = item.isBold,
                    isItalic = item.isItalic,
                    backgroundColorArgb = item.backgroundColor.toArgb().toLong() and 0xFFFFFFFFL,
                    audioTimestampMs = item.audioTimestampMs,
                    isMathFormula = item.isMathFormula,
                    linkNotebookId = item.linkNotebookId,
                    linkNotebookTitle = item.linkNotebookTitle
                )
            )
        }
    }

    // Media Items
    fun getMediaItemsForPage(pageId: Long): Flow<List<CanvasMediaItem>> {
        return database.mediaItemDao().getMediaItemsForPage(pageId).map { entities ->
            entities.map { entity ->
                CanvasMediaItem(
                    id = entity.id,
                    pageId = entity.pageId,
                    localUri = entity.localUri,
                    x = entity.x,
                    y = entity.y,
                    width = entity.width,
                    height = entity.height,
                    rotationDegrees = entity.rotationDegrees,
                    audioTimestampMs = entity.audioTimestampMs
                )
            }
        }
    }

    suspend fun saveMediaItemsForPage(pageId: Long, items: List<CanvasMediaItem>, userId: String = "") = withContext(Dispatchers.IO) {
        database.mediaItemDao().clearMediaForPage(pageId)
        val resolvedUserId = if (userId.isNotEmpty()) {
            userId
        } else {
            database.pageDao().getPageById(pageId)?.userId ?: ""
        }
        items.forEach { item ->
            database.mediaItemDao().insertMediaItem(
                MediaItemEntity(
                    userId = resolvedUserId,
                    pageId = pageId,
                    localUri = item.localUri,
                    x = item.x,
                    y = item.y,
                    width = item.width,
                    height = item.height,
                    rotationDegrees = item.rotationDegrees,
                    audioTimestampMs = item.audioTimestampMs
                )
            )
        }
    }

    // Audio Recordings
    fun getRecordingsForNotebook(notebookId: Long): Flow<List<AudioRecordingEntity>> =
        database.audioDao().getRecordingsForNotebook(notebookId)

    suspend fun saveRecording(recording: AudioRecordingEntity): Long =
        database.audioDao().insertRecording(recording)

    suspend fun deleteRecording(recording: AudioRecordingEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(recording.filePath)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        database.audioDao().deleteRecording(recording)
    }

    suspend fun renameRecording(recording: AudioRecordingEntity, newTitle: String) = withContext(Dispatchers.IO) {
        database.audioDao().insertRecording(recording.copy(title = newTitle))
    }

    suspend fun getStrokesListForExport(pageId: Long): List<VectorStroke> = withContext(Dispatchers.IO) {
        database.strokeDao().getStrokesListForPage(pageId).map { entity ->
            VectorStroke(
                id = entity.id,
                pageId = entity.pageId,
                toolType = try { ToolType.valueOf(entity.toolType) } catch (e: Exception) { ToolType.PEN },
                color = Color(entity.colorArgb.toInt()),
                strokeWidth = entity.strokeWidth,
                alpha = entity.alpha,
                points = VectorStroke.parseSerializedPoints(entity.pointsJson),
                audioTimestampMs = entity.audioTimestampMs
            )
        }
    }

    suspend fun getShapesListForExport(pageId: Long): List<GeometricShape> = withContext(Dispatchers.IO) {
        database.shapeDao().getShapesListForPage(pageId).map { entity ->
            GeometricShape(
                id = entity.id,
                pageId = entity.pageId,
                shapeType = try { ShapeType.valueOf(entity.shapeType) } catch (e: Exception) { ShapeType.RECTANGLE },
                startX = entity.startX,
                startY = entity.startY,
                endX = entity.endX,
                endY = entity.endY,
                strokeColor = Color(entity.strokeColorArgb.toInt()),
                fillColor = Color(entity.fillColorArgb.toInt()),
                strokeWidth = entity.strokeWidth,
                rotationDegrees = entity.rotationDegrees,
                isFilled = entity.isFilled,
                sideCount = entity.sideCount,
                audioTimestampMs = entity.audioTimestampMs
            )
        }
    }

    suspend fun getTextListForExport(pageId: Long): List<CanvasTextItem> = withContext(Dispatchers.IO) {
        database.textItemDao().getTextItemsListForPage(pageId).map { entity ->
            CanvasTextItem(
                id = entity.id,
                pageId = entity.pageId,
                text = entity.text,
                x = entity.x,
                y = entity.y,
                width = entity.width,
                height = entity.height,
                fontSize = entity.fontSize,
                fontColor = Color(entity.fontColorArgb.toInt()),
                isBold = entity.isBold,
                isItalic = entity.isItalic,
                backgroundColor = Color(entity.backgroundColorArgb.toInt()),
                audioTimestampMs = entity.audioTimestampMs,
                isMathFormula = entity.isMathFormula,
                linkNotebookId = entity.linkNotebookId,
                linkNotebookTitle = entity.linkNotebookTitle
            )
        }
    }

    suspend fun getMediaListForExport(pageId: Long): List<CanvasMediaItem> = withContext(Dispatchers.IO) {
        database.mediaItemDao().getMediaItemsListForPage(pageId).map { entity ->
            CanvasMediaItem(
                id = entity.id,
                pageId = entity.pageId,
                localUri = entity.localUri,
                x = entity.x,
                y = entity.y,
                width = entity.width,
                height = entity.height,
                rotationDegrees = entity.rotationDegrees,
                audioTimestampMs = entity.audioTimestampMs
            )
        }
    }

    suspend fun searchAllText(userId: String, query: String): List<TextItemEntity> =
        database.textItemDao().searchAllText(userId, query)

    // Snapshots / Version History
    fun getSnapshotsForPage(pageId: Long): Flow<List<PageSnapshotEntity>> =
        database.pageSnapshotDao().getSnapshotsForPage(pageId)

    suspend fun savePageSnapshot(
        pageId: Long,
        notebookId: Long,
        title: String,
        strokesJson: String,
        shapesJson: String,
        textJson: String,
        userId: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val resolvedUserId = if (userId.isNotEmpty()) {
            userId
        } else {
            database.pageDao().getPageById(pageId)?.userId ?: ""
        }
        database.pageSnapshotDao().insertSnapshot(
            PageSnapshotEntity(
                userId = resolvedUserId,
                pageId = pageId,
                notebookId = notebookId,
                title = title,
                timestamp = System.currentTimeMillis(),
                strokesJson = strokesJson,
                shapesJson = shapesJson,
                textJson = textJson
            )
        )
    }

    suspend fun deleteSnapshot(snapshot: PageSnapshotEntity) = withContext(Dispatchers.IO) {
        database.pageSnapshotDao().deleteSnapshot(snapshot)
    }

    // Flashcards & Study Tools
    fun getFlashcardsForNotebook(notebookId: Long): Flow<List<FlashcardEntity>> =
        database.flashcardDao().getFlashcardsForNotebook(notebookId)

    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>) = withContext(Dispatchers.IO) {
        database.flashcardDao().insertFlashcards(flashcards)
    }

    suspend fun updateFlashcard(flashcard: FlashcardEntity) = withContext(Dispatchers.IO) {
        database.flashcardDao().updateFlashcard(flashcard)
    }

    suspend fun deleteFlashcard(flashcard: FlashcardEntity) = withContext(Dispatchers.IO) {
        database.flashcardDao().deleteFlashcard(flashcard)
    }

    // Security & Tags
    suspend fun updateNotebookSecurity(notebookId: Long, isLocked: Boolean, pinCode: String?) = withContext(Dispatchers.IO) {
        val nb = database.notebookDao().getNotebookById(notebookId) ?: return@withContext
        database.notebookDao().updateNotebook(nb.copy(isLocked = isLocked, pinCode = pinCode, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateNotebookTags(notebookId: Long, tags: String) = withContext(Dispatchers.IO) {
        val nb = database.notebookDao().getNotebookById(notebookId) ?: return@withContext
        database.notebookDao().updateNotebook(nb.copy(tags = tags, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updatePageTags(pageId: Long, tags: String) = withContext(Dispatchers.IO) {
        val p = database.pageDao().getPageById(pageId) ?: return@withContext
        database.pageDao().updatePage(p.copy(tags = tags, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updatePageSize(pageId: Long, pageSize: String, width: Float, height: Float) = withContext(Dispatchers.IO) {
        val p = database.pageDao().getPageById(pageId) ?: return@withContext
        database.pageDao().updatePage(p.copy(pageSize = pageSize, width = width, height = height, updatedAt = System.currentTimeMillis()))
    }

    suspend fun initializeDefaultDataForUser(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = database.notebookDao().getAllNotebooksList(userId)
        if (existing.isNotEmpty()) return@withContext

        val sub1Id = database.subjectDao().insertSubject(
            SubjectEntity(userId = userId, name = "Personal & Ideas", colorHex = "#6366F1", sortOrder = 0)
        )
        database.subjectDao().insertSubject(
            SubjectEntity(userId = userId, name = "Work & Projects", colorHex = "#EC4899", sortOrder = 1)
        )
        database.subjectDao().insertSubject(
            SubjectEntity(userId = userId, name = "Math & Sketches", colorHex = "#10B981", sortOrder = 2)
        )

        val nbId = database.notebookDao().insertNotebook(
            NotebookEntity(
                userId = userId,
                subjectId = sub1Id,
                title = "Welcome to InkNote ✍️",
                coverColorHex = "#6366F1",
                isFavorite = true,
                pageCount = 2
            )
        )

        val page1Id = database.pageDao().insertPage(
            PageEntity(
                userId = userId,
                notebookId = nbId,
                pageNumber = 1,
                templateType = "BLANK"
            )
        )

        database.pageDao().insertPage(
            PageEntity(
                userId = userId,
                notebookId = nbId,
                pageNumber = 2,
                templateType = "BLANK"
            )
        )

        database.textItemDao().insertTextItem(
            TextItemEntity(
                userId = userId,
                pageId = page1Id,
                text = "Welcome to InkNote!\n\n• Smooth stylus & finger vector ink\n• Geometric tools: Ruler, Protractor, Compass & Shape snap\n• Synced Audio Recording & Scrubbing\n• PDF & Image Annotations\n• 100% Cloud Synced & Private to your account",
                x = 80f,
                y = 120f,
                width = 650f,
                height = 220f,
                fontSize = 18f,
                fontColorArgb = 0xFF1E293BL,
                isBold = false,
                backgroundColorArgb = 0x156366F1L
            )
        )

        database.shapeDao().insertShape(
            ShapeEntity(
                userId = userId,
                pageId = page1Id,
                shapeType = "RECTANGLE",
                startX = 60f,
                startY = 100f,
                endX = 760f,
                endY = 360f,
                strokeColorArgb = 0xFF6366F1L,
                fillColorArgb = 0x086366F1L,
                strokeWidth = 3f,
                isFilled = true
            )
        )
    }

    companion object {
        @Volatile
        private var instance: NoteRepository? = null

        fun getInstance(context: Context): NoteRepository {
            return instance ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                NoteRepository(db).also { instance = it }
            }
        }
    }
}
