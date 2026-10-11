package com.example.inknote.ui.canvas.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.ToolType
import com.example.ui.theme.NoteInkColors
import com.example.inknote.viewmodel.EraserMode
import com.example.inknote.ui.theme.EraserIcon
import com.example.inknote.ui.theme.FountainPenIcon
import com.example.inknote.ui.theme.GeometricShapesIcon
import com.example.inknote.ui.theme.HighlighterMarkerIcon
import com.example.inknote.ui.theme.LaserPointerIcon
import com.example.inknote.ui.theme.LassoIcon
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasTopAppBar(
    notebookTitle: String,
    canUndo: Boolean,
    canRedo: Boolean,
    isLocked: Boolean,
    isSpeakingNotes: Boolean = false,
    isRecordingAudio: Boolean = false,
    isPlayingAudio: Boolean = false,
    isAudioControlsOpen: Boolean = false,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenCalculator: () -> Unit = {},
    onExport: () -> Unit,
    onToggleAudioControls: () -> Unit,
    onOpenAudioList: () -> Unit,
    onRenameTitle: () -> Unit = {},
    onTranscribe: () -> Unit = {},
    onOpenStudyHub: () -> Unit = {},
    onOpenSummary: () -> Unit = {},
    onOpenSnapshots: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onManualSave: () -> Unit = {},
    onSpeakNotes: () -> Unit = {},
    onStopSpeakingNotes: () -> Unit = {},
    onAddPdf: () -> Unit = {},
    onResetToolbarPosition: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Surface(
                onClick = onRenameTitle,
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = notebookTitle,
                        maxLines = 1,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename Note",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    if (isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Protected Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Calculator button (Inkstead Calculator Popup - positioned next to Undo)
            IconButton(
                onClick = onOpenCalculator,
                modifier = Modifier.testTag("inkstead_calculator_button")
            ) {
                Icon(
                    Icons.Default.Calculate,
                    contentDescription = "Inkstead Calculator",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Undo (frequently used - directly visible)
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else Color.LightGray
                )
            }
            // Redo (frequently used - directly visible)
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface else Color.LightGray
                )
            }
            // Compact microphone / record icon (directly visible)
            IconButton(
                onClick = onToggleAudioControls,
                modifier = Modifier
                    .background(
                        if (isRecordingAudio) Color(0xFFDC2626).copy(alpha = 0.16f)
                        else if (isAudioControlsOpen || isPlayingAudio) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else Color.Transparent,
                        CircleShape
                    )
            ) {
                if (isRecordingAudio) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Recording active (Tap to toggle audio controls)",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Audio Recording & Playback",
                        tint = if (isAudioControlsOpen || isPlayingAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Share / Export (frequently used - directly visible)
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, contentDescription = "Share / Export", tint = MaterialTheme.colorScheme.primary)
            }
            // Three-dots overflow menu (contains AI, Quiz, TTS, and extra options)
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Note Actions")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename Note") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onRenameTitle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("AI Summary & Insights") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6366F1)) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSummary()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Flashcards & Study Quizzes") },
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showMoreMenu = false
                            onOpenStudyHub()
                        }
                    )
                    if (isSpeakingNotes) {
                        DropdownMenuItem(
                            text = { Text("Stop Reading Aloud") },
                            leadingIcon = { Icon(Icons.Default.VolumeOff, contentDescription = null, tint = Color(0xFFDC2626)) },
                            onClick = {
                                showMoreMenu = false
                                onStopSpeakingNotes()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Read Note Aloud (TTS)") },
                            leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMoreMenu = false
                                onSpeakNotes()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Audio Recordings List") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenAudioList()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add PDF to Note") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showMoreMenu = false
                            onAddPdf()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Save Note Now") },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onManualSave()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Version History & Snapshots") },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSnapshots()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isLocked) "Manage Passcode PIN" else "Lock with Passcode") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSecurity()
                        }
                    )
                    HorizontalDivider()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isDarkPaper by com.example.inknote.data.preferences.ThemeStateHolder.isDarkPaperEnabled.collectAsState()
                    DropdownMenuItem(
                        text = { Text(if (isDarkPaper) "Switch to Light Paper" else "Switch to Dark Paper") },
                        leadingIcon = {
                            Icon(
                                if (isDarkPaper) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            com.example.inknote.data.preferences.ThemeStateHolder.setDarkPaperEnabled(context, !isDarkPaper)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reset Toolbar Position") },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.testTag("reset_toolbar_menu_item"),
                        onClick = {
                            showMoreMenu = false
                            onResetToolbarPosition()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings & Appearance") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSettings()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun AudioRecordingBar(
    isRecording: Boolean,
    recordingElapsedMs: Long,
    isPlaying: Boolean,
    playbackCurrentMs: Long,
    playbackTotalMs: Long,
    playbackSpeed: Float,
    recordingsCount: Int = 0,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onTranscribe: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Record button & Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Record Audio",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isRecording) {
                    Text(
                        text = "REC ${formatDuration(recordingElapsedMs)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626),
                        fontSize = 13.sp
                    )
                } else if (playbackTotalMs > 0L) {
                    Text(
                        text = "Voice Note",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            // Center/Right: Player Controls if audio recording exists
            if (playbackTotalMs > 0L && !isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Scrubber Slider
                    Slider(
                        value = playbackCurrentMs.toFloat().coerceIn(0f, playbackTotalMs.toFloat()),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..playbackTotalMs.toFloat(),
                        modifier = Modifier.weight(1f).height(20.dp)
                    )

                    Text(
                        text = "${formatDuration(playbackCurrentMs)} / ${formatDuration(playbackTotalMs)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Speed button
                    TextButton(
                        onClick = {
                            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                            val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                            onSetSpeed(speeds[nextIdx])
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(text = "${playbackSpeed}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Right: Recordings List Button & Close Button
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onOpenRecordings,
                    modifier = Modifier.size(32.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (recordingsCount > 0) {
                                Badge { Text(recordingsCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Voice Recordings List",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Audio Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun DockableToolbar(
    activeTool: ToolType,
    penColor: Color,
    penWidth: Float,
    highlighterColor: Color,
    highlighterWidth: Float = 24f,
    activeShapeType: ShapeType = ShapeType.RECTANGLE,
    eraserMode: EraserMode = EraserMode.STROKE,
    eraserWidth: Float = 30f,
    laserColor: Color = Color(0xFFEF4444),
    isLaserTailEnabled: Boolean = true,
    isMagicInkEnabled: Boolean,
    rulerActive: Boolean,
    onSelectTool: (ToolType) -> Unit,
    onSetPenColor: (Color) -> Unit,
    onSetPenWidth: (Float) -> Unit,
    onSetHighlighterColor: (Color) -> Unit,
    onSetHighlighterWidth: (Float) -> Unit = {},
    onSetEraserMode: (EraserMode) -> Unit = {},
    onSetEraserWidth: (Float) -> Unit = {},
    onSetLaserColor: (Color) -> Unit = {},
    onToggleLaserTail: () -> Unit = {},
    onToggleMagicInk: () -> Unit,
    onToggleRuler: () -> Unit,
    onSelectShapeType: (ShapeType) -> Unit,
    onOpenShapeProperties: () -> Unit,
    onPickImage: () -> Unit,
    onAddPdf: () -> Unit = {},
    isCollapsed: Boolean = false,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    resetTrigger: Long = 0L,
    onToggleCollapse: () -> Unit = {},
    onOffsetChanged: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var openSettingsTool by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember(initialOffsetX, initialOffsetY, resetTrigger) {
        mutableStateOf(Offset(initialOffsetX, initialOffsetY))
    }
    LaunchedEffect(initialOffsetX, initialOffsetY, resetTrigger) {
        dragOffset = Offset(initialOffsetX, initialOffsetY)
    }

    // Active tool display helper for collapsed icon
    val activeToolIcon = when (activeTool) {
        ToolType.PEN -> FountainPenIcon
        ToolType.HIGHLIGHTER -> HighlighterMarkerIcon
        ToolType.ERASER -> EraserIcon
        ToolType.LASSO -> LassoIcon
        ToolType.LASER -> LaserPointerIcon
        ToolType.TEXT -> Icons.Default.TextFields
        ToolType.SHAPE -> GeometricShapesIcon
        ToolType.RULER -> Icons.Default.Straighten
        ToolType.MEDIA -> Icons.Default.Image
        ToolType.HAND -> Icons.Default.PanTool
    }

    val activeToolIndicatorColor: Color? = when (activeTool) {
        ToolType.PEN -> penColor
        ToolType.HIGHLIGHTER -> highlighterColor
        ToolType.LASER -> laserColor
        else -> null
    }

    val activeToolLabel = when (activeTool) {
        ToolType.PEN -> "Pen"
        ToolType.HIGHLIGHTER -> "Highlighter"
        ToolType.ERASER -> if (eraserMode == EraserMode.AREA) "Precision Eraser" else "Stroke Eraser"
        ToolType.LASSO -> "Lasso"
        ToolType.LASER -> "Laser"
        ToolType.TEXT -> "Text"
        ToolType.SHAPE -> "Shapes"
        ToolType.RULER -> "Ruler"
        ToolType.MEDIA -> "Image"
        ToolType.HAND -> "Hand"
    }

    // Central tool tap handler:
    // First tap on an inactive tool -> Selects it only, closes any open settings.
    // Second tap on the already active tool -> Opens or toggles its settings panel.
    fun handleToolClick(toolKey: String, isAlreadyActive: Boolean, activate: () -> Unit) {
        if (!isAlreadyActive) {
            openSettingsTool = null
            activate()
        } else {
            openSettingsTool = if (openSettingsTool == toolKey) null else toolKey
        }
    }

    AnimatedContent(
        targetState = isCollapsed,
        modifier = modifier.wrapContentSize(),
        transitionSpec = {
            (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)))
                .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140)))
        },
        label = "ToolbarCollapseExpandTransition"
    ) { collapsed ->
        if (collapsed) {
            // Collapsed Single-Icon Floating Button reflecting the active tool
            Surface(
                modifier = Modifier
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .padding(horizontal = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                tonalElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleCollapse)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    onOffsetChanged(dragOffset.x, dragOffset.y)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val newX = dragOffset.x + dragAmount.x
                                val newY = (dragOffset.y + dragAmount.y).coerceAtLeast(0f)
                                dragOffset = Offset(newX, newY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activeToolIcon,
                        contentDescription = "Expand Toolbar (Current Tool: $activeToolLabel)",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (activeToolIndicatorColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 9.dp, bottom = 9.dp)
                                .size(8.dp)
                                .background(activeToolIndicatorColor, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
            }
        } else {
            // Full Floating Toolbar - compact capsule fitting only its content
            Surface(
                modifier = Modifier
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .padding(horizontal = 4.dp)
                    .widthIn(max = 680.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 8.dp,
                tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Contextual Settings Panel (Only opens on second tap of the active tool)
                    AnimatedVisibility(visible = openSettingsTool != null) {
                        Column(
                            modifier = Modifier
                                .wrapContentSize()
                                .widthIn(max = 520.dp)
                                .padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                    when (openSettingsTool) {
                        "PEN" -> {
                            // Color swatches for Pen
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                items(NoteInkColors) { color ->
                                    val isSelected = penColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                            .clickable { onSetPenColor(color) }
                                    )
                                }
                            }

                            // Stroke Width Continuous Slider with Live Preview Swatch
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Pen Width",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f pt", penWidth),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Live stroke-width preview swatch
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .width(54.dp)
                                            .height(24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(36.dp)
                                                    .height(penWidth.coerceIn(1f, 16f).dp)
                                                    .clip(RoundedCornerShape(penWidth.dp / 2f))
                                                    .background(penColor)
                                            )
                                        }
                                    }
                                }

                                Slider(
                                    value = penWidth.coerceIn(1.0f, 20.0f),
                                    onValueChange = { newWidth ->
                                        onSetPenWidth((newWidth * 10).roundToInt() / 10f)
                                    },
                                    valueRange = 1.0f..20.0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .testTag("pen_width_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = penColor,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }

                        "HIGHLIGHTER" -> {
                            // Color swatches for Highlighter
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                items(NoteInkColors) { color ->
                                    val isSelected = highlighterColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                            .clickable { onSetHighlighterColor(color) }
                                    )
                                }
                            }

                            // Highlighter Width Continuous Slider with Live Preview Swatch
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Highlighter Width",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f pt", highlighterWidth),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Live stroke-width preview swatch
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .width(54.dp)
                                            .height(24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(36.dp)
                                                    .height((highlighterWidth * 0.45f).coerceIn(4f, 18f).dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(highlighterColor.copy(alpha = 0.55f))
                                            )
                                        }
                                    }
                                }

                                Slider(
                                    value = highlighterWidth.coerceIn(8.0f, 48.0f),
                                    onValueChange = { newWidth ->
                                        onSetHighlighterWidth((newWidth * 10).roundToInt() / 10f)
                                    },
                                    valueRange = 8.0f..48.0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .testTag("highlighter_width_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = highlighterColor,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }

                        "ERASER" -> {
                            // Eraser Mode Toggle (Stroke vs Precision Partial Vector Eraser) + Size Controls
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                // Eraser Mode Selector Row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = eraserMode == EraserMode.STROKE,
                                        onClick = { onSetEraserMode(EraserMode.STROKE) },
                                        label = { Text("Stroke Eraser", fontSize = 11.sp, fontWeight = if (eraserMode == EraserMode.STROKE) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = EraserIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    )
                                    FilterChip(
                                        selected = eraserMode == EraserMode.AREA,
                                        onClick = { onSetEraserMode(EraserMode.AREA) },
                                        label = { Text("Precision Partial", fontSize = 11.sp, fontWeight = if (eraserMode == EraserMode.AREA) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Adjust,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            EraserIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Eraser Size: ${eraserWidth.toInt()}pt",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Visual circle preview of current eraser size
                                    Box(
                                        modifier = Modifier.size(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val previewSize = (eraserWidth * 0.35f).coerceIn(6f, 32f).dp
                                        Box(
                                            modifier = Modifier
                                                .size(previewSize)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                    }
                                }

                                // Preset sizes row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        Pair("Small", 16f),
                                        Pair("Medium", 32f),
                                        Pair("Large", 54f),
                                        Pair("X-Large", 84f)
                                    ).forEach { (label, size) ->
                                        val isSelected = kotlin.math.abs(eraserWidth - size) < 4f
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSetEraserWidth(size) },
                                            label = { Text(label, fontSize = 11.sp) },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                // Continuous Slider for granular control
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "Smaller",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { onSetEraserWidth((eraserWidth - 5f).coerceAtLeast(8f)) },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = eraserWidth,
                                        onValueChange = { onSetEraserWidth(it) },
                                        valueRange = 8f..120f,
                                        modifier = Modifier.weight(1f).height(24.dp)
                                    )
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Larger",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { onSetEraserWidth((eraserWidth + 5f).coerceAtMost(120f)) },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        "LASER" -> {
                            val laserColors = listOf(
                                Color(0xFFEF4444), // Red
                                Color(0xFF10B981), // Green
                                Color(0xFF3B82F6), // Blue
                                Color(0xFFA855F7), // Purple
                                Color(0xFFF59E0B)  // Gold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    laserColors.forEach { color ->
                                        val isSelected = laserColor == color
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                )
                                                .clickable { onSetLaserColor(color) }
                                        )
                                    }
                                }

                                // Tail Toggle Button
                                FilterChip(
                                    selected = isLaserTailEnabled,
                                    onClick = onToggleLaserTail,
                                    label = { Text(if (isLaserTailEnabled) "Trail: ON" else "Trail: OFF", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isLaserTailEnabled) Icons.Default.Timeline else Icons.Default.Adjust,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        "LASSO" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Gesture,
                                        contentDescription = null,
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Selection Tool",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Circle ink or shapes to select, resize & move",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        "SHAPE" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                                ) {
                                    val shapeTypes = listOf(
                                        Triple("Rectangle", Icons.Default.CropLandscape, ShapeType.RECTANGLE),
                                        Triple("Square", Icons.Default.CropSquare, ShapeType.SQUARE),
                                        Triple("Circle", Icons.Default.Circle, ShapeType.CIRCLE),
                                        Triple("Line", Icons.Default.HorizontalRule, ShapeType.LINE),
                                        Triple("Arrow", Icons.AutoMirrored.Filled.ArrowRightAlt, ShapeType.ARROW),
                                        Triple("Triangle", Icons.Default.ChangeHistory, ShapeType.TRIANGLE),
                                        Triple("Star", Icons.Default.Star, ShapeType.STAR)
                                    )
                                    items(shapeTypes) { (name, icon, shape) ->
                                        val isSelected = activeShapeType == shape
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSelectShapeType(shape) },
                                            label = { Text(name, fontSize = 11.sp) },
                                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onOpenShapeProperties,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "Shape Properties",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        "RULER" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("Ruler Active (20cm)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        openSettingsTool = null
                                        onToggleRuler()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Hide Ruler", fontSize = 11.sp)
                                }
                            }
                        }

                        "MAGIC_INK" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                                    Text("Magic Ink: Shape Recognition Active", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        openSettingsTool = null
                                        onToggleMagicInk()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Turn Off", fontSize = 11.sp)
                                }
                            }
                        }

                        "TEXT" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Text Tool: Tap on the canvas to place a note", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Main Tool Icon Strip
            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                // Drag handle to reposition toolbar
                item {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        onOffsetChanged(dragOffset.x, dragOffset.y)
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val newX = dragOffset.x + dragAmount.x
                                    val newY = (dragOffset.y + dragAmount.y).coerceAtLeast(0f)
                                    dragOffset = Offset(newX, newY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag to Reposition Toolbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Dedicated Collapse Button (chevron left)
                item {
                    IconButton(
                        onClick = {
                            openSettingsTool = null
                            onToggleCollapse()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Collapse Toolbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Pen Tool: First tap selects, second tap toggles settings
                item {
                    ToolButton(
                        icon = FountainPenIcon,
                        label = "Pen",
                        isSelected = activeTool == ToolType.PEN,
                        indicatorColor = penColor,
                        onClick = {
                            handleToolClick("PEN", activeTool == ToolType.PEN) {
                                onSelectTool(ToolType.PEN)
                            }
                        }
                    )
                }

                // Highlighter Tool: First tap selects, second tap toggles settings
                item {
                    ToolButton(
                        icon = HighlighterMarkerIcon,
                        label = "Highlighter",
                        isSelected = activeTool == ToolType.HIGHLIGHTER,
                        indicatorColor = highlighterColor,
                        onClick = {
                            handleToolClick("HIGHLIGHTER", activeTool == ToolType.HIGHLIGHTER) {
                                onSelectTool(ToolType.HIGHLIGHTER)
                            }
                        }
                    )
                }

                // Eraser Tool: First tap selects, second tap toggles settings
                item {
                    ToolButton(
                        icon = EraserIcon,
                        label = if (eraserMode == EraserMode.AREA) "Precision" else "Eraser",
                        isSelected = activeTool == ToolType.ERASER,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            handleToolClick("ERASER", activeTool == ToolType.ERASER) {
                                onSelectTool(ToolType.ERASER)
                            }
                        }
                    )
                }

                // Lasso Selection Tool: First tap selects, second tap toggles settings
                item {
                    ToolButton(
                        icon = LassoIcon,
                        label = "Lasso",
                        isSelected = activeTool == ToolType.LASSO,
                        activeColor = Color(0xFF6366F1),
                        onClick = {
                            handleToolClick("LASSO", activeTool == ToolType.LASSO) {
                                onSelectTool(ToolType.LASSO)
                            }
                        }
                    )
                }

                // Laser Pointer Tool: First tap selects, second tap toggles settings
                item {
                    ToolButton(
                        icon = LaserPointerIcon,
                        label = "Laser",
                        isSelected = activeTool == ToolType.LASER,
                        indicatorColor = laserColor,
                        activeColor = laserColor,
                        onClick = {
                            handleToolClick("LASER", activeTool == ToolType.LASER) {
                                onSelectTool(ToolType.LASER)
                            }
                        }
                    )
                }

                // Shapes Tool: First tap selects, second tap toggles shape picker & settings
                item {
                    ToolButton(
                        icon = GeometricShapesIcon,
                        label = "Shapes",
                        isSelected = activeTool == ToolType.SHAPE,
                        onClick = {
                            handleToolClick("SHAPE", activeTool == ToolType.SHAPE) {
                                onSelectTool(ToolType.SHAPE)
                            }
                        }
                    )
                }

                // Magic Ink Shape Recognition Toggle: First tap activates, second tap toggles options
                item {
                    ToolButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "Magic Ink",
                        isSelected = isMagicInkEnabled,
                        activeColor = Color(0xFF8B5CF6),
                        onClick = {
                            handleToolClick("MAGIC_INK", isMagicInkEnabled) {
                                onToggleMagicInk()
                            }
                        }
                    )
                }

                // Ruler Tool: First tap activates, second tap toggles options
                item {
                    ToolButton(
                        icon = Icons.Default.Straighten,
                        label = "Ruler",
                        isSelected = rulerActive,
                        onClick = {
                            handleToolClick("RULER", rulerActive) {
                                onToggleRuler()
                            }
                        }
                    )
                }

                // Text Tool: First tap selects, second tap toggles options
                item {
                    ToolButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        isSelected = activeTool == ToolType.TEXT,
                        onClick = {
                            handleToolClick("TEXT", activeTool == ToolType.TEXT) {
                                onSelectTool(ToolType.TEXT)
                            }
                        }
                    )
                }

                // Media / Image Tool
                item {
                    ToolButton(
                        icon = Icons.Default.Image,
                        label = "Image",
                        isSelected = false,
                        onClick = {
                            openSettingsTool = null
                            onPickImage()
                        }
                    )
                }

                // Add PDF Tool
                item {
                    ToolButton(
                        icon = Icons.Default.PictureAsPdf,
                        label = "Add PDF",
                        isSelected = false,
                        onClick = {
                            openSettingsTool = null
                            onAddPdf()
                        }
                    )
                }
            }
        }
    }
    }
}
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    indicatorColor: Color? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .background(
                if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) activeColor else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.BottomEnd)
                        .background(indicatorColor, CircleShape)
                )
            }
        }
    }
}

@Composable
fun PageNavigatorBar(
    currentPage: Int,
    totalPages: Int,
    onAddPage: () -> Unit,
    onChangeTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            TextButton(
                onClick = onChangeTemplate,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Page Options", modifier = Modifier.size(16.dp))
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 2.dp))

            IconButton(
                onClick = onAddPage,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Page", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ZoomControlsBar(
    zoomScale: Float,
    isFitToScreen: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleFitToScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(32.dp),
                enabled = zoomScale > 0.52f
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(18.dp)
                )
            }

            TextButton(
                onClick = onResetZoom,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(32.dp),
                enabled = zoomScale < 4.95f
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(18.dp)
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(18.dp)
                    .padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Fit to Screen button
            FilledTonalIconButton(
                onClick = onToggleFitToScreen,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isFitToScreen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isFitToScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FitScreen,
                    contentDescription = "Fit to Screen",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CombinedBottomBar(
    currentPage: Int,
    totalPages: Int,
    zoomScale: Float,
    isFitToScreen: Boolean,
    onJumpToPage: () -> Unit,
    onAddPage: () -> Unit,
    onChangeTemplate: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleFitToScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Page X of Y (tappable to jump to page)
            TextButton(
                onClick = onJumpToPage,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Jump to Page",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Page Template options dropdown icon
            IconButton(
                onClick = onChangeTemplate,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Page Templates & Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Add Page button
            IconButton(
                onClick = onAddPage,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Page",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(18.dp)
                    .padding(horizontal = 3.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            // Zoom Out
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(28.dp),
                enabled = zoomScale > 0.52f
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(16.dp)
                )
            }

            // Zoom percentage button (tap resets to 100%)
            TextButton(
                onClick = onResetZoom,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Zoom In
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(28.dp),
                enabled = zoomScale < 4.95f
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(16.dp)
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(18.dp)
                    .padding(horizontal = 3.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            // Fit to Screen button
            FilledTonalIconButton(
                onClick = onToggleFitToScreen,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isFitToScreen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isFitToScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FitScreen,
                    contentDescription = "Fit to Screen",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


