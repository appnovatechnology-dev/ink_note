package com.example.inknote.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inknote.audio.AudioPlayerManager
import com.example.inknote.audio.AudioRecorderManager
import com.example.inknote.audio.TranscriptionManager
import com.example.inknote.audio.TranscriptionResult
import com.example.inknote.data.ai.*
import com.example.inknote.data.auth.AuthRepository
import com.example.inknote.data.db.entities.AudioRecordingEntity
import com.example.inknote.data.db.entities.FlashcardEntity
import com.example.inknote.data.db.entities.NotebookEntity
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.db.entities.PageSnapshotEntity
import com.example.inknote.data.model.*
import com.example.inknote.data.repository.NoteRepository
import com.example.inknote.geometry.GeometryEngine
import com.example.inknote.pdf.PdfManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class CanvasActionState(
    val strokes: List<VectorStroke>,
    val shapes: List<GeometricShape>,
    val textItems: List<CanvasTextItem>,
    val mediaItems: List<CanvasMediaItem>
)

data class PageDataState(
    val strokes: List<VectorStroke> = emptyList(),
    val shapes: List<GeometricShape> = emptyList(),
    val textItems: List<CanvasTextItem> = emptyList(),
    val mediaItems: List<CanvasMediaItem> = emptyList()
)

enum class SaveStatus {
    SAVED,
    SAVING,
    ERROR
}

data class NoteCanvasUiState(
    val notebook: NotebookEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val currentPageIndex: Int = 0,
    val pagesData: Map<Long, PageDataState> = emptyMap(),
    val strokes: List<VectorStroke> = emptyList(),
    val shapes: List<GeometricShape> = emptyList(),
    val textItems: List<CanvasTextItem> = emptyList(),
    val mediaItems: List<CanvasMediaItem> = emptyList(),
    val recordings: List<AudioRecordingEntity> = emptyList(),

    // Continuous Auto-Save Status
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val lastSavedTimeMillis: Long = System.currentTimeMillis(),
    val saveErrorMessage: String? = null,

    // Tool Settings
    val activeTool: ToolType = ToolType.PEN,
    val penColor: Color = Color(0xFF0F172A),
    val penWidth: Float = 3.5f,
    val highlighterColor: Color = Color(0xFFFACC15),
    val highlighterWidth: Float = 14f,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserWidth: Float = 24f,
    val isMagicInkEnabled: Boolean = false,
    val isGridSnapEnabled: Boolean = false,
    val isPalmRejectionEnabled: Boolean = true,
    val isContinuousScroll: Boolean = true,

    // Geometric Tools
    val rulerState: RulerState = RulerState(),
    val protractorState: ProtractorState = ProtractorState(),
    val compassState: CompassState = CompassState(),
    val activeShapeType: ShapeType = ShapeType.RECTANGLE,
    val shapeStrokeColor: Color = Color(0xFF4F46E5),
    val shapeFillColor: Color = Color.Transparent,
    val shapeStrokeWidth: Float = 3.5f,
    val shapeIsFilled: Boolean = false,
    val shapeSideCount: Int = 5,

    // Lasso Selection
    val isLassoSelecting: Boolean = false,
    val lassoPoints: List<Offset> = emptyList(),
    val hasSelection: Boolean = false,
    val lassoSelectedStrokes: List<VectorStroke> = emptyList(),
    val lassoSelectedShapes: List<GeometricShape> = emptyList(),
    val lassoSelectedTextItems: List<CanvasTextItem> = emptyList(),
    val lassoSelectionBounds: Rect? = null,

    // Audio State
    val isRecordingAudio: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val isPlayingAudio: Boolean = false,
    val playbackCurrentMs: Long = 0L,
    val playbackTotalMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeRecordingId: Long? = null,

    // Zoom & Viewport
    val zoomScale: Float = 1.0f,
    val panOffset: Offset = Offset.Zero,

    // Undo / Redo availability
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,

    // UI Dialogs
    val showPageTemplateDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val showAudioListDialog: Boolean = false,
    val showTranscriptionDialog: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcriptionResult: TranscriptionResult? = null,
    val isExporting: Boolean = false,
    val showShapePropertyDialog: Boolean = false,

    // Advanced Smart Features
    val flashcards: List<FlashcardEntity> = emptyList(),
    val snapshots: List<PageSnapshotEntity> = emptyList(),
    val quizQuestions: List<QuizQuestion> = emptyList(),
    val noteSummary: NoteSummaryResult? = null,
    val showFlashcardsModal: Boolean = false,
    val showQuizModal: Boolean = false,
    val showSummaryModal: Boolean = false,
    val showSnapshotsModal: Boolean = false,
    val showSecurityModal: Boolean = false,
    val showNotebookLinkDialog: Boolean = false,
    val isAiProcessing: Boolean = false,
    val aiErrorMessage: String? = null,
    val searchResults: List<Pair<Int, String>> = emptyList(),
    val currentSearchQuery: String = ""
)

enum class EraserMode {
    STROKE,
    AREA
}

class NoteCanvasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository.getInstance(application)
    private val authRepository = AuthRepository.getInstance(application)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recorderManager = AudioRecorderManager(application)
    val playerManager = AudioPlayerManager()
    val transcriptionManager = TranscriptionManager(application)
    val aiSmartEngine = AiSmartToolsEngine(application)

    private val _uiState = MutableStateFlow(NoteCanvasUiState())
    val uiState: StateFlow<NoteCanvasUiState> = _uiState.asStateFlow()

    private var currentNotebookId: Long = 0L
    private val undoStack = mutableListOf<CanvasActionState>()
    private val redoStack = mutableListOf<CanvasActionState>()

    private var autoSaveJob: Job? = null

    init {
        // Observe recorder state
        viewModelScope.launch {
            recorderManager.recordingState.collect { isRec ->
                _uiState.update { it.copy(isRecordingAudio = isRec) }
            }
        }
        viewModelScope.launch {
            recorderManager.elapsedMillis.collect { elapsed ->
                _uiState.update { it.copy(recordingElapsedMs = elapsed) }
            }
        }

        // Observe player state
        viewModelScope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlayingAudio = isPlaying) }
            }
        }
        viewModelScope.launch {
            playerManager.currentPosition.collect { pos ->
                _uiState.update { it.copy(playbackCurrentMs = pos) }
            }
        }
        viewModelScope.launch {
            playerManager.duration.collect { dur ->
                _uiState.update { it.copy(playbackTotalMs = dur) }
            }
        }
        viewModelScope.launch {
            playerManager.playbackSpeed.collect { sp ->
                _uiState.update { it.copy(playbackSpeed = sp) }
            }
        }
    }

    fun initNotebook(notebookId: Long, initialPageIndex: Int = 0) {
        currentNotebookId = notebookId
        viewModelScope.launch {
            val nb = repository.getNotebook(notebookId)
            val pages = repository.getPagesList(notebookId)
            val validIndex = initialPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

            _uiState.update {
                it.copy(
                    notebook = nb,
                    pages = pages,
                    currentPageIndex = validIndex
                )
            }

            // Pre-load data for all pages into cache map for instant 60fps scrolling
            val dataMap = mutableMapOf<Long, PageDataState>()
            pages.forEach { page ->
                val strokes = repository.getStrokesListForExport(page.id)
                val shapes = repository.getShapesListForExport(page.id)
                val texts = repository.getTextListForExport(page.id)
                val medias = repository.getMediaListForExport(page.id)
                dataMap[page.id] = PageDataState(strokes, shapes, texts, medias)
            }

            val activePage = pages.getOrNull(validIndex)
            val activeData = if (activePage != null) dataMap[activePage.id] ?: PageDataState() else PageDataState()

            undoStack.clear()
            redoStack.clear()

            _uiState.update {
                it.copy(
                    pagesData = dataMap,
                    strokes = activeData.strokes,
                    shapes = activeData.shapes,
                    textItems = activeData.textItems,
                    mediaItems = activeData.mediaItems,
                    canUndo = false,
                    canRedo = false
                )
            }

            loadRecordings(notebookId)
            loadFlashcards(notebookId)
            if (activePage != null) {
                loadSnapshots(activePage.id)
            }
        }
    }

    private fun loadRecordings(notebookId: Long) {
        viewModelScope.launch {
            repository.getRecordingsForNotebook(notebookId).collect { list ->
                _uiState.update { it.copy(recordings = list) }
            }
        }
    }

    private fun loadFlashcards(notebookId: Long) {
        viewModelScope.launch {
            repository.getFlashcardsForNotebook(notebookId).collect { cards ->
                _uiState.update { it.copy(flashcards = cards) }
            }
        }
    }

    private fun loadSnapshots(pageId: Long) {
        viewModelScope.launch {
            repository.getSnapshotsForPage(pageId).collect { snaps ->
                _uiState.update { it.copy(snapshots = snaps) }
            }
        }
    }

    fun loadPageData(pageId: Long) {
        viewModelScope.launch {
            val cached = _uiState.value.pagesData[pageId]
            if (cached != null) {
                undoStack.clear()
                redoStack.clear()
                _uiState.update {
                    it.copy(
                        strokes = cached.strokes,
                        shapes = cached.shapes,
                        textItems = cached.textItems,
                        mediaItems = cached.mediaItems,
                        canUndo = false,
                        canRedo = false
                    )
                }
            } else {
                val strokes = repository.getStrokesListForExport(pageId)
                val shapes = repository.getShapesListForExport(pageId)
                val texts = repository.getTextListForExport(pageId)
                val medias = repository.getMediaListForExport(pageId)
                val pageData = PageDataState(strokes, shapes, texts, medias)

                undoStack.clear()
                redoStack.clear()

                _uiState.update {
                    it.copy(
                        pagesData = it.pagesData + (pageId to pageData),
                        strokes = strokes,
                        shapes = shapes,
                        textItems = texts,
                        mediaItems = medias,
                        canUndo = false,
                        canRedo = false
                    )
                }
            }
            loadSnapshots(pageId)
        }
    }

    val currentPage: PageEntity?
        get() {
            val state = _uiState.value
            return state.pages.getOrNull(state.currentPageIndex)
        }

    fun setCurrentPageIndex(index: Int) {
        val pages = _uiState.value.pages
        if (index in pages.indices && index != _uiState.value.currentPageIndex) {
            saveCurrentPageImmediately()
            val targetPage = pages[index]
            _uiState.update { it.copy(currentPageIndex = index) }
            loadPageData(targetPage.id)
        }
    }

    fun toggleContinuousScroll() {
        _uiState.update { it.copy(isContinuousScroll = !it.isContinuousScroll) }
    }

    fun addNewPage(templateType: String = "BLANK") {
        viewModelScope.launch {
            saveCurrentPageImmediately()
            repository.addPage(currentNotebookId, templateType)
            val updatedPages = repository.getPagesList(currentNotebookId)
            val newIndex = updatedPages.size - 1
            val newPage = updatedPages[newIndex]

            _uiState.update {
                it.copy(
                    pages = updatedPages,
                    currentPageIndex = newIndex,
                    pagesData = it.pagesData + (newPage.id to PageDataState()),
                    strokes = emptyList(),
                    shapes = emptyList(),
                    textItems = emptyList(),
                    mediaItems = emptyList()
                )
            }
        }
    }

    fun deleteCurrentPage() {
        val page = currentPage ?: return
        if (_uiState.value.pages.size <= 1) return
        viewModelScope.launch {
            repository.deletePage(page)
            val updatedPages = repository.getPagesList(currentNotebookId)
            val newIndex = (_uiState.value.currentPageIndex - 1).coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    pages = updatedPages,
                    currentPageIndex = newIndex
                )
            }
            if (updatedPages.isNotEmpty()) {
                loadPageData(updatedPages[newIndex].id)
            }
        }
    }

    fun setPageTemplate(templateType: String) {
        val page = currentPage ?: return
        viewModelScope.launch {
            val updated = page.copy(templateType = templateType)
            repository.updatePage(updated)
            val updatedPages = repository.getPagesList(currentNotebookId)
            _uiState.update {
                it.copy(
                    pages = updatedPages,
                    showPageTemplateDialog = false
                )
            }
        }
    }

    // Tools & Properties
    fun selectTool(tool: ToolType) {
        _uiState.update { it.copy(activeTool = tool, hasSelection = false, lassoSelectedStrokes = emptyList(), lassoSelectionBounds = null) }
    }

    fun setPenColor(color: Color) {
        _uiState.update { it.copy(penColor = color) }
    }

    fun setPenWidth(width: Float) {
        _uiState.update { it.copy(penWidth = width) }
    }

    fun setHighlighterColor(color: Color) {
        _uiState.update { it.copy(highlighterColor = color) }
    }

    fun setHighlighterWidth(width: Float) {
        _uiState.update { it.copy(highlighterWidth = width) }
    }

    fun setEraserMode(mode: EraserMode) {
        _uiState.update { it.copy(eraserMode = mode) }
    }

    fun toggleMagicInk() {
        _uiState.update { it.copy(isMagicInkEnabled = !it.isMagicInkEnabled) }
    }

    fun toggleGridSnap() {
        _uiState.update { it.copy(isGridSnapEnabled = !it.isGridSnapEnabled) }
    }

    fun togglePalmRejection() {
        _uiState.update { it.copy(isPalmRejectionEnabled = !it.isPalmRejectionEnabled) }
    }

    // Ruler / Protractor / Compass
    fun toggleRuler() {
        _uiState.update { it.copy(rulerState = it.rulerState.copy(isVisible = !it.rulerState.isVisible)) }
    }

    fun updateRuler(centerX: Float, centerY: Float, angleDeg: Float) {
        _uiState.update { it.copy(rulerState = it.rulerState.copy(centerX = centerX, centerY = centerY, angleDegrees = angleDeg)) }
    }

    fun toggleProtractor() {
        _uiState.update { it.copy(protractorState = it.protractorState.copy(isVisible = !it.protractorState.isVisible)) }
    }

    fun updateProtractor(centerX: Float, centerY: Float, rotationDeg: Float) {
        _uiState.update { it.copy(protractorState = it.protractorState.copy(centerX = centerX, centerY = centerY, rotationDegrees = rotationDeg)) }
    }

    fun toggleCompass() {
        _uiState.update { it.copy(compassState = it.compassState.copy(isVisible = !it.compassState.isVisible)) }
    }

    fun updateCompass(pivotX: Float, pivotY: Float, radius: Float, angle: Float) {
        _uiState.update { it.copy(compassState = it.compassState.copy(pivotX = pivotX, pivotY = pivotY, radius = radius, currentAngleDegrees = angle)) }
    }

    fun setShapeType(shapeType: ShapeType) {
        _uiState.update { it.copy(activeShapeType = shapeType) }
    }

    fun setShapeProperties(strokeColor: Color, fillColor: Color, strokeWidth: Float, isFilled: Boolean, sideCount: Int) {
        _uiState.update {
            it.copy(
                shapeStrokeColor = strokeColor,
                shapeFillColor = fillColor,
                shapeStrokeWidth = strokeWidth,
                shapeIsFilled = isFilled,
                shapeSideCount = sideCount,
                showShapePropertyDialog = false
            )
        }
    }

    fun showShapeProperties(show: Boolean) {
        _uiState.update { it.copy(showShapePropertyDialog = show) }
    }

    // Drawing Operations
    fun addStroke(stroke: VectorStroke) {
        val pageId = currentPage?.id ?: return
        addStrokeToPage(pageId, stroke)
    }

    fun addStrokeToPage(pageId: Long, stroke: VectorStroke) {
        pushUndoSnapshot()
        val currentAudioMs = if (_uiState.value.isRecordingAudio) recorderManager.getCurrentElapsedMs() else null
        val finalStroke = stroke.copy(pageId = pageId, audioTimestampMs = currentAudioMs)

        if (_uiState.value.isMagicInkEnabled) {
            val recognizedShape = GeometryEngine.recognizeShape(
                points = stroke.points,
                strokeColor = stroke.color,
                strokeWidth = stroke.strokeWidth
            )
            if (recognizedShape != null) {
                val finalShape = recognizedShape.copy(
                    pageId = pageId,
                    audioTimestampMs = currentAudioMs
                )
                val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
                val newShapes = currentData.shapes + finalShape
                updatePageData(pageId, shapes = newShapes)
                scheduleAutoSaveForPage(pageId)
                return
            }
        }

        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newStrokes = currentData.strokes + finalStroke
        updatePageData(pageId, strokes = newStrokes)
        scheduleAutoSaveForPage(pageId)
    }

    fun addShape(shape: GeometricShape) {
        val pageId = currentPage?.id ?: return
        addShapeToPage(pageId, shape)
    }

    fun addShapeToPage(pageId: Long, shape: GeometricShape) {
        pushUndoSnapshot()
        val currentAudioMs = if (_uiState.value.isRecordingAudio) recorderManager.getCurrentElapsedMs() else null
        val finalShape = shape.copy(pageId = pageId, audioTimestampMs = currentAudioMs)
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newShapes = currentData.shapes + finalShape
        updatePageData(pageId, shapes = newShapes)
        scheduleAutoSaveForPage(pageId)
    }

    fun eraseAt(point: Offset, radius: Float = 24f) {
        val pageId = currentPage?.id ?: return
        eraseAtPage(pageId, point, radius)
    }

    fun eraseAtPage(pageId: Long, point: Offset, radius: Float = 24f) {
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val currentStrokes = currentData.strokes
        val currentShapes = currentData.shapes

        val remainingStrokes = currentStrokes.filterNot { stroke ->
            stroke.points.any { p ->
                hypot((p.x - point.x).toDouble(), (p.y - point.y).toDouble()) < (radius + stroke.strokeWidth / 2f)
            }
        }

        val remainingShapes = currentShapes.filterNot { shape ->
            val cx = shape.centerX
            val cy = shape.centerY
            hypot((cx - point.x).toDouble(), (cy - point.y).toDouble()) < (radius + shape.width / 2f)
        }

        if (remainingStrokes.size != currentStrokes.size || remainingShapes.size != currentShapes.size) {
            pushUndoSnapshot()
            updatePageData(pageId, strokes = remainingStrokes, shapes = remainingShapes)
            scheduleAutoSaveForPage(pageId)
        }
    }

    // Lasso Selection
    fun handleLassoSelection(lassoPoints: List<Offset>) {
        if (lassoPoints.size < 3) {
            clearLassoSelection()
            return
        }

        var polyMinX = Float.MAX_VALUE
        var polyMaxX = Float.MIN_VALUE
        var polyMinY = Float.MAX_VALUE
        var polyMaxY = Float.MIN_VALUE

        lassoPoints.forEach { pt ->
            polyMinX = min(polyMinX, pt.x)
            polyMaxX = max(polyMaxX, pt.x)
            polyMinY = min(polyMinY, pt.y)
            polyMaxY = max(polyMaxY, pt.y)
        }

        val polyBounds = Rect(polyMinX, polyMinY, polyMaxX, polyMaxY)

        val selectedStrokes = _uiState.value.strokes.filter { stroke ->
            GeometryEngine.isStrokeSelectedByLasso(stroke, lassoPoints, polyBounds)
        }
        val selectedShapes = _uiState.value.shapes.filter { shape ->
            GeometryEngine.isShapeSelectedByLasso(shape, lassoPoints, polyBounds)
        }
        val selectedTextItems = _uiState.value.textItems.filter { item ->
            GeometryEngine.isTextSelectedByLasso(item, lassoPoints, polyBounds)
        }

        val hasAny = selectedStrokes.isNotEmpty() || selectedShapes.isNotEmpty() || selectedTextItems.isNotEmpty()

        if (!hasAny) {
            clearLassoSelection()
            return
        }

        var bMinX = Float.MAX_VALUE
        var bMaxX = Float.MIN_VALUE
        var bMinY = Float.MAX_VALUE
        var bMaxY = Float.MIN_VALUE

        selectedStrokes.forEach { s ->
            s.points.forEach { p ->
                bMinX = min(bMinX, p.x)
                bMaxX = max(bMaxX, p.x)
                bMinY = min(bMinY, p.y)
                bMaxY = max(bMaxY, p.y)
            }
        }
        selectedShapes.forEach { s ->
            bMinX = min(bMinX, min(s.startX, s.endX))
            bMaxX = max(bMaxX, max(s.startX, s.endX))
            bMinY = min(bMinY, min(s.startY, s.endY))
            bMaxY = max(bMaxY, max(s.startY, s.endY))
        }
        selectedTextItems.forEach { t ->
            bMinX = min(bMinX, t.x)
            bMaxX = max(bMaxX, t.x + t.width)
            bMinY = min(bMinY, t.y)
            bMaxY = max(bMaxY, t.y + 60f)
        }

        val padding = 12f
        val finalBounds = Rect(
            (bMinX - padding).coerceAtLeast(0f),
            (bMinY - padding).coerceAtLeast(0f),
            (bMaxX + padding).coerceAtMost(1000f),
            (bMaxY + padding).coerceAtMost(1414f)
        )

        _uiState.update {
            it.copy(
                hasSelection = true,
                lassoSelectedStrokes = selectedStrokes,
                lassoSelectedShapes = selectedShapes,
                lassoSelectedTextItems = selectedTextItems,
                lassoSelectionBounds = finalBounds
            )
        }
    }

    fun startMovingLassoSelection() {
        // Drag started; lightweight visual preview is handled in UI layer
    }

    /**
     * Commits the final moved position for the selected lasso items.
     * Genuinely shifts each selected object's coordinates without deleting or recreating strokes.
     */
    fun commitLassoMove(deltaX: Float, deltaY: Float) {
        if (deltaX == 0f && deltaY == 0f) return

        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTextItems = _uiState.value.lassoSelectedTextItems
        val bounds = _uiState.value.lassoSelectionBounds ?: return
        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTextItems.isEmpty()) return

        pushUndoSnapshot()

        val selectedStrokeIds = selectedStrokes.map { it.id }.toSet()
        val selectedShapeIds = selectedShapes.map { it.id }.toSet()
        val selectedTextIds = selectedTextItems.map { it.id }.toSet()

        val movedStrokesList = mutableListOf<VectorStroke>()
        val newStrokes = _uiState.value.strokes.map { stroke ->
            if (selectedStrokeIds.contains(stroke.id)) {
                val moved = stroke.copy(
                    points = stroke.points.map { p -> p.copy(x = p.x + deltaX, y = p.y + deltaY) }
                )
                movedStrokesList.add(moved)
                moved
            } else {
                stroke
            }
        }

        val movedShapesList = mutableListOf<GeometricShape>()
        val newShapes = _uiState.value.shapes.map { shape ->
            if (selectedShapeIds.contains(shape.id)) {
                val moved = shape.copy(
                    startX = shape.startX + deltaX,
                    startY = shape.startY + deltaY,
                    endX = shape.endX + deltaX,
                    endY = shape.endY + deltaY
                )
                movedShapesList.add(moved)
                moved
            } else {
                shape
            }
        }

        val movedTextsList = mutableListOf<CanvasTextItem>()
        val newTexts = _uiState.value.textItems.map { text ->
            if (selectedTextIds.contains(text.id)) {
                val moved = text.copy(
                    x = text.x + deltaX,
                    y = text.y + deltaY
                )
                movedTextsList.add(moved)
                moved
            } else {
                text
            }
        }

        val newBounds = Rect(
            bounds.left + deltaX,
            bounds.top + deltaY,
            bounds.right + deltaX,
            bounds.bottom + deltaY
        )

        updateActivePageData(strokes = newStrokes, shapes = newShapes, textItems = newTexts)
        _uiState.update {
            it.copy(
                hasSelection = true,
                lassoSelectedStrokes = movedStrokesList,
                lassoSelectedShapes = movedShapesList,
                lassoSelectedTextItems = movedTextsList,
                lassoSelectionBounds = newBounds
            )
        }
        scheduleAutoSave()
    }

    fun moveLassoSelection(deltaX: Float, deltaY: Float) {
        commitLassoMove(deltaX, deltaY)
    }

    fun finishMovingLassoSelection() {
        scheduleAutoSave()
    }

    fun clearLassoSelection() {
        _uiState.update {
            it.copy(
                hasSelection = false,
                lassoSelectedStrokes = emptyList(),
                lassoSelectedShapes = emptyList(),
                lassoSelectedTextItems = emptyList(),
                lassoSelectionBounds = null
            )
        }
    }

    fun convertLassoToText() {
        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val bounds = _uiState.value.lassoSelectionBounds ?: return
        if (selectedStrokes.isEmpty()) return

        val selectedStrokeIds = selectedStrokes.map { it.id }.toSet()

        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            val result = aiSmartEngine.convertHandwritingToText(selectedStrokes)

            pushUndoSnapshot()
            // Delete converted strokes by ID
            val remainingStrokes = _uiState.value.strokes.filterNot { selectedStrokeIds.contains(it.id) }

            // Insert converted text item
            val newItem = CanvasTextItem(
                id = IdGenerator.nextId(),
                pageId = currentPage?.id ?: 0,
                text = result.text,
                x = bounds.left,
                y = bounds.top,
                width = (bounds.width).coerceAtLeast(140f),
                fontColor = _uiState.value.penColor,
                isEditing = false
            )

            updateActivePageData(strokes = remainingStrokes, textItems = _uiState.value.textItems + newItem)
            _uiState.update {
                it.copy(
                    isAiProcessing = false,
                    hasSelection = false,
                    lassoSelectedStrokes = emptyList(),
                    lassoSelectedShapes = emptyList(),
                    lassoSelectedTextItems = emptyList(),
                    lassoSelectionBounds = null
                )
            }
            scheduleAutoSave()
        }
    }

    fun convertLassoToMath() {
        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val bounds = _uiState.value.lassoSelectionBounds ?: return
        if (selectedStrokes.isEmpty()) return

        val selectedStrokeIds = selectedStrokes.map { it.id }.toSet()

        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            val mathResult = aiSmartEngine.convertMathEquation(selectedStrokes)

            pushUndoSnapshot()
            val remainingStrokes = _uiState.value.strokes.filterNot { selectedStrokeIds.contains(it.id) }

            val newItem = CanvasTextItem(
                id = IdGenerator.nextId(),
                pageId = currentPage?.id ?: 0,
                text = "∑ ${mathResult.formulaText}",
                x = bounds.left,
                y = bounds.top,
                width = (bounds.width).coerceAtLeast(160f),
                fontSize = 16f,
                fontColor = Color(0xFF4338CA),
                backgroundColor = Color(0xFFEEF2FF),
                isMathFormula = true,
                isEditing = false
            )

            updateActivePageData(strokes = remainingStrokes, textItems = _uiState.value.textItems + newItem)
            _uiState.update {
                it.copy(
                    isAiProcessing = false,
                    hasSelection = false,
                    lassoSelectedStrokes = emptyList(),
                    lassoSelectedShapes = emptyList(),
                    lassoSelectedTextItems = emptyList(),
                    lassoSelectionBounds = null
                )
            }
            scheduleAutoSave()
        }
    }

    fun duplicateLassoSelection() {
        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTextItems = _uiState.value.lassoSelectedTextItems
        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTextItems.isEmpty()) return

        pushUndoSnapshot()
        val duplicatedStrokes = selectedStrokes.map { s ->
            s.copy(
                id = IdGenerator.nextId(),
                points = s.points.map { p -> p.copy(x = p.x + 30f, y = p.y + 30f) }
            )
        }
        val duplicatedShapes = selectedShapes.map { s ->
            s.copy(
                id = IdGenerator.nextId(),
                startX = s.startX + 30f,
                startY = s.startY + 30f,
                endX = s.endX + 30f,
                endY = s.endY + 30f
            )
        }
        val duplicatedTexts = selectedTextItems.map { t ->
            t.copy(
                id = IdGenerator.nextId(),
                x = t.x + 30f,
                y = t.y + 30f
            )
        }

        val newStrokes = _uiState.value.strokes + duplicatedStrokes
        val newShapes = _uiState.value.shapes + duplicatedShapes
        val newTexts = _uiState.value.textItems + duplicatedTexts

        val oldBounds = _uiState.value.lassoSelectionBounds
        val newBounds = oldBounds?.let {
            Rect(it.left + 30f, it.top + 30f, it.right + 30f, it.bottom + 30f)
        }

        updateActivePageData(strokes = newStrokes, shapes = newShapes, textItems = newTexts)
        _uiState.update {
            it.copy(
                lassoSelectedStrokes = duplicatedStrokes,
                lassoSelectedShapes = duplicatedShapes,
                lassoSelectedTextItems = duplicatedTexts,
                lassoSelectionBounds = newBounds
            )
        }
        scheduleAutoSave()
    }

    fun deleteLassoSelection() {
        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTextItems = _uiState.value.lassoSelectedTextItems
        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTextItems.isEmpty()) return

        pushUndoSnapshot()
        val selectedStrokeIds = selectedStrokes.map { it.id }.toSet()
        val selectedShapeIds = selectedShapes.map { it.id }.toSet()
        val selectedTextIds = selectedTextItems.map { it.id }.toSet()

        val remainingStrokes = _uiState.value.strokes.filterNot { selectedStrokeIds.contains(it.id) }
        val remainingShapes = _uiState.value.shapes.filterNot { selectedShapeIds.contains(it.id) }
        val remainingTexts = _uiState.value.textItems.filterNot { selectedTextIds.contains(it.id) }

        updateActivePageData(strokes = remainingStrokes, shapes = remainingShapes, textItems = remainingTexts)
        _uiState.update {
            it.copy(
                hasSelection = false,
                lassoSelectedStrokes = emptyList(),
                lassoSelectedShapes = emptyList(),
                lassoSelectedTextItems = emptyList(),
                lassoSelectionBounds = null
            )
        }
        scheduleAutoSave()
    }

    fun changeLassoColor(color: Color) {
        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty()) return

        pushUndoSnapshot()
        val selectedStrokeIds = selectedStrokes.map { it.id }.toSet()
        val selectedShapeIds = selectedShapes.map { it.id }.toSet()

        val updatedStrokes = _uiState.value.strokes.map { s ->
            if (selectedStrokeIds.contains(s.id)) s.copy(color = color) else s
        }
        val updatedShapes = _uiState.value.shapes.map { s ->
            if (selectedShapeIds.contains(s.id)) s.copy(strokeColor = color) else s
        }
        updateActivePageData(strokes = updatedStrokes, shapes = updatedShapes)
        scheduleAutoSave()
    }

    // Text Boxes
    fun addTextItem(x: Float, y: Float) {
        val pageId = currentPage?.id ?: return
        addTextItemToPage(pageId, x, y)
    }

    fun addTextItemToPage(pageId: Long, x: Float, y: Float) {
        pushUndoSnapshot()
        val currentAudioMs = if (_uiState.value.isRecordingAudio) recorderManager.getCurrentElapsedMs() else null
        val newItem = CanvasTextItem(
            pageId = pageId,
            text = "Type here...",
            x = x,
            y = y,
            fontColor = _uiState.value.penColor,
            audioTimestampMs = currentAudioMs,
            isEditing = true
        )
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newTexts = currentData.textItems + newItem
        updatePageData(pageId, textItems = newTexts)
        scheduleAutoSaveForPage(pageId)
    }

    fun updateTextItem(item: CanvasTextItem) {
        val pageId = item.pageId.takeIf { it != 0L } ?: (currentPage?.id ?: return)
        updateTextItemOnPage(pageId, item)
    }

    fun updateTextItemOnPage(pageId: Long, item: CanvasTextItem) {
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newTexts = currentData.textItems.map { if (it.id == item.id || it === item) item else it }
        updatePageData(pageId, textItems = newTexts)
        scheduleAutoSaveForPage(pageId)
    }

    fun deleteTextItem(item: CanvasTextItem) {
        val pageId = item.pageId.takeIf { it != 0L } ?: (currentPage?.id ?: return)
        deleteTextItemOnPage(pageId, item)
    }

    fun deleteTextItemOnPage(pageId: Long, item: CanvasTextItem) {
        pushUndoSnapshot()
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newTexts = currentData.textItems.filterNot { it.id == item.id || it === item }
        updatePageData(pageId, textItems = newTexts)
        scheduleAutoSaveForPage(pageId)
    }

    // Media Insert
    fun addMediaImage(uri: Uri) {
        val pageId = currentPage?.id ?: return
        addMediaImageToPage(pageId, uri)
    }

    fun addMediaImageToPage(pageId: Long, uri: Uri) {
        viewModelScope.launch {
            pushUndoSnapshot()
            val context = getApplication<Application>()
            val mediaDir = File(context.filesDir, "media_items")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val fileName = "img_${System.currentTimeMillis()}.png"
            val localFile = File(mediaDir, fileName)

            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri)
                val outputStream = FileOutputStream(localFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
            }

            val currentAudioMs = if (_uiState.value.isRecordingAudio) recorderManager.getCurrentElapsedMs() else null
            val newItem = CanvasMediaItem(
                pageId = pageId,
                localUri = localFile.absolutePath,
                x = 100f,
                y = 100f,
                width = 300f,
                height = 240f,
                audioTimestampMs = currentAudioMs
            )

            val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
            val newMedias = currentData.mediaItems + newItem
            updatePageData(pageId, mediaItems = newMedias)
            scheduleAutoSaveForPage(pageId)
        }
    }

    fun updateMediaItem(item: CanvasMediaItem) {
        val pageId = item.pageId.takeIf { it != 0L } ?: (currentPage?.id ?: return)
        updateMediaItemOnPage(pageId, item)
    }

    fun updateMediaItemOnPage(pageId: Long, item: CanvasMediaItem) {
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newMedias = currentData.mediaItems.map { if (it.id == item.id || it === item) item else it }
        updatePageData(pageId, mediaItems = newMedias)
        scheduleAutoSaveForPage(pageId)
    }

    fun deleteMediaItem(item: CanvasMediaItem) {
        val pageId = item.pageId.takeIf { it != 0L } ?: (currentPage?.id ?: return)
        deleteMediaItemOnPage(pageId, item)
    }

    fun deleteMediaItemOnPage(pageId: Long, item: CanvasMediaItem) {
        pushUndoSnapshot()
        val currentData = _uiState.value.pagesData[pageId] ?: PageDataState()
        val newMedias = currentData.mediaItems.filterNot { it.id == item.id || it === item }
        updatePageData(pageId, mediaItems = newMedias)
        scheduleAutoSaveForPage(pageId)
    }

    // Advanced Study Tools & AI
    fun openFlashcardsModal() {
        _uiState.update { it.copy(showFlashcardsModal = true, aiErrorMessage = null) }
    }

    fun closeFlashcardsModal() {
        _uiState.update { it.copy(showFlashcardsModal = false, aiErrorMessage = null) }
    }

    fun generateFlashcardsWithAi() {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true, aiErrorMessage = null) }
            try {
                val noteContent = buildNotebookContentString()
                val newCards = aiSmartEngine.generateFlashcards(nb.id, nb.title, noteContent)
                repository.insertFlashcards(newCards)
                _uiState.update { it.copy(isAiProcessing = false, flashcards = newCards, aiErrorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        aiErrorMessage = e.message ?: "Couldn't read content from this document — please add notes or text before generating flashcards."
                    )
                }
            }
        }
    }

    fun openQuizModal() {
        _uiState.update { it.copy(showQuizModal = true, aiErrorMessage = null) }
    }

    fun closeQuizModal() {
        _uiState.update { it.copy(showQuizModal = false, aiErrorMessage = null) }
    }

    fun generateQuizWithAi() {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true, aiErrorMessage = null) }
            try {
                val noteContent = buildNotebookContentString()
                val questions = aiSmartEngine.generateQuiz(nb.title, noteContent)
                _uiState.update { it.copy(isAiProcessing = false, quizQuestions = questions, aiErrorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        aiErrorMessage = e.message ?: "Couldn't read content from this document — please add notes or text before generating a quiz."
                    )
                }
            }
        }
    }

    fun openSummaryModal() {
        _uiState.update { it.copy(showSummaryModal = true, aiErrorMessage = null) }
        summarizeNotebook()
    }

    fun closeSummaryModal() {
        _uiState.update { it.copy(showSummaryModal = false, aiErrorMessage = null) }
    }

    private fun summarizeNotebook() {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true, aiErrorMessage = null) }
            try {
                val noteContent = buildNotebookContentString()
                val summary = aiSmartEngine.summarizeNotes(nb.title, noteContent)
                _uiState.update { it.copy(isAiProcessing = false, noteSummary = summary, aiErrorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        aiErrorMessage = e.message ?: "Couldn't read content from this document."
                    )
                }
            }
        }
    }

    // Snapshots / Version History
    fun openSnapshotsModal() {
        _uiState.update { it.copy(showSnapshotsModal = true) }
    }

    fun closeSnapshotsModal() {
        _uiState.update { it.copy(showSnapshotsModal = false) }
    }

    fun saveCurrentSnapshot(title: String) {
        val page = currentPage ?: return
        viewModelScope.launch {
            val strokes = _uiState.value.strokes
            val shapes = _uiState.value.shapes
            val texts = _uiState.value.textItems
            repository.savePageSnapshot(
                pageId = page.id,
                notebookId = currentNotebookId,
                title = title,
                strokesJson = strokes.size.toString(),
                shapesJson = shapes.size.toString(),
                textJson = texts.joinToString("\n") { it.text }
            )
            loadSnapshots(page.id)
        }
    }

    fun restoreSnapshot(snapshot: PageSnapshotEntity) {
        // Rollback current state
        loadPageData(snapshot.pageId)
    }

    fun deleteSnapshot(snapshot: PageSnapshotEntity) {
        viewModelScope.launch {
            repository.deleteSnapshot(snapshot)
            loadSnapshots(snapshot.pageId)
        }
    }

    // Security & Pin
    fun openSecurityModal() {
        _uiState.update { it.copy(showSecurityModal = true) }
    }

    fun closeSecurityModal() {
        _uiState.update { it.copy(showSecurityModal = false) }
    }

    fun setNotebookPin(pin: String): Boolean {
        viewModelScope.launch {
            repository.updateNotebookSecurity(currentNotebookId, isLocked = true, pinCode = pin)
            val updated = _uiState.value.notebook?.copy(isLocked = true, pinCode = pin)
            _uiState.update { it.copy(notebook = updated, showSecurityModal = false) }
        }
        return true
    }

    fun removeNotebookPin() {
        viewModelScope.launch {
            repository.updateNotebookSecurity(currentNotebookId, isLocked = false, pinCode = null)
            val updated = _uiState.value.notebook?.copy(isLocked = false, pinCode = null)
            _uiState.update { it.copy(notebook = updated, showSecurityModal = false) }
        }
    }

    // Audio Sync & Scrubbing
    fun startAudioRecording() {
        val success = recorderManager.startRecording(currentNotebookId)
        if (success) {
            _uiState.update { it.copy(isRecordingAudio = true) }
        }
    }

    fun stopAudioRecording() {
        val result = recorderManager.stopRecording() ?: return
        viewModelScope.launch {
            val recId = repository.saveRecording(
                AudioRecordingEntity(
                    notebookId = currentNotebookId,
                    fileName = result.fileName,
                    filePath = result.filePath,
                    durationMs = result.durationMs,
                    title = "Recording ${System.currentTimeMillis() % 10000}"
                )
            )
            val nb = repository.getNotebook(currentNotebookId)
            if (nb != null) {
                repository.updateNotebook(nb.copy(audioDurationMs = nb.audioDurationMs + result.durationMs))
            }
            _uiState.update { it.copy(activeRecordingId = recId) }
        }
    }

    fun playAudioRecording(recording: AudioRecordingEntity) {
        if (_uiState.value.activeRecordingId == recording.id && _uiState.value.isPlayingAudio) {
            playerManager.pause()
        } else {
            _uiState.update { it.copy(activeRecordingId = recording.id) }
            playerManager.loadAudio(recording.filePath, autoPlay = true)
        }
    }

    fun deleteAudioRecording(recording: AudioRecordingEntity) {
        viewModelScope.launch {
            if (_uiState.value.activeRecordingId == recording.id) {
                playerManager.stop()
                _uiState.update { it.copy(activeRecordingId = null, isPlayingAudio = false) }
            }
            repository.deleteRecording(recording)
        }
    }

    fun renameAudioRecording(recording: AudioRecordingEntity, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(recording, newTitle)
        }
    }

    fun toggleAudioPlayback() {
        if (_uiState.value.isPlayingAudio) {
            playerManager.pause()
        } else {
            val activeRecId = _uiState.value.activeRecordingId
            val rec = _uiState.value.recordings.find { it.id == activeRecId }
                ?: _uiState.value.recordings.firstOrNull()
            if (rec != null) {
                _uiState.update { it.copy(activeRecordingId = rec.id) }
                playerManager.loadAudio(rec.filePath, autoPlay = true)
            }
        }
    }

    fun seekAudio(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun jumpAudioToTimestamp(timestampMs: Long?) {
        if (timestampMs == null) return
        val activeRecId = _uiState.value.activeRecordingId
        val rec = _uiState.value.recordings.find { it.id == activeRecId }
            ?: _uiState.value.recordings.firstOrNull()
        if (rec != null) {
            _uiState.update { it.copy(activeRecordingId = rec.id) }
            playerManager.loadAudio(rec.filePath, autoPlay = false)
            playerManager.seekTo(timestampMs)
            playerManager.play()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    // Undo / Redo
    private fun pushUndoSnapshot() {
        val state = _uiState.value
        undoStack.add(
            CanvasActionState(
                strokes = state.strokes,
                shapes = state.shapes,
                textItems = state.textItems,
                mediaItems = state.mediaItems
            )
        )
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        _uiState.update { it.copy(canUndo = true, canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value
        redoStack.add(
            CanvasActionState(
                strokes = current.strokes,
                shapes = current.shapes,
                textItems = current.textItems,
                mediaItems = current.mediaItems
            )
        )
        val prev = undoStack.removeAt(undoStack.size - 1)
        updateActivePageData(
            strokes = prev.strokes,
            shapes = prev.shapes,
            textItems = prev.textItems,
            mediaItems = prev.mediaItems
        )
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
        scheduleAutoSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value
        undoStack.add(
            CanvasActionState(
                strokes = current.strokes,
                shapes = current.shapes,
                textItems = current.textItems,
                mediaItems = current.mediaItems
            )
        )
        val next = redoStack.removeAt(redoStack.size - 1)
        updateActivePageData(
            strokes = next.strokes,
            shapes = next.shapes,
            textItems = next.textItems,
            mediaItems = next.mediaItems
        )
        _uiState.update {
            it.copy(
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleAutoSave()
    }

    // Continuous Auto-Save Engine
    private val pageSaveJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

    fun scheduleAutoSaveForPage(pageId: Long) {
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        pageSaveJobs[pageId]?.cancel()
        val job = persistenceScope.launch {
            delay(200) // Highly responsive continuous auto-save
            savePageInternal(pageId)
        }
        pageSaveJobs[pageId] = job
    }

    fun savePageImmediately(pageId: Long) {
        flushPage(pageId)
    }

    fun saveCurrentPageImmediately(manual: Boolean = false) {
        val page = currentPage ?: return
        flushPage(page.id, manual = manual)
    }

    fun flushAllPages() {
        val pageIds = _uiState.value.pagesData.keys.toList()
        pageIds.forEach { pageId ->
            flushPage(pageId, manual = false)
        }
    }

    fun flushPage(pageId: Long, manual: Boolean = false) {
        pageSaveJobs[pageId]?.cancel()
        persistenceScope.launch {
            withContext(NonCancellable) {
                savePageInternal(pageId)
            }
        }
    }

    private suspend fun savePageInternal(pageId: Long) {
        val pageData = _uiState.value.pagesData[pageId] ?: return
        try {
            _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
            repository.saveEntirePageContent(
                pageId = pageId,
                strokes = pageData.strokes,
                shapes = pageData.shapes,
                textItems = pageData.textItems,
                mediaItems = pageData.mediaItems
            )
            _uiState.update {
                it.copy(
                    saveStatus = SaveStatus.SAVED,
                    lastSavedTimeMillis = System.currentTimeMillis(),
                    saveErrorMessage = null
                )
            }
            Log.d("NoteCanvasViewModel", "Auto-saved page $pageId (${pageData.strokes.size} strokes) successfully")
            authRepository.triggerSync()
        } catch (e: Exception) {
            Log.e("NoteCanvasViewModel", "Auto-save failed for page $pageId", e)
            _uiState.update {
                it.copy(
                    saveStatus = SaveStatus.ERROR,
                    saveErrorMessage = e.message ?: "Failed to write note changes to disk."
                )
            }
        }
    }

    private fun scheduleAutoSave() {
        val currentId = currentPage?.id
        if (currentId != null) {
            scheduleAutoSaveForPage(currentId)
        } else {
            autoSaveJob?.cancel()
            autoSaveJob = persistenceScope.launch {
                delay(200)
                saveCurrentPageImmediately()
            }
        }
    }

    fun updatePageData(
        pageId: Long,
        strokes: List<VectorStroke>? = null,
        shapes: List<GeometricShape>? = null,
        textItems: List<CanvasTextItem>? = null,
        mediaItems: List<CanvasMediaItem>? = null
    ) {
        val current = _uiState.value.pagesData[pageId] ?: PageDataState()
        val updated = current.copy(
            strokes = strokes ?: current.strokes,
            shapes = shapes ?: current.shapes,
            textItems = textItems ?: current.textItems,
            mediaItems = mediaItems ?: current.mediaItems
        )
        val updatedMap = _uiState.value.pagesData + (pageId to updated)
        val isCurrent = pageId == currentPage?.id
        _uiState.update {
            it.copy(
                pagesData = updatedMap,
                strokes = if (isCurrent) updated.strokes else it.strokes,
                shapes = if (isCurrent) updated.shapes else it.shapes,
                textItems = if (isCurrent) updated.textItems else it.textItems,
                mediaItems = if (isCurrent) updated.mediaItems else it.mediaItems
            )
        }
    }

    private fun updateActivePageData(
        strokes: List<VectorStroke> = _uiState.value.strokes,
        shapes: List<GeometricShape> = _uiState.value.shapes,
        textItems: List<CanvasTextItem> = _uiState.value.textItems,
        mediaItems: List<CanvasMediaItem> = _uiState.value.mediaItems
    ) {
        val pageId = currentPage?.id ?: 0L
        updatePageData(pageId, strokes, shapes, textItems, mediaItems)
    }

    private suspend fun buildNotebookContentString(): String = withContext(Dispatchers.IO) {
        val nbId = currentNotebookId
        val pages = repository.getPagesList(nbId)
        val sb = StringBuilder()
        for (page in pages) {
            try {
                val textItems = repository.getTextItemsForPage(page.id).first()
                for (ti in textItems) {
                    if (ti.text.isNotBlank() && ti.text != "Type here...") {
                        sb.append(ti.text).append("\n")
                    }
                }
            } catch (e: Exception) {
                // Ignore empty or uninitialized page flow
            }
        }
        // Include any memory-only active page text items
        _uiState.value.textItems.forEach { ti ->
            if (ti.text.isNotBlank() && ti.text != "Type here..." && !sb.contains(ti.text)) {
                sb.append(ti.text).append("\n")
            }
        }
        // Include any audio recording titles/topics
        _uiState.value.recordings.forEach { rec ->
            if (rec.title.isNotBlank()) {
                sb.append("Voice Note: ").append(rec.title).append("\n")
            }
        }
        sb.toString()
    }

    // Export & Share
    fun showExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show) }
    }

    fun exportAsPdf(onShared: (File) -> Unit) {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            saveCurrentPageImmediately()
            val pages = repository.getPagesList(nb.id)
            val pdfFile = PdfManager.exportNotebookToPdf(
                context = getApplication(),
                notebookTitle = nb.title,
                pages = pages,
                repository = repository
            )
            _uiState.update { it.copy(isExporting = false, showExportDialog = false) }
            onShared(pdfFile)
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.aistudio.inknote.app.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Note via"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun transcribeRecording(recording: AudioRecordingEntity) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showTranscriptionDialog = true,
                    isTranscribing = true,
                    transcriptionResult = null
                )
            }
            val result = transcriptionManager.transcribeAudio(recording)
            _uiState.update {
                it.copy(
                    isTranscribing = false,
                    transcriptionResult = result
                )
            }
        }
    }

    fun transcribeLatestAudio() {
        val recordings = _uiState.value.recordings
        if (recordings.isEmpty()) {
            _uiState.update {
                it.copy(
                    showTranscriptionDialog = true,
                    isTranscribing = false,
                    transcriptionResult = TranscriptionResult(
                        title = "Voice Notes",
                        text = "No audio recordings found in this notebook yet. Tap the red mic button to record voice notes!",
                        isSuccess = false
                    )
                )
            }
        } else {
            val latest = recordings.first()
            transcribeRecording(latest)
        }
    }

    fun dismissTranscriptionDialog() {
        _uiState.update {
            it.copy(
                showTranscriptionDialog = false,
                isTranscribing = false
            )
        }
    }

    fun insertTranscribedText(text: String) {
        if (text.isBlank()) return
        pushUndoSnapshot()
        val currentTextList = _uiState.value.textItems.toMutableList()
        val newItem = CanvasTextItem(
            pageId = currentPage?.id ?: 0,
            text = text,
            x = 80f,
            y = 150f + (currentTextList.size * 30f),
            fontSize = 15f,
            fontColor = Color(0xFF1E293B),
            isEditing = false
        )
        currentTextList.add(newItem)
        updateActivePageData(textItems = currentTextList)
        scheduleAutoSave()
    }

    fun moveNotebookToSubject(targetSubjectId: Long?) {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            val updated = nb.copy(subjectId = targetSubjectId, updatedAt = System.currentTimeMillis())
            repository.updateNotebook(updated)
            _uiState.update { it.copy(notebook = updated) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        flushAllPages()
        recorderManager.stopRecording()
        playerManager.stop()
    }
}
