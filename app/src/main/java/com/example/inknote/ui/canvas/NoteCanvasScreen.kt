package com.example.inknote.ui.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inknote.data.model.ToolType
import com.example.inknote.ui.canvas.components.*
import com.example.inknote.ui.settings.AppSettingsDialog
import com.example.inknote.viewmodel.NoteCanvasViewModel
import kotlinx.coroutines.delay
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

    // Sync viewModel.currentPageIndex -> listState when changed externally (instant cut for distant jumps!)
    LaunchedEffect(uiState.currentPageIndex) {
        if (uiState.currentPageIndex in 0 until pageCount && listState.firstVisibleItemIndex != uiState.currentPageIndex) {
            val diff = kotlin.math.abs(listState.firstVisibleItemIndex - uiState.currentPageIndex)
            if (diff > 1) {
                listState.scrollToItem(uiState.currentPageIndex) // Instant cut, no slow animation!
            } else {
                listState.animateScrollToItem(uiState.currentPageIndex)
            }
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
    var showAudioControls by remember { mutableStateOf(false) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var previousPageBeforeJump by remember { mutableStateOf<Int?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var isFitToScreen by remember { mutableStateOf(false) }
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var lastBottomBarInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-hide combined bottom bar after ~1.2s of inactivity
    LaunchedEffect(lastBottomBarInteraction, isBottomBarVisible) {
        if (isBottomBarVisible) {
            delay(1200L)
            isBottomBarVisible = false
        }
    }

    // Auto-dismiss previous jump affordance after 8 seconds
    LaunchedEffect(previousPageBeforeJump) {
        if (previousPageBeforeJump != null) {
            delay(8000L)
            previousPageBeforeJump = null
        }
    }

    // Bring bottom bar back smoothly when user scrolls
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isBottomBarVisible = true
            lastBottomBarInteraction = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            CanvasTopAppBar(
                notebookTitle = uiState.notebook?.title ?: "Note",
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                isLocked = uiState.notebook?.isLocked == true,
                isSpeakingNotes = uiState.isSpeakingTts,
                isRecordingAudio = uiState.isRecordingAudio,
                isPlayingAudio = uiState.isPlayingAudio,
                isAudioControlsOpen = showAudioControls,
                onBack = {
                    viewModel.saveCurrentPageImmediately()
                    onNavigateBack()
                },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onExport = { viewModel.showExportDialog(true) },
                onToggleAudioControls = { showAudioControls = !showAudioControls },
                onOpenAudioList = { showAudioListDialog = true },
                onRenameTitle = { showRenameDialog = true },
                onOpenStudyHub = { viewModel.openFlashcardsModal() },
                onOpenSummary = { viewModel.openSummaryModal() },
                onOpenSnapshots = { viewModel.openSnapshotsModal() },
                onOpenSecurity = { viewModel.openSecurityModal() },
                onManualSave = { viewModel.manualSaveNote() },
                onSpeakNotes = { viewModel.speakCurrentNotes() },
                onStopSpeakingNotes = { viewModel.stopSpeakingNotes() },
                onOpenSettings = { viewModel.setShowSettingsDialog(true) }
            )
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
                        laserColor = uiState.laserColor,
                        isLaserTailEnabled = uiState.isLaserTailEnabled,
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
                        palmRejectionMode = uiState.palmRejectionMode,
                        sharedStylusInputState = viewModel.stylusInputState,
                        hasLassoSelection = if (isCurrentActivePage) uiState.hasSelection else false,
                        lassoSelectionBounds = if (isCurrentActivePage) uiState.lassoSelectionBounds else null,
                        lassoSelectedStrokes = if (isCurrentActivePage) uiState.lassoSelectedStrokes else emptyList(),
                        lassoSelectedShapes = if (isCurrentActivePage) uiState.lassoSelectedShapes else emptyList(),
                        lassoSelectedTextItems = if (isCurrentActivePage) uiState.lassoSelectedTextItems else emptyList(),
                        lassoSelectedMediaItems = if (isCurrentActivePage) uiState.lassoSelectedMediaItems else emptyList(),
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
                        onLassoComplete = { viewModel.handleLassoSelection(it, targetPageId) },
                        onMoveLasso = { dx, dy -> viewModel.moveLassoSelection(dx, dy, targetPageId) },
                        onDuplicateLasso = { viewModel.duplicateLassoSelection(targetPageId) },
                        onDeleteLasso = { viewModel.deleteLassoSelection(targetPageId) }
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

            // Floating Audio Recording & Playback Overlay
            AnimatedVisibility(
                visible = showAudioControls || uiState.isRecordingAudio || uiState.isPlayingAudio,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp, start = 8.dp, end = 8.dp)
                    .zIndex(15f)
            ) {
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
                    onOpenRecordings = { showAudioListDialog = true },
                    onClose = { showAudioControls = false }
                )
            }

            // Dockable Toolbar Floating Over the Canvas
            DockableToolbar(
                activeTool = uiState.activeTool,
                activeShapeType = uiState.activeShapeType,
                penColor = uiState.penColor,
                penWidth = uiState.penWidth,
                highlighterColor = uiState.highlighterColor,
                highlighterWidth = uiState.highlighterWidth,
                eraserWidth = uiState.eraserWidth,
                laserColor = uiState.laserColor,
                isLaserTailEnabled = uiState.isLaserTailEnabled,
                isMagicInkEnabled = uiState.isMagicInkEnabled,
                isGridSnapEnabled = uiState.isGridSnapEnabled,
                rulerActive = uiState.rulerState.isVisible,
                protractorActive = uiState.protractorState.isVisible,
                compassActive = uiState.compassState.isVisible,
                onSelectTool = { viewModel.selectTool(it) },
                onSetPenColor = { viewModel.setPenColor(it) },
                onSetPenWidth = { viewModel.setPenWidth(it) },
                onSetHighlighterColor = { viewModel.setHighlighterColor(it) },
                onSetHighlighterWidth = { viewModel.setHighlighterWidth(it) },
                onSetEraserWidth = { viewModel.setEraserWidth(it) },
                onSetLaserColor = { viewModel.setLaserColor(it) },
                onToggleLaserTail = { viewModel.toggleLaserTail() },
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
                    .padding(top = if (showAudioControls || uiState.isRecordingAudio || uiState.isPlayingAudio) 66.dp else 6.dp)
                    .zIndex(10f)
            )

            // Optional hop-back button if a jump was performed
            if (previousPageBeforeJump != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 58.dp)
                        .zIndex(14f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Page ${uiState.currentPageIndex + 1}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            onClick = {
                                val target = previousPageBeforeJump!!
                                previousPageBeforeJump = null
                                coroutineScope.launch {
                                    listState.scrollToItem(target)
                                    viewModel.setCurrentPageIndex(target)
                                }
                                isBottomBarVisible = true
                                lastBottomBarInteraction = System.currentTimeMillis()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Return to Page ${previousPageBeforeJump!! + 1}",
                                color = MaterialTheme.colorScheme.primaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { previousPageBeforeJump = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Bottom tap-to-reveal affordance when bar is hidden
            if (!isBottomBarVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable {
                            isBottomBarVisible = true
                            lastBottomBarInteraction = System.currentTimeMillis()
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .width(44.dp)
                            .height(4.dp)
                            .background(Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )
                }
            }

            // Slim Combined Bottom Bar (Page Navigator & Zoom Controls with Auto-Hide)
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .zIndex(12f)
            ) {
                CombinedBottomBar(
                    currentPage = uiState.currentPageIndex,
                    totalPages = pageCount,
                    zoomScale = zoomScale,
                    isFitToScreen = isFitToScreen,
                    onJumpToPage = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        showJumpToPageDialog = true
                    },
                    onAddPage = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        viewModel.addNewPage()
                    },
                    onChangeTemplate = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        showTemplateDialog = true
                    },
                    onZoomIn = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        isFitToScreen = false
                        zoomScale = (zoomScale + 0.15f).coerceAtMost(4.0f)
                    },
                    onZoomOut = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        isFitToScreen = false
                        zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.5f)
                    },
                    onResetZoom = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        isFitToScreen = false
                        zoomScale = 1.0f
                    },
                    onToggleFitToScreen = {
                        lastBottomBarInteraction = System.currentTimeMillis()
                        isFitToScreen = !isFitToScreen
                        if (isFitToScreen) {
                            zoomScale = 1.0f
                        }
                    }
                )
            }

            // Save status notification banner
            if (uiState.saveNotificationMessage != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp)
                        .zIndex(18f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = uiState.saveNotificationMessage ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
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
            pages = uiState.pages,
            currentPageIndex = uiState.currentPageIndex,
            onExportPdf = { selectedIds ->
                viewModel.exportAsPdf(selectedIds) { file ->
                    viewModel.shareFile(context, file, "application/pdf")
                }
            },
            onSaveToDownloads = { selectedIds ->
                viewModel.downloadPdfToDevice(context, selectedIds)
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
            onDownloadRecording = { viewModel.downloadRecordingToDevice(context, it) },
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

    if (uiState.showSettingsDialog) {
        AppSettingsDialog(
            currentPalmMode = uiState.palmRejectionMode,
            onSetPalmMode = { viewModel.setPalmRejectionMode(it) },
            onDismiss = { viewModel.setShowSettingsDialog(false) }
        )
    }

    if (showJumpToPageDialog) {
        JumpToPageDialog(
            currentPage = uiState.currentPageIndex,
            totalPages = pageCount,
            onJump = { targetIndex ->
                val prev = uiState.currentPageIndex
                previousPageBeforeJump = prev
                coroutineScope.launch {
                    listState.scrollToItem(targetIndex)
                    viewModel.setCurrentPageIndex(targetIndex)
                }
                isBottomBarVisible = true
                lastBottomBarInteraction = System.currentTimeMillis()
            },
            onDismiss = { showJumpToPageDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameNoteDialog(
            currentTitle = uiState.notebook?.title ?: "Note",
            onRename = { newTitle ->
                viewModel.renameNotebook(newTitle)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}
