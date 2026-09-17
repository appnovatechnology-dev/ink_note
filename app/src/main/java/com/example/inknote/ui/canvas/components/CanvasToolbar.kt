package com.example.inknote.ui.canvas.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.ToolType
import com.example.ui.theme.NoteInkColors
import com.example.inknote.viewmodel.EraserMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasTopAppBar(
    notebookTitle: String,
    canUndo: Boolean,
    canRedo: Boolean,
    isLocked: Boolean,
    isSpeakingNotes: Boolean = false,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
    onOpenAudioList: () -> Unit,
    onTranscribe: () -> Unit = {},
    onOpenStudyHub: () -> Unit = {},
    onOpenSummary: () -> Unit = {},
    onOpenSnapshots: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onManualSave: () -> Unit = {},
    onSpeakNotes: () -> Unit = {},
    onStopSpeakingNotes: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = notebookTitle,
                    maxLines = 1,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
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
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) MaterialTheme.colorScheme.onSurface else Color.LightGray)
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) MaterialTheme.colorScheme.onSurface else Color.LightGray)
            }
            if (isSpeakingNotes) {
                IconButton(onClick = onStopSpeakingNotes) {
                    Icon(Icons.Default.VolumeOff, contentDescription = "Stop Reading Aloud", tint = Color(0xFFDC2626))
                }
            } else {
                IconButton(onClick = onSpeakNotes) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Read Note Aloud", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onOpenStudyHub) {
                Icon(Icons.Default.School, contentDescription = "Flashcards & Quizzes", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenSummary) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary & Insights", tint = Color(0xFF6366F1))
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, contentDescription = "Share / Export", tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Note Actions")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
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
                    DropdownMenuItem(
                        text = { Text("Audio Recordings List") },
                        leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenAudioList()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("App Settings (Storage)") },
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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

            // Right: Recordings List Button
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
    highlighterWidth: Float,
    laserColor: Color = Color(0xFFEF4444),
    isLaserTailEnabled: Boolean = true,
    isMagicInkEnabled: Boolean,
    isGridSnapEnabled: Boolean,
    rulerActive: Boolean,
    protractorActive: Boolean,
    compassActive: Boolean,
    onSelectTool: (ToolType) -> Unit,
    onSetPenColor: (Color) -> Unit,
    onSetPenWidth: (Float) -> Unit,
    onSetHighlighterColor: (Color) -> Unit,
    onSetLaserColor: (Color) -> Unit = {},
    onToggleLaserTail: () -> Unit = {},
    onToggleMagicInk: () -> Unit,
    onToggleGridSnap: () -> Unit,
    onToggleRuler: () -> Unit,
    onToggleProtractor: () -> Unit,
    onToggleCompass: () -> Unit,
    onSelectShapeType: (ShapeType) -> Unit,
    onOpenShapeProperties: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPalette by remember { mutableStateOf(false) }
    var showShapesDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Color & Stroke / Laser Palette popup
        AnimatedVisibility(visible = showColorPalette) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                if (activeTool == ToolType.LASER) {
                    // Laser color swatches
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
                } else {
                    // Color swatches for Pen / Highlighter
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(NoteInkColors) { color ->
                            val isSelected = if (activeTool == ToolType.HIGHLIGHTER) highlighterColor == color else penColor == color
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
                                    .clickable {
                                        if (activeTool == ToolType.HIGHLIGHTER) onSetHighlighterColor(color) else onSetPenColor(color)
                                    }
                            )
                        }
                    }

                    // Stroke Width presets
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(2f, 3.5f, 6f, 10f).forEach { width ->
                            val isSelected = penWidth == width
                            Button(
                                onClick = { onSetPenWidth(width) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("${width.toInt()}pt", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Main Tool Icon Strip
        LazyRow(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pen Tool
            item {
                ToolButton(
                    icon = Icons.Default.Edit,
                    label = "Pen",
                    isSelected = activeTool == ToolType.PEN,
                    indicatorColor = penColor,
                    onClick = {
                        if (activeTool == ToolType.PEN) showColorPalette = !showColorPalette else {
                            onSelectTool(ToolType.PEN)
                            showColorPalette = true
                        }
                    }
                )
            }

            // Highlighter Tool
            item {
                ToolButton(
                    icon = Icons.Default.BorderColor,
                    label = "Highlighter",
                    isSelected = activeTool == ToolType.HIGHLIGHTER,
                    indicatorColor = highlighterColor,
                    onClick = {
                        if (activeTool == ToolType.HIGHLIGHTER) showColorPalette = !showColorPalette else {
                            onSelectTool(ToolType.HIGHLIGHTER)
                            showColorPalette = true
                        }
                    }
                )
            }

            // Eraser Tool
            item {
                ToolButton(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Eraser",
                    isSelected = activeTool == ToolType.ERASER,
                    onClick = {
                        onSelectTool(ToolType.ERASER)
                        showColorPalette = false
                    }
                )
            }

            // Lasso Selection Tool
            item {
                ToolButton(
                    icon = Icons.Default.Gesture,
                    label = "Lasso",
                    isSelected = activeTool == ToolType.LASSO,
                    activeColor = Color(0xFF6366F1),
                    onClick = {
                        onSelectTool(ToolType.LASSO)
                        showColorPalette = false
                    }
                )
            }

            // Laser Pointer Tool
            item {
                ToolButton(
                    icon = Icons.Default.Highlight,
                    label = "Laser",
                    isSelected = activeTool == ToolType.LASER,
                    indicatorColor = laserColor,
                    activeColor = laserColor,
                    onClick = {
                        if (activeTool == ToolType.LASER) {
                            showColorPalette = !showColorPalette
                        } else {
                            onSelectTool(ToolType.LASER)
                            showColorPalette = true
                        }
                    }
                )
            }

            // Shapes Tool & Dropdown
            item {
                Box {
                    ToolButton(
                        icon = Icons.Default.Category,
                        label = "Shapes",
                        isSelected = activeTool == ToolType.SHAPE,
                        onClick = {
                            onSelectTool(ToolType.SHAPE)
                            showShapesDropdown = true
                        }
                    )
                    DropdownMenu(
                        expanded = showShapesDropdown,
                        onDismissRequest = { showShapesDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rectangle") },
                            leadingIcon = { Icon(Icons.Default.CropLandscape, null) },
                            onClick = { onSelectShapeType(ShapeType.RECTANGLE); showShapesDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Square") },
                            leadingIcon = { Icon(Icons.Default.CropSquare, null) },
                            onClick = { onSelectShapeType(ShapeType.SQUARE); showShapesDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Circle / Ellipse") },
                            leadingIcon = { Icon(Icons.Default.Circle, null) },
                            onClick = { onSelectShapeType(ShapeType.CIRCLE); showShapesDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Straight Line") },
                            leadingIcon = { Icon(Icons.Default.HorizontalRule, null) },
                            onClick = { onSelectShapeType(ShapeType.LINE); showShapesDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Arrow") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowRightAlt, null) },
                            onClick = { onSelectShapeType(ShapeType.ARROW); showShapesDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Triangle") },
                            leadingIcon = { Icon(Icons.Default.ChangeHistory, null) },
                            onClick = { onSelectShapeType(ShapeType.TRIANGLE); showShapesDropdown = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Polygon / Star") },
                            leadingIcon = { Icon(Icons.Default.Star, null) },
                            onClick = { onSelectShapeType(ShapeType.STAR); showShapesDropdown = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Shape Settings...") },
                            leadingIcon = { Icon(Icons.Default.Palette, null) },
                            onClick = { onOpenShapeProperties(); showShapesDropdown = false }
                        )
                    }
                }
            }

            // Magic Ink Shape Recognition Toggle
            item {
                ToolButton(
                    icon = Icons.Default.AutoAwesome,
                    label = "Magic Ink",
                    isSelected = isMagicInkEnabled,
                    activeColor = Color(0xFF8B5CF6),
                    onClick = onToggleMagicInk
                )
            }

            // Ruler Tool
            item {
                ToolButton(
                    icon = Icons.Default.Straighten,
                    label = "Ruler",
                    isSelected = rulerActive,
                    onClick = onToggleRuler
                )
            }

            // Protractor Tool
            item {
                ToolButton(
                    icon = Icons.Default.Explore,
                    label = "Protractor",
                    isSelected = protractorActive,
                    onClick = onToggleProtractor
                )
            }

            // Compass Tool
            item {
                ToolButton(
                    icon = Icons.Default.Adjust,
                    label = "Compass",
                    isSelected = compassActive,
                    onClick = onToggleCompass
                )
            }

            // Grid Snapping
            item {
                ToolButton(
                    icon = Icons.Default.GridOn,
                    label = "Grid Snap",
                    isSelected = isGridSnapEnabled,
                    onClick = onToggleGridSnap
                )
            }

            // Text Tool
            item {
                ToolButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeTool == ToolType.TEXT,
                    onClick = {
                        onSelectTool(ToolType.TEXT)
                        showColorPalette = false
                    }
                )
            }

            // Media / Image Tool
            item {
                ToolButton(
                    icon = Icons.Default.Image,
                    label = "Image",
                    isSelected = false,
                    onClick = onPickImage
                )
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
                enabled = zoomScale > 0.55f
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
                enabled = zoomScale < 2.95f
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

