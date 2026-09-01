package com.example.inknote.ui.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inknote.data.model.ToolType
import com.example.inknote.ui.canvas.components.*
import com.example.inknote.viewmodel.NoteCanvasViewModel
import kotlinx.coroutines.launch

@Composable
fun NoteCanvasScreen(
    notebookId: Long,
    viewModel: NoteCanvasViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(notebookId) {
        viewModel.initNotebook(notebookId)
    }

    val pageCount = uiState.pages.size.coerceAtLeast(1)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.currentPageIndex.coerceIn(0, pageCount - 1)
    )

    // Sync listState -> viewModel.currentPageIndex when user continuously scrolls
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex != uiState.currentPageIndex && listState.firstVisibleItemIndex in uiState.pages.indices) {
            viewModel.setCurrentPageIndex(listState.firstVisibleItemIndex)
        }
    }

    // Sync viewModel.currentPageIndex -> listState when changed externally (e.g. page jump or added new page)
    LaunchedEffect(uiState.currentPageIndex) {
        if (uiState.currentPageIndex in 0 until pageCount && listState.firstVisibleItemIndex != uiState.currentPageIndex) {
            listState.animateScrollToItem(uiState.currentPageIndex)
        }
    }

    // Permission launcher for audio recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startAudioRecording()
        }
    }

    // Media image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addMediaImage(uri)
        }
    }

    var showTemplateDialog by remember { mutableStateOf(false) }
    var showAudioListDialog by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var isFitToScreen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                CanvasTopAppBar(
                    notebookTitle = uiState.notebook?.title ?: "Note",
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    isPalmRejectionActive = uiState.isPalmRejectionEnabled,
                    isLocked = uiState.notebook?.isLocked == true,
                    onBack = {
                        viewModel.saveCurrentPageImmediately()
                        onNavigateBack()
                    },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onExport = { viewModel.showExportDialog(true) },
                    onOpenAudioList = { showAudioListDialog = true },
                    onOpenStudyHub = { viewModel.openFlashcardsModal() },
                    onOpenSummary = { viewModel.openSummaryModal() },
                    onOpenSnapshots = { viewModel.openSnapshotsModal() },
                    onOpenSecurity = { viewModel.openSecurityModal() },
                    onTogglePalmRejection = { viewModel.togglePalmRejection() }
                )

                // Top Audio Bar (Record & Scrubbing controls)
                AudioRecordingBar(
                    isRecording = uiState.isRecordingAudio,
                    recordingElapsedMs = uiState.recordingElapsedMs,
                    isPlaying = uiState.isPlayingAudio,
                    playbackCurrentMs = uiState.playbackCurrentMs,
                    playbackTotalMs = uiState.playbackTotalMs,
                    playbackSpeed = uiState.playbackSpeed,
                    recordingsCount = uiState.recordings.size,
                    onStartRecording = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            viewModel.startAudioRecording()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = { viewModel.stopAudioRecording() },
                    onTogglePlay = { viewModel.toggleAudioPlayback() },
                    onSeek = { viewModel.seekAudio(it) },
                    onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                    onOpenRecordings = { showAudioListDialog = true }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFE2E8F0))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            isFitToScreen = false
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.5f)
                        }
                    }
                }
        ) {
            // Continuous Vertical Scrollable Canvas (A4 aspect-ratio virtualized stream)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(
                    count = uiState.pages.size,
                    key = { index -> uiState.pages[index].id }
                ) { pageIndex ->
                    val pageEntity = uiState.pages.getOrNull(pageIndex)
                    val isCurrentActivePage = pageIndex == uiState.currentPageIndex
                    val pageData = uiState.pagesData[pageEntity?.id ?: -1L]

                    val targetPageId = pageEntity?.id ?: 0L
                    val pageStrokes = pageData?.strokes ?: emptyList()
                    val pageShapes = pageData?.shapes ?: emptyList()
                    val pageTexts = pageData?.textItems ?: emptyList()
                    val pageMedias = pageData?.mediaItems ?: emptyList()

                    InkDrawingCanvas(
                        page = pageEntity,
                        strokes = pageStrokes,
                        shapes = pageShapes,
                        textItems = pageTexts,
                        mediaItems = pageMedias,
                        activeTool = uiState.activeTool,
                        penColor = uiState.penColor,
                        penWidth = uiState.penWidth,
                        highlighterColor = uiState.highlighterColor,
                        highlighterWidth = uiState.highlighterWidth,
                        eraserMode = uiState.eraserMode,
                        eraserWidth = uiState.eraserWidth,
                        isMagicInkEnabled = uiState.isMagicInkEnabled,
                        isGridSnapEnabled = uiState.isGridSnapEnabled,
                        isPalmRejectionEnabled = uiState.isPalmRejectionEnabled,
                        rulerState = uiState.rulerState,
                        protractorState = uiState.protractorState,
                        compassState = uiState.compassState,
                        activeShapeType = uiState.activeShapeType,
                        shapeStrokeColor = uiState.shapeStrokeColor,
                        shapeFillColor = uiState.shapeFillColor,
                        shapeStrokeWidth = uiState.shapeStrokeWidth,
                        shapeIsFilled = uiState.shapeIsFilled,
                        shapeSideCount = uiState.shapeSideCount,
                        playbackCurrentMs = uiState.playbackCurrentMs,
                        isPlayingAudio = uiState.isPlayingAudio,
                        zoomScale = zoomScale,
                        isFitToScreen = isFitToScreen,
                        hasLassoSelection = if (isCurrentActivePage) uiState.hasSelection else false,
                        lassoSelectionBounds = if (isCurrentActivePage) uiState.lassoSelectionBounds else null,
                        onPinchZoom = { delta ->
                            isFitToScreen = false
                            zoomScale = (zoomScale * delta).coerceIn(0.5f, 3.5f)
                        },
                        onAddStroke = { viewModel.addStrokeToPage(targetPageId, it) },
                        onAddShape = { viewModel.addShapeToPage(targetPageId, it) },
                        onEraseAt = { point, rad -> viewModel.eraseAtPage(targetPageId, point, rad) },
                        onAddTextItem = { x, y -> viewModel.addTextItemToPage(targetPageId, x, y) },
                        onUpdateTextItem = { viewModel.updateTextItemOnPage(targetPageId, it) },
                        onDeleteTextItem = { viewModel.deleteTextItemOnPage(targetPageId, it) },
                        onUpdateMediaItem = { viewModel.updateMediaItemOnPage(targetPageId, it) },
                        onDeleteMediaItem = { viewModel.deleteMediaItemOnPage(targetPageId, it) },
                        onJumpAudioToTimestamp = { viewModel.jumpAudioToTimestamp(it) },
                        onLassoComplete = { viewModel.handleLassoSelection(it) },
                        onStartMoveLasso = { viewModel.startMovingLassoSelection() },
                        onMoveLasso = { dx, dy -> viewModel.moveLassoSelection(dx, dy) },
                        onFinishMoveLasso = { viewModel.finishMovingLassoSelection() },
                        onDuplicateLasso = { viewModel.duplicateLassoSelection() },
                        onDeleteLasso = { viewModel.deleteLassoSelection() },
                        onClearLasso = { viewModel.clearLassoSelection() },
                        onConvertLassoToText = { viewModel.convertLassoToText() },
                        onConvertLassoToMath = { viewModel.convertLassoToMath() }
                    )
                }
            }

            // Geometric Tool Overlays
            RulerOverlay(
                rulerState = uiState.rulerState,
                onUpdate = { x, y, angle -> viewModel.updateRuler(x, y, angle) },
                onClose = { viewModel.toggleRuler() }
            )

            ProtractorOverlay(
                protractorState = uiState.protractorState,
                onUpdate = { x, y, rot -> viewModel.updateProtractor(x, y, rot) },
                onClose = { viewModel.toggleProtractor() }
            )

            CompassOverlay(
                compassState = uiState.compassState,
                onUpdate = { px, py, r, a -> viewModel.updateCompass(px, py, r, a) },
                onClose = { viewModel.toggleCompass() }
            )

            // Dockable Toolbar at the Top-Center
            DockableToolbar(
                activeTool = uiState.activeTool,
                penColor = uiState.penColor,
                penWidth = uiState.penWidth,
                highlighterColor = uiState.highlighterColor,
                highlighterWidth = uiState.highlighterWidth,
                isMagicInkEnabled = uiState.isMagicInkEnabled,
                isGridSnapEnabled = uiState.isGridSnapEnabled,
                rulerActive = uiState.rulerState.isVisible,
                protractorActive = uiState.protractorState.isVisible,
                compassActive = uiState.compassState.isVisible,
                onSelectTool = { viewModel.selectTool(it) },
                onSetPenColor = { viewModel.setPenColor(it) },
                onSetPenWidth = { viewModel.setPenWidth(it) },
                onSetHighlighterColor = { viewModel.setHighlighterColor(it) },
                onToggleMagicInk = { viewModel.toggleMagicInk() },
                onToggleGridSnap = { viewModel.toggleGridSnap() },
                onToggleRuler = { viewModel.toggleRuler() },
                onToggleProtractor = { viewModel.toggleProtractor() },
                onToggleCompass = { viewModel.toggleCompass() },
                onSelectShapeType = { viewModel.setShapeType(it) },
                onOpenShapeProperties = { viewModel.showShapeProperties(true) },
                onPickImage = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )

            // Bottom Floating Controls: Page Navigator & Zoom Controls Bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageNavigatorBar(
                    currentPage = uiState.currentPageIndex,
                    totalPages = pageCount,
                    onAddPage = { viewModel.addNewPage() },
                    onChangeTemplate = { showTemplateDialog = true }
                )

                ZoomControlsBar(
                    zoomScale = zoomScale,
                    isFitToScreen = isFitToScreen,
                    onZoomIn = {
                        isFitToScreen = false
                        zoomScale = (zoomScale + 0.15f).coerceAtMost(4.0f)
                    },
                    onZoomOut = {
                        isFitToScreen = false
                        zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.5f)
                    },
                    onResetZoom = {
                        isFitToScreen = false
                        zoomScale = 1.0f
                    },
                    onToggleFitToScreen = {
                        isFitToScreen = !isFitToScreen
                        if (isFitToScreen) {
                            zoomScale = 1.0f
                        }
                    }
                )
            }
        }
    }

    // Dialogs & Modals
    if (showTemplateDialog) {
        PageTemplateDialog(
            currentTemplate = viewModel.currentPage?.templateType ?: "BLANK",
            totalPages = uiState.pages.size,
            onSelectTemplate = {
                viewModel.setPageTemplate(it)
                showTemplateDialog = false
            },
            onDeletePage = {
                viewModel.deleteCurrentPage()
                showTemplateDialog = false
            },
            onDismiss = { showTemplateDialog = false }
        )
    }

    if (uiState.showShapePropertyDialog) {
        ShapePropertiesDialog(
            strokeColor = uiState.shapeStrokeColor,
            fillColor = uiState.shapeFillColor,
            strokeWidth = uiState.shapeStrokeWidth,
            isFilled = uiState.shapeIsFilled,
            sideCount = uiState.shapeSideCount,
            onApply = { sc, fc, sw, f, scount ->
                viewModel.setShapeProperties(sc, fc, sw, f, scount)
            },
            onDismiss = { viewModel.showShapeProperties(false) }
        )
    }

    if (uiState.showExportDialog) {
        ExportDialog(
            isExporting = uiState.isExporting,
            onExportPdf = {
                viewModel.exportAsPdf { file ->
                    viewModel.shareFile(context, file, "application/pdf")
                }
            },
            onDismiss = { viewModel.showExportDialog(false) }
        )
    }

    if (showAudioListDialog) {
        AudioRecordingsListDialog(
            recordings = uiState.recordings,
            activeRecordingId = uiState.activeRecordingId,
            isPlaying = uiState.isPlayingAudio,
            onPlayRecording = { viewModel.playAudioRecording(it) },
            onDeleteRecording = { viewModel.deleteAudioRecording(it) },
            onRenameRecording = { rec, title -> viewModel.renameAudioRecording(rec, title) },
            onTranscribeRecording = {
                showAudioListDialog = false
                viewModel.transcribeRecording(it)
            },
            onDismiss = { showAudioListDialog = false }
        )
    }

    if (uiState.showTranscriptionDialog) {
        TranscriptionDialog(
            transcriptionResult = uiState.transcriptionResult,
            isLoading = uiState.isTranscribing,
            onInsertAsTextBox = { text ->
                viewModel.insertTranscribedText(text)
            },
            onDismiss = { viewModel.dismissTranscriptionDialog() }
        )
    }

    // AI Study & Productivity Modals
    if (uiState.showFlashcardsModal) {
        FlashcardStudyModal(
            notebookTitle = uiState.notebook?.title ?: "Note",
            flashcards = uiState.flashcards,
            isLoading = uiState.isAiProcessing,
            errorMessage = uiState.aiErrorMessage,
            onGenerateWithAi = { viewModel.generateFlashcardsWithAi() },
            onMarkMastered = { /* Marked mastered */ },
            onMarkReview = { /* Marked for review */ },
            onDismiss = { viewModel.closeFlashcardsModal() }
        )
    }

    if (uiState.showQuizModal) {
        InteractiveQuizModal(
            notebookTitle = uiState.notebook?.title ?: "Note",
            quizQuestions = uiState.quizQuestions,
            isLoading = uiState.isAiProcessing,
            errorMessage = uiState.aiErrorMessage,
            onGenerateQuiz = { viewModel.generateQuizWithAi() },
            onDismiss = { viewModel.closeQuizModal() }
        )
    }

    if (uiState.showSummaryModal) {
        AiSummaryModal(
            summaryResult = uiState.noteSummary,
            isLoading = uiState.isAiProcessing,
            errorMessage = uiState.aiErrorMessage,
            onInsertAsText = { text -> viewModel.insertTranscribedText(text) },
            onDismiss = { viewModel.closeSummaryModal() }
        )
    }

    if (uiState.showSnapshotsModal) {
        PageSnapshotsModal(
            snapshots = uiState.snapshots,
            onSaveCurrentSnapshot = { label -> viewModel.saveCurrentSnapshot(label) },
            onRestoreSnapshot = { snapshot -> viewModel.restoreSnapshot(snapshot) },
            onDeleteSnapshot = { snapshot -> viewModel.deleteSnapshot(snapshot) },
            onDismiss = { viewModel.closeSnapshotsModal() }
        )
    }

    if (uiState.showSecurityModal) {
        SecurityPinDialog(
            currentPin = uiState.notebook?.pinCode,
            isUnlocking = false,
            onVerifyOrSetPin = { pin -> viewModel.setNotebookPin(pin) },
            onRemovePin = { viewModel.removeNotebookPin() },
            onDismiss = { viewModel.closeSecurityModal() }
        )
    }
}
