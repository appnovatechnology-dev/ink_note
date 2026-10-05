package com.example.inknote.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inknote.audio.AudioPlayerManager
import com.example.inknote.audio.AudioRecorderManager
import com.example.inknote.audio.OfflineTtsManager
import com.example.inknote.audio.TranscriptionManager
import com.example.inknote.audio.TranscriptionResult
import com.example.inknote.data.ai.*
import com.example.inknote.data.db.entities.AudioRecordingEntity
import com.example.inknote.data.db.entities.FlashcardEntity
import com.example.inknote.data.db.entities.NotebookEntity
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.db.entities.PageSnapshotEntity
import com.example.inknote.data.model.*
import com.example.inknote.data.preferences.SettingsManager
import com.example.inknote.data.repository.NoteRepository
import com.example.inknote.geometry.GeometryEngine
import com.example.inknote.pdf.PdfManager
import com.example.inknote.ui.canvas.components.StylusInputState
import com.example.inknote.util.DownloadUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
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
    val laserColor: Color = Color(0xFFEF4444),
    val isLaserTailEnabled: Boolean = true,
    val palmRejectionMode: PalmRejectionMode = PalmRejectionMode.STYLUS_ONLY,
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
    val lassoSelectedMediaItems: List<CanvasMediaItem> = emptyList(),
    val lassoSelectionBounds: Rect? = null,

    // Audio State
    val isRecordingAudio: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val isPlayingAudio: Boolean = false,
    val playbackCurrentMs: Long = 0L,
    val playbackTotalMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeRecordingId: Long? = null,

    // Offline Text to Speech
    val isSpeakingTts: Boolean = false,

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
    val showSettingsDialog: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcriptionResult: TranscriptionResult? = null,
    val isExporting: Boolean = false,
    val showShapePropertyDialog: Boolean = false,
    val saveNotificationMessage: String? = null,

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
    val settingsManager = SettingsManager(application)
    val recorderManager = AudioRecorderManager(application)
    val playerManager = AudioPlayerManager()
    val transcriptionManager = TranscriptionManager(application)
    val aiSmartEngine = AiSmartToolsEngine(application)
    val ttsManager = OfflineTtsManager(application)
    val stylusInputState = StylusInputState()

    private val _uiState = MutableStateFlow(NoteCanvasUiState())
    val uiState: StateFlow<NoteCanvasUiState> = _uiState.asStateFlow()

    private var currentNotebookId: Long = 0L
    private val undoStack = mutableListOf<CanvasActionState>()
    private val redoStack = mutableListOf<CanvasActionState>()

    private var autoSaveJob: Job? = null

    init {
        stylusInputState.isStylusConnectedOrActive(application)
        // Load persisted settings
        _uiState.update {
            it.copy(
                palmRejectionMode = settingsManager.palmRejectionMode,
                penColor = settingsManager.penColor,
                penWidth = settingsManager.penWidth,
                highlighterColor = settingsManager.highlighterColor,
                highlighterWidth = settingsManager.highlighterWidth,
                eraserMode = settingsManager.eraserMode,
                eraserWidth = settingsManager.eraserWidth,
                isGridSnapEnabled = settingsManager.isGridSnapEnabled,
                isMagicInkEnabled = settingsManager.isMagicInkEnabled,
                laserColor = settingsManager.laserColor,
                isLaserTailEnabled = settingsManager.isLaserTailEnabled
            )
        }

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
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeakingTts = speaking) }
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

    fun renameNotebook(newTitle: String) {
        val nb = _uiState.value.notebook ?: return
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty() || trimmed == nb.title) return
        viewModelScope.launch {
            val updated = nb.copy(title = trimmed, updatedAt = System.currentTimeMillis())
            repository.updateNotebook(updated)
            _uiState.update { it.copy(notebook = updated) }
        }
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
        settingsManager.penColor = color
        _uiState.update { it.copy(penColor = color) }
    }

    fun setPenWidth(width: Float) {
        settingsManager.penWidth = width
        _uiState.update { it.copy(penWidth = width) }
    }

    fun setHighlighterColor(color: Color) {
        settingsManager.highlighterColor = color
        _uiState.update { it.copy(highlighterColor = color) }
    }

    fun setHighlighterWidth(width: Float) {
        settingsManager.highlighterWidth = width
        _uiState.update { it.copy(highlighterWidth = width) }
    }

    fun setEraserMode(mode: EraserMode) {
        settingsManager.eraserMode = mode
        _uiState.update { it.copy(eraserMode = mode) }
    }

    fun setEraserWidth(width: Float) {
        settingsManager.eraserWidth = width
        _uiState.update { it.copy(eraserWidth = width) }
    }

    fun toggleMagicInk() {
        val newVal = !_uiState.value.isMagicInkEnabled
        settingsManager.isMagicInkEnabled = newVal
        _uiState.update { it.copy(isMagicInkEnabled = newVal) }
    }

    fun toggleGridSnap() {
        val newVal = !_uiState.value.isGridSnapEnabled
        settingsManager.isGridSnapEnabled = newVal
        _uiState.update { it.copy(isGridSnapEnabled = newVal) }
    }

    fun setLaserColor(color: Color) {
        settingsManager.laserColor = color
        _uiState.update { it.copy(laserColor = color) }
    }

    fun toggleLaserTail() {
        val newVal = !_uiState.value.isLaserTailEnabled
        settingsManager.isLaserTailEnabled = newVal
        _uiState.update { it.copy(isLaserTailEnabled = newVal) }
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
            if (stroke.points.isEmpty()) return@filterNot false
            if (stroke.points.size == 1) {
                val p = stroke.points[0]
                hypot((p.x - point.x).toDouble(), (p.y - point.y).toDouble()) <= (radius + stroke.strokeWidth / 2f)
            } else {
                for (i in 0 until stroke.points.size - 1) {
                    val p1 = stroke.points[i]
                    val p2 = stroke.points[i + 1]
                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val lenSq = dx * dx + dy * dy
                    val dist = if (lenSq == 0f) {
                        hypot((point.x - p1.x).toDouble(), (point.y - p1.y).toDouble()).toFloat()
                    } else {
                        val t = (((point.x - p1.x) * dx + (point.y - p1.y) * dy) / lenSq).coerceIn(0f, 1f)
                        val projX = p1.x + t * dx
                        val projY = p1.y + t * dy
                        hypot((point.x - projX).toDouble(), (point.y - projY).toDouble()).toFloat()
                    }
                    if (dist <= (radius + stroke.strokeWidth / 2f)) return@filterNot true
                }
                false
            }
        }

        val remainingShapes = currentShapes.filterNot { shape ->
            val cx = shape.centerX
            val cy = shape.centerY
            hypot((cx - point.x).toDouble(), (cy - point.y).toDouble()) <= (radius + shape.width / 2f)
        }

        if (remainingStrokes.size != currentStrokes.size || remainingShapes.size != currentShapes.size) {
            pushUndoSnapshot()
            updatePageData(pageId, strokes = remainingStrokes, shapes = remainingShapes)
            scheduleAutoSaveForPage(pageId)
        }
    }

    // Lasso Selection
    fun handleLassoSelection(lassoPoints: List<Offset>, targetPageId: Long? = null) {
        if (lassoPoints.size < 3) {
            _uiState.update {
                it.copy(
                    hasSelection = false,
                    lassoSelectedStrokes = emptyList(),
                    lassoSelectedShapes = emptyList(),
                    lassoSelectedTextItems = emptyList(),
                    lassoSelectedMediaItems = emptyList(),
                    lassoSelectionBounds = null
                )
            }
            return
        }

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        lassoPoints.forEach { pt ->
            minX = min(minX, pt.x)
            maxX = max(maxX, pt.x)
            minY = min(minY, pt.y)
            maxY = max(maxY, pt.y)
        }

        val bounds = Rect(minX, minY, maxX, maxY)
        val pageId = targetPageId ?: currentPage?.id
        val pageData = if (pageId != null) _uiState.value.pagesData[pageId] else null
        val strokes = pageData?.strokes ?: _uiState.value.strokes
        val shapes = pageData?.shapes ?: _uiState.value.shapes
        val texts = pageData?.textItems ?: _uiState.value.textItems
        val medias = pageData?.mediaItems ?: _uiState.value.mediaItems

        val selectedStrokes = strokes.filter { stroke ->
            stroke.points.any { p -> bounds.contains(Offset(p.x, p.y)) }
        }

        val selectedShapes = shapes.filter { shape ->
            val sMinX = min(shape.startX, shape.endX)
            val sMaxX = max(shape.startX, shape.endX)
            val sMinY = min(shape.startY, shape.endY)
            val sMaxY = max(shape.startY, shape.endY)
            bounds.overlaps(Rect(sMinX, sMinY, sMaxX, sMaxY))
        }

        val selectedTexts = texts.filter { text ->
            bounds.overlaps(Rect(text.x, text.y, text.x + text.width, text.y + text.height))
        }

        val selectedMedias = medias.filter { media ->
            bounds.overlaps(Rect(media.x, media.y, media.x + media.width, media.y + media.height))
        }

        val hasAny = selectedStrokes.isNotEmpty() || selectedShapes.isNotEmpty() || selectedTexts.isNotEmpty() || selectedMedias.isNotEmpty()

        // Calculate tight bounding box of all selected content
        val contentBounds = if (hasAny) {
            var bMinX = bounds.left
            var bMaxX = bounds.right
            var bMinY = bounds.top
            var bMaxY = bounds.bottom

            selectedStrokes.forEach { s ->
                s.points.forEach { p ->
                    bMinX = min(bMinX, p.x)
                    bMaxX = max(bMaxX, p.x)
                    bMinY = min(bMinY, p.y)
                    bMaxY = max(bMaxY, p.y)
                }
            }
            selectedShapes.forEach { sh ->
                bMinX = min(bMinX, min(sh.startX, sh.endX))
                bMaxX = max(bMaxX, max(sh.startX, sh.endX))
                bMinY = min(bMinY, min(sh.startY, sh.endY))
                bMaxY = max(bMaxY, max(sh.startY, sh.endY))
            }
            selectedTexts.forEach { t ->
                bMinX = min(bMinX, t.x)
                bMaxX = max(bMaxX, t.x + t.width)
                bMinY = min(bMinY, t.y)
                bMaxY = max(bMaxY, t.y + t.height)
            }
            selectedMedias.forEach { m ->
                bMinX = min(bMinX, m.x)
                bMaxX = max(bMaxX, m.x + m.width)
                bMinY = min(bMinY, m.y)
                bMaxY = max(bMaxY, m.y + m.height)
            }
            Rect(bMinX, bMinY, bMaxX, bMaxY)
        } else null

        _uiState.update {
            it.copy(
                hasSelection = hasAny,
                lassoSelectedStrokes = selectedStrokes,
                lassoSelectedShapes = selectedShapes,
                lassoSelectedTextItems = selectedTexts,
                lassoSelectedMediaItems = selectedMedias,
                lassoSelectionBounds = contentBounds
            )
        }
    }

    fun moveLassoSelection(deltaX: Float, deltaY: Float, targetPageId: Long? = null) {
        if (deltaX == 0f && deltaY == 0f) return
        val pageId = targetPageId ?: currentPage?.id ?: return
        val currentData = _uiState.value.pagesData[pageId] ?: return

        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTexts = _uiState.value.lassoSelectedTextItems
        val selectedMedias = _uiState.value.lassoSelectedMediaItems

        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTexts.isEmpty() && selectedMedias.isEmpty()) return

        pushUndoSnapshot()

        val selectedStrokeIds = selectedStrokes.map { it.id }.filter { it != 0L }.toSet()
        val updatedStrokes = currentData.strokes.map { s ->
            val isSelected = (s.id != 0L && selectedStrokeIds.contains(s.id)) || selectedStrokes.contains(s)
            if (isSelected) {
                s.copy(points = s.points.map { it.copy(x = it.x + deltaX, y = it.y + deltaY) })
            } else {
                s
            }
        }

        val selectedShapeIds = selectedShapes.map { it.id }.filter { it != 0L }.toSet()
        val updatedShapes = currentData.shapes.map { shape ->
            val isSelected = (shape.id != 0L && selectedShapeIds.contains(shape.id)) || selectedShapes.contains(shape)
            if (isSelected) {
                shape.copy(
                    startX = shape.startX + deltaX,
                    startY = shape.startY + deltaY,
                    endX = shape.endX + deltaX,
                    endY = shape.endY + deltaY
                )
            } else {
                shape
            }
        }

        val selectedTextIds = selectedTexts.map { it.id }.filter { it != 0L }.toSet()
        val updatedTexts = currentData.textItems.map { text ->
            val isSelected = (text.id != 0L && selectedTextIds.contains(text.id)) || selectedTexts.any { it.id == text.id }
            if (isSelected) {
                text.copy(x = text.x + deltaX, y = text.y + deltaY)
            } else {
                text
            }
        }

        val selectedMediaIds = selectedMedias.map { it.id }.filter { it != 0L }.toSet()
        val updatedMedias = currentData.mediaItems.map { media ->
            val isSelected = (media.id != 0L && selectedMediaIds.contains(media.id)) || selectedMedias.any { it.id == media.id }
            if (isSelected) {
                media.copy(x = media.x + deltaX, y = media.y + deltaY)
            } else {
                media
            }
        }

        val newBounds = _uiState.value.lassoSelectionBounds?.translate(Offset(deltaX, deltaY))

        updatePageData(
            pageId = pageId,
            strokes = updatedStrokes,
            shapes = updatedShapes,
            textItems = updatedTexts,
            mediaItems = updatedMedias
        )

        val newSelectedStrokes = updatedStrokes.filter { s ->
            (s.id != 0L && selectedStrokeIds.contains(s.id)) || selectedStrokes.any { it.id == s.id }
        }
        val newSelectedShapes = updatedShapes.filter { sh ->
            (sh.id != 0L && selectedShapeIds.contains(sh.id)) || selectedShapes.any { it.id == sh.id }
        }
        val newSelectedTexts = updatedTexts.filter { t ->
            (t.id != 0L && selectedTextIds.contains(t.id)) || selectedTexts.any { it.id == t.id }
        }
        val newSelectedMedias = updatedMedias.filter { m ->
            (m.id != 0L && selectedMediaIds.contains(m.id)) || selectedMedias.any { it.id == m.id }
        }

        _uiState.update {
            it.copy(
                lassoSelectionBounds = newBounds,
                lassoSelectedStrokes = newSelectedStrokes,
                lassoSelectedShapes = newSelectedShapes,
                lassoSelectedTextItems = newSelectedTexts,
                lassoSelectedMediaItems = newSelectedMedias
            )
        }

        scheduleAutoSaveForPage(pageId)
    }

    fun convertLassoToMath() {
        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val bounds = _uiState.value.lassoSelectionBounds ?: return
        if (selectedStrokes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            val mathResult = aiSmartEngine.convertMathEquation(selectedStrokes)

            pushUndoSnapshot()
            val remainingStrokes = _uiState.value.strokes.filterNot { selectedStrokes.contains(it) }

            val newItem = CanvasTextItem(
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
                    lassoSelectedMediaItems = emptyList(),
                    lassoSelectionBounds = null
                )
            }
            scheduleAutoSave()
        }
    }

    fun duplicateLassoSelection(targetPageId: Long? = null) {
        val pageId = targetPageId ?: currentPage?.id ?: return
        val currentData = _uiState.value.pagesData[pageId] ?: return

        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTexts = _uiState.value.lassoSelectedTextItems
        val selectedMedias = _uiState.value.lassoSelectedMediaItems

        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTexts.isEmpty() && selectedMedias.isEmpty()) return

        pushUndoSnapshot()
        val offset = 35f

        val duplicatedStrokes = selectedStrokes.map { s ->
            s.copy(
                id = 0,
                points = s.points.map { p -> p.copy(x = p.x + offset, y = p.y + offset) }
            )
        }
        val duplicatedShapes = selectedShapes.map { sh ->
            sh.copy(
                id = 0,
                startX = sh.startX + offset,
                startY = sh.startY + offset,
                endX = sh.endX + offset,
                endY = sh.endY + offset
            )
        }
        val duplicatedTexts = selectedTexts.map { t ->
            t.copy(
                id = 0,
                x = t.x + offset,
                y = t.y + offset
            )
        }
        val duplicatedMedias = selectedMedias.map { m ->
            m.copy(
                id = 0,
                x = m.x + offset,
                y = m.y + offset
            )
        }

        updatePageData(
            pageId = pageId,
            strokes = currentData.strokes + duplicatedStrokes,
            shapes = currentData.shapes + duplicatedShapes,
            textItems = currentData.textItems + duplicatedTexts,
            mediaItems = currentData.mediaItems + duplicatedMedias
        )

        _uiState.update {
            it.copy(
                hasSelection = false,
                lassoSelectedStrokes = emptyList(),
                lassoSelectedShapes = emptyList(),
                lassoSelectedTextItems = emptyList(),
                lassoSelectedMediaItems = emptyList(),
                lassoSelectionBounds = null
            )
        }
        scheduleAutoSaveForPage(pageId)
    }

    fun deleteLassoSelection(targetPageId: Long? = null) {
        val pageId = targetPageId ?: currentPage?.id ?: return
        val currentData = _uiState.value.pagesData[pageId] ?: return

        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTexts = _uiState.value.lassoSelectedTextItems
        val selectedMedias = _uiState.value.lassoSelectedMediaItems

        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTexts.isEmpty() && selectedMedias.isEmpty()) return

        pushUndoSnapshot()

        val selectedStrokeIds = selectedStrokes.map { it.id }.toSet()
        val remainingStrokes = currentData.strokes.filterNot { selectedStrokeIds.contains(it.id) || selectedStrokes.contains(it) }
        val remainingShapes = currentData.shapes.filterNot { selectedShapes.contains(it) }
        val remainingTexts = currentData.textItems.filterNot { selectedTexts.contains(it) }
        val remainingMedias = currentData.mediaItems.filterNot { selectedMedias.contains(it) }

        updatePageData(
            pageId = pageId,
            strokes = remainingStrokes,
            shapes = remainingShapes,
            textItems = remainingTexts,
            mediaItems = remainingMedias
        )

        _uiState.update {
            it.copy(
                hasSelection = false,
                lassoSelectedStrokes = emptyList(),
                lassoSelectedShapes = emptyList(),
                lassoSelectedTextItems = emptyList(),
                lassoSelectedMediaItems = emptyList(),
                lassoSelectionBounds = null
            )
        }
        scheduleAutoSaveForPage(pageId)
    }

    fun changeLassoColor(color: Color, targetPageId: Long? = null) {
        val pageId = targetPageId ?: currentPage?.id ?: return
        val currentData = _uiState.value.pagesData[pageId] ?: return

        val selectedStrokes = _uiState.value.lassoSelectedStrokes
        val selectedShapes = _uiState.value.lassoSelectedShapes
        val selectedTexts = _uiState.value.lassoSelectedTextItems

        if (selectedStrokes.isEmpty() && selectedShapes.isEmpty() && selectedTexts.isEmpty()) return

        pushUndoSnapshot()
        val updatedStrokes = currentData.strokes.map { s ->
            if (selectedStrokes.contains(s)) s.copy(color = color) else s
        }
        val updatedShapes = currentData.shapes.map { sh ->
            if (selectedShapes.contains(sh)) sh.copy(strokeColor = color) else sh
        }
        val updatedTexts = currentData.textItems.map { t ->
            if (selectedTexts.contains(t)) t.copy(fontColor = color) else t
        }

        updatePageData(pageId, strokes = updatedStrokes, shapes = updatedShapes, textItems = updatedTexts)
        scheduleAutoSaveForPage(pageId)
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

    private val ttsTranscriptionCache = mutableMapOf<Long, String>()

    data class NoteMultimodalPayload(
        val pageImages: List<String>,
        val typedText: String,
        val hasInkOrDrawings: Boolean
    )

    private suspend fun gatherNotebookMultimodalPayload(): NoteMultimodalPayload = withContext(Dispatchers.IO) {
        saveAllPendingChangesImmediately()
        val nbId = currentNotebookId
        val allPages = repository.getPagesList(nbId)
        val pageImages = mutableListOf<String>()
        val textBuilder = StringBuilder()
        var hasInkOrDrawings = false

        for (page in allPages) {
            val cached = _uiState.value.pagesData[page.id]
            val strokes = cached?.strokes ?: repository.getStrokesListForExport(page.id)
            val shapes = cached?.shapes ?: repository.getShapesListForExport(page.id)
            val textItems = cached?.textItems ?: repository.getTextListForExport(page.id)
            val mediaItems = cached?.mediaItems ?: repository.getMediaListForExport(page.id)

            for (ti in textItems) {
                val t = ti.text.trim()
                if (t.isNotBlank() && t != "Type here...") {
                    textBuilder.append(t).append("\n")
                }
            }

            if (strokes.isNotEmpty() || shapes.isNotEmpty()) {
                hasInkOrDrawings = true
            }

            val isPopulated = PageImageRenderer.isPagePopulated(page, strokes, shapes, textItems, mediaItems)
            if (isPopulated && pageImages.size < 8) {
                try {
                    val base64Img = PageImageRenderer.renderPageToBase64Jpeg(
                        page = page,
                        strokes = strokes,
                        shapes = shapes,
                        textItems = textItems,
                        mediaItems = mediaItems
                    )
                    pageImages.add(base64Img)
                } catch (e: Exception) {
                    Log.w("NoteCanvasViewModel", "Failed to render page ${page.id} to image", e)
                }
            }
        }

        // Include any audio recording titles/topics
        _uiState.value.recordings.forEach { rec ->
            if (rec.title.isNotBlank()) {
                textBuilder.append("Voice Note: ").append(rec.title).append("\n")
            }
        }

        NoteMultimodalPayload(
            pageImages = pageImages,
            typedText = textBuilder.toString().trim(),
            hasInkOrDrawings = hasInkOrDrawings
        )
    }

    fun generateFlashcardsWithAi() {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true, aiErrorMessage = null) }
            try {
                val payload = gatherNotebookMultimodalPayload()
                val newCards = aiSmartEngine.generateFlashcards(
                    notebookId = nb.id,
                    notebookTitle = nb.title,
                    pageImages = payload.pageImages,
                    notesContent = payload.typedText
                )
                repository.insertFlashcards(newCards)
                _uiState.update { it.copy(isAiProcessing = false, flashcards = newCards, aiErrorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        aiErrorMessage = e.message ?: "Couldn't read content from this document — please add handwriting or notes before generating flashcards."
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
                val payload = gatherNotebookMultimodalPayload()
                val questions = aiSmartEngine.generateQuiz(
                    notebookTitle = nb.title,
                    pageImages = payload.pageImages,
                    notesContent = payload.typedText
                )
                _uiState.update { it.copy(isAiProcessing = false, quizQuestions = questions, aiErrorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        aiErrorMessage = e.message ?: "Couldn't read content from this document — please add handwriting or notes before generating a quiz."
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
                val payload = gatherNotebookMultimodalPayload()
                val summary = aiSmartEngine.summarizeNotes(
                    notebookTitle = nb.title,
                    pageImages = payload.pageImages,
                    notesContent = payload.typedText
                )
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

    // Auto-Save Continuous Engine
    private val pageSaveJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

    fun scheduleAutoSaveForPage(pageId: Long) {
        pageSaveJobs[pageId]?.cancel()
        val job = viewModelScope.launch {
            delay(350)
            savePageImmediately(pageId)
        }
        pageSaveJobs[pageId] = job
    }

    fun savePageImmediately(pageId: Long) {
        val pageData = _uiState.value.pagesData[pageId] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveStrokesForPage(pageId, pageData.strokes)
            repository.saveShapesForPage(pageId, pageData.shapes)
            repository.saveTextItemsForPage(pageId, pageData.textItems)
            repository.saveMediaItemsForPage(pageId, pageData.mediaItems)
        }
    }

    private fun scheduleAutoSave() {
        val currentId = currentPage?.id
        if (currentId != null) {
            scheduleAutoSaveForPage(currentId)
        } else {
            autoSaveJob?.cancel()
            autoSaveJob = viewModelScope.launch {
                delay(350)
                saveCurrentPageImmediately()
            }
        }
    }

    fun saveCurrentPageImmediately() {
        val page = currentPage ?: return
        savePageImmediately(page.id)
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

    fun exportAsPdf(selectedPageIds: List<Long>? = null, onShared: (File) -> Unit) {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            saveAllPendingChangesImmediately()
            val allPages = repository.getPagesList(nb.id)
            val pages = if (!selectedPageIds.isNullOrEmpty()) {
                allPages.filter { selectedPageIds.contains(it.id) }
            } else {
                allPages
            }
            val pdfFile = PdfManager.exportNotebookToPdf(
                context = getApplication(),
                notebookTitle = nb.title,
                pages = pages,
                repository = repository,
                pagesDataMap = _uiState.value.pagesData
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

    fun saveAllPendingChangesImmediately() {
        val currentMap = _uiState.value.pagesData
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            currentMap.forEach { (pageId, pageData) ->
                repository.saveStrokesForPage(pageId, pageData.strokes)
                repository.saveShapesForPage(pageId, pageData.shapes)
                repository.saveTextItemsForPage(pageId, pageData.textItems)
                repository.saveMediaItemsForPage(pageId, pageData.mediaItems)
            }
        }
    }

    fun manualSaveNote() {
        saveAllPendingChangesImmediately()
        _uiState.update { it.copy(saveNotificationMessage = "All changes saved locally to device") }
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(saveNotificationMessage = null) }
        }
    }

    fun setPalmRejectionMode(mode: PalmRejectionMode) {
        settingsManager.palmRejectionMode = mode
        _uiState.update { it.copy(palmRejectionMode = mode) }
    }

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun speakCurrentNotes() {
        val nb = _uiState.value.notebook ?: return
        viewModelScope.launch {
            val payload = gatherNotebookMultimodalPayload()

            if (payload.hasInkOrDrawings) {
                // Check memory cache first for instant playback
                val cached = ttsTranscriptionCache[nb.id]
                if (!cached.isNullOrBlank()) {
                    _uiState.update { it.copy(saveNotificationMessage = "Reading note aloud (from cached transcription)") }
                    ttsManager.speak(cached)
                    delay(2500)
                    _uiState.update { it.copy(saveNotificationMessage = null) }
                    return@launch
                }

                // Initial transcription of handwritten ink requires online connectivity
                if (!aiSmartEngine.isOnline()) {
                    _uiState.update {
                        it.copy(saveNotificationMessage = "Reading handwritten notes aloud requires an internet connection for initial AI transcription.")
                    }
                    delay(3500)
                    _uiState.update { it.copy(saveNotificationMessage = null) }
                    return@launch
                }

                _uiState.update { it.copy(saveNotificationMessage = "Transcribing handwritten notes with AI...") }
                try {
                    val transcribed = aiSmartEngine.transcribeNotesForTts(
                        notebookTitle = nb.title,
                        pageImages = payload.pageImages,
                        notesContent = payload.typedText
                    )
                    if (transcribed.isNotBlank()) {
                        ttsTranscriptionCache[nb.id] = transcribed
                        ttsManager.speak(transcribed)
                        _uiState.update { it.copy(saveNotificationMessage = "Reading note aloud") }
                    } else {
                        _uiState.update { it.copy(saveNotificationMessage = "Couldn't read handwritten text to speak aloud") }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(saveNotificationMessage = e.message ?: "Failed to transcribe notes for reading aloud")
                    }
                }
                delay(3000)
                _uiState.update { it.copy(saveNotificationMessage = null) }
            } else if (payload.typedText.isNotBlank()) {
                // Purely typed text note — works completely offline via on-device TTS engine
                ttsManager.speak(payload.typedText)
                _uiState.update { it.copy(saveNotificationMessage = "Reading note aloud") }
                delay(2000)
                _uiState.update { it.copy(saveNotificationMessage = null) }
            } else {
                _uiState.update { it.copy(saveNotificationMessage = "No content to read aloud — write or type notes first") }
                delay(2500)
                _uiState.update { it.copy(saveNotificationMessage = null) }
            }
        }
    }

    fun stopSpeakingNotes() {
        ttsManager.stop()
    }

    fun downloadPdfToDevice(context: Context, selectedPageIds: List<Long>? = null) {
        val nb = _uiState.value.notebook ?: return
        val allPages = _uiState.value.pages
        val pages = if (!selectedPageIds.isNullOrEmpty()) {
            allPages.filter { selectedPageIds.contains(it.id) }
        } else {
            allPages
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                saveAllPendingChangesImmediately()
                val tempPdf = withContext(Dispatchers.IO) {
                    PdfManager.exportNotebookToPdf(
                        context = context,
                        notebookTitle = nb.title,
                        pages = pages,
                        repository = repository,
                        pagesDataMap = _uiState.value.pagesData
                    )
                }
                val safeTitle = nb.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val fileName = "${safeTitle}_${System.currentTimeMillis()}.pdf"
                val savedUri = withContext(Dispatchers.IO) {
                    DownloadUtils.saveFileToDownloads(
                        context = context,
                        sourceFile = tempPdf,
                        targetFileName = fileName,
                        mimeType = "application/pdf"
                    )
                }
                val msg = if (savedUri != null) "Saved to Downloads: $fileName" else "Failed to save PDF to Downloads"
                _uiState.update { it.copy(isExporting = false, showExportDialog = false, saveNotificationMessage = msg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, saveNotificationMessage = "Export failed: ${e.message}") }
            }
            delay(3000)
            _uiState.update { it.copy(saveNotificationMessage = null) }
        }
    }

    fun downloadRecordingToDevice(context: Context, recording: AudioRecordingEntity) {
        viewModelScope.launch {
            try {
                val sourceFile = File(recording.filePath)
                if (sourceFile.exists()) {
                    val safeTitle = recording.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                    val fileName = "${safeTitle}_${System.currentTimeMillis()}.m4a"
                    val savedUri = withContext(Dispatchers.IO) {
                        DownloadUtils.saveFileToDownloads(
                            context = context,
                            sourceFile = sourceFile,
                            targetFileName = fileName,
                            mimeType = "audio/mp4"
                        )
                    }
                    val msg = if (savedUri != null) "Saved recording to Downloads: $fileName" else "Failed to save recording"
                    _uiState.update { it.copy(saveNotificationMessage = msg) }
                } else {
                    _uiState.update { it.copy(saveNotificationMessage = "Recording file not found") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saveNotificationMessage = "Save failed: ${e.message}") }
            }
            delay(3000)
            _uiState.update { it.copy(saveNotificationMessage = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveAllPendingChangesImmediately()
        ttsManager.release()
        recorderManager.stopRecording()
        playerManager.stop()
    }
}
