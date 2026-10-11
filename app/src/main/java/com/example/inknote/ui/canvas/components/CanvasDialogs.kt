package com.example.inknote.ui.canvas.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.db.entities.AudioRecordingEntity
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.util.PageRangeParser
import com.example.ui.theme.NoteInkColors
import java.util.Locale

@Composable
fun PageTemplateDialog(
    currentTemplate: String,
    totalPages: Int,
    onSelectTemplate: (String) -> Unit,
    onDeletePage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page Settings & Templates", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Paper Pattern:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val templates = listOf(
                    Triple("BLANK", "Blank Paper", Icons.Default.CropLandscape),
                    Triple("LINED_WIDE", "Lined (Wide Ruled)", Icons.AutoMirrored.Filled.FormatAlignLeft),
                    Triple("LINED_NARROW", "Lined (Narrow Ruled)", Icons.AutoMirrored.Filled.Notes),
                    Triple("GRID_SMALL", "Grid / Graph Math", Icons.Default.GridOn),
                    Triple("GRID_LARGE", "Large Grid", Icons.Default.Grid4x4),
                    Triple("DOTTED", "Dotted Bullet Journal", Icons.Default.MoreHoriz),
                    Triple("CORNELL", "Cornell Notes Template", Icons.Default.DashboardCustomize),
                    Triple("ISOMETRIC", "Isometric Grid (3D)", Icons.Default.Details),
                    Triple("MUSIC", "Music Staves", Icons.Default.MusicNote),
                    Triple("HABIT_TRACKER", "Habit Tracker (Radial)", Icons.Default.TrackChanges),
                    Triple("DAILY_GOALS", "Daily Goals & Priorities", Icons.Default.CheckCircle),
                    Triple("WEEKLY_PLANNER", "Weekly Planner", Icons.Default.DateRange),
                    Triple("DAILY_PLANNER", "Daily Planner & Schedule", Icons.Default.Schedule),
                    Triple("GOAL_SETTING", "Goal Setting & Milestones", Icons.Default.Flag),
                    Triple("ASSIGNMENT_PLANNER", "Assignment Planner", Icons.Default.Assignment),
                    Triple("MIND_MAP", "Mind Map (Branching)", Icons.Default.AccountTree),
                    Triple("SEMESTER_OVERVIEW", "Semester Overview", Icons.Default.CalendarMonth),
                    Triple("MONTHLY_PLANNER", "Monthly Planner Calendar", Icons.Default.CalendarToday)
                )

                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(templates) { (type, label, icon) ->
                        val isSelected = currentTemplate == type
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { onSelectTemplate(type) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                if (totalPages > 1) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            onDeletePage()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Current Page")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ShapePropertiesDialog(
    strokeColor: Color,
    fillColor: Color,
    strokeWidth: Float,
    isFilled: Boolean,
    sideCount: Int,
    onApply: (Color, Color, Float, Boolean, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sColor by remember { mutableStateOf(strokeColor) }
    var fColor by remember { mutableStateOf(fillColor) }
    var width by remember { mutableFloatStateOf(strokeWidth) }
    var filled by remember { mutableStateOf(isFilled) }
    var sides by remember { mutableIntStateOf(sideCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shape Style & Geometry", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stroke Color:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NoteInkColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (sColor == color) 2.5.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { sColor = color }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fill Shape:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(checked = filled, onCheckedChange = { filled = it })
                }

                if (filled) {
                    Text("Fill Color:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(NoteInkColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (fColor == color) 2.5.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { fColor = color.copy(alpha = 0.35f) }
                            )
                        }
                    }
                }

                Text("Stroke Width: ${width.toInt()}pt", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(value = width, onValueChange = { width = it }, valueRange = 1f..16f)

                Text("Polygon / Star Sides: $sides", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(value = sides.toFloat(), onValueChange = { sides = it.toInt() }, valueRange = 3f..10f, steps = 6)
            }
        },
        confirmButton = {
            Button(onClick = { onApply(sColor, if (filled) fColor else Color.Transparent, width, filled, sides) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

enum class PageSelectionMode {
    ALL_PAGES,
    TYPE_RANGE,
    TAP_TO_SELECT
}

@Composable
fun ExportDialog(
    isExporting: Boolean,
    pages: List<PageEntity> = emptyList(),
    currentPageIndex: Int = 0,
    onExportPdf: (selectedPageIds: List<Long>) -> Unit,
    onSaveToDownloads: (selectedPageIds: List<Long>) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectionMode by remember { mutableStateOf(PageSelectionMode.ALL_PAGES) }
    var typedRangeText by remember(pages.size) {
        mutableStateOf(if (pages.isNotEmpty()) "1-${pages.size}" else "1")
    }

    // Selection state for tap-to-select individual checkboxes
    val selectedPageMap = remember(pages) {
        mutableStateMapOf<Long, Boolean>().apply {
            pages.forEach { put(it.id, true) }
        }
    }

    // Live parsing for typed range input
    val rangeParseResult = remember(typedRangeText, pages.size) {
        PageRangeParser.parse(typedRangeText, pages.size)
    }

    // Calculate final selected page IDs and whether export is valid based on mode
    val finalSelectedPageIds: List<Long> = remember(
        selectionMode,
        rangeParseResult,
        selectedPageMap.toMap(),
        pages
    ) {
        when (selectionMode) {
            PageSelectionMode.ALL_PAGES -> pages.map { it.id }
            PageSelectionMode.TYPE_RANGE -> {
                if (rangeParseResult.isValid) {
                    rangeParseResult.selectedPageIndices.mapNotNull { pages.getOrNull(it)?.id }
                } else {
                    emptyList()
                }
            }
            PageSelectionMode.TAP_TO_SELECT -> {
                pages.filter { selectedPageMap[it.id] == true }.map { it.id }
            }
        }
    }

    val finalCount = finalSelectedPageIds.size
    val canExport = when (selectionMode) {
        PageSelectionMode.ALL_PAGES -> pages.isNotEmpty()
        PageSelectionMode.TYPE_RANGE -> rangeParseResult.isValid && finalCount > 0
        PageSelectionMode.TAP_TO_SELECT -> finalCount > 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Export & Save Note", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Export all handwriting, drawings, geometric shapes, and annotations into a vector PDF. Works offline without internet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (pages.size > 1) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Section header with live count
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Page Selection", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                if (canExport) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "$finalCount / ${pages.size} selected",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "0 / ${pages.size} selected",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // 3 Equal alternative modes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectionMode == PageSelectionMode.ALL_PAGES,
                                    onClick = { selectionMode = PageSelectionMode.ALL_PAGES },
                                    label = { Text("All Pages", fontSize = 11.sp) },
                                    leadingIcon = if (selectionMode == PageSelectionMode.ALL_PAGES) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )

                                FilterChip(
                                    selected = selectionMode == PageSelectionMode.TYPE_RANGE,
                                    onClick = {
                                        selectionMode = PageSelectionMode.TYPE_RANGE
                                        if (typedRangeText.isBlank()) {
                                            typedRangeText = "1-${pages.size}"
                                        }
                                    },
                                    label = { Text("Type Range", fontSize = 11.sp) },
                                    leadingIcon = if (selectionMode == PageSelectionMode.TYPE_RANGE) {
                                        { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )

                                FilterChip(
                                    selected = selectionMode == PageSelectionMode.TAP_TO_SELECT,
                                    onClick = { selectionMode = PageSelectionMode.TAP_TO_SELECT },
                                    label = { Text("Tap to Select", fontSize = 11.sp) },
                                    leadingIcon = if (selectionMode == PageSelectionMode.TAP_TO_SELECT) {
                                        { Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // Sub-panel based on active mode
                            when (selectionMode) {
                                PageSelectionMode.ALL_PAGES -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "All ${pages.size} pages will be exported in ascending order (Pages 1 to ${pages.size}).",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                PageSelectionMode.TYPE_RANGE -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = typedRangeText,
                                            onValueChange = { typedRangeText = it },
                                            label = { Text("Page Range (e.g. 3-7, 1, 4, 9)", fontSize = 12.sp) },
                                            placeholder = { Text("e.g. 1, 3-5, 8, 11-14", fontSize = 12.sp) },
                                            singleLine = true,
                                            isError = !rangeParseResult.isValid && typedRangeText.isNotBlank(),
                                            supportingText = {
                                                if (typedRangeText.isNotBlank() && !rangeParseResult.isValid) {
                                                    Text(
                                                        text = rangeParseResult.errorMessage ?: "Invalid page range",
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                } else if (rangeParseResult.isValid && typedRangeText.isNotBlank()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = Color(0xFF16A34A),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = rangeParseResult.confirmationText,
                                                            color = Color(0xFF16A34A),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                } else {
                                                    Text("Note has ${pages.size} total pages. Supports ranges and commas.", fontSize = 11.sp)
                                                }
                                            },
                                            trailingIcon = {
                                                if (typedRangeText.isNotEmpty()) {
                                                    IconButton(onClick = { typedRangeText = "" }) {
                                                        Icon(Icons.Default.Clear, contentDescription = "Clear range input", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Preset shortcut buttons for typed range
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AssistChip(
                                                onClick = { typedRangeText = "1-${pages.size}" },
                                                label = { Text("All (1-${pages.size})", fontSize = 10.sp) }
                                            )
                                            AssistChip(
                                                onClick = { typedRangeText = "${currentPageIndex + 1}" },
                                                label = { Text("Page ${currentPageIndex + 1}", fontSize = 10.sp) }
                                            )
                                            if (pages.size > 2) {
                                                val half = (pages.size + 1) / 2
                                                AssistChip(
                                                    onClick = { typedRangeText = "1-$half" },
                                                    label = { Text("1-$half", fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }

                                PageSelectionMode.TAP_TO_SELECT -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Quick preset buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val currentId = pages.getOrNull(currentPageIndex)?.id
                                            val isCurrentOnly = pages.count { selectedPageMap[it.id] == true } == 1 &&
                                                    currentId != null && selectedPageMap[currentId] == true

                                            FilterChip(
                                                selected = isCurrentOnly,
                                                onClick = {
                                                    pages.forEach { selectedPageMap[it.id] = (it.id == currentId) }
                                                },
                                                label = { Text("Page ${currentPageIndex + 1} Only", fontSize = 11.sp) }
                                            )

                                            val allSelected = pages.all { selectedPageMap[it.id] == true }
                                            TextButton(
                                                onClick = {
                                                    val target = !allSelected
                                                    pages.forEach { selectedPageMap[it.id] = target }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(if (allSelected) "Deselect All" else "Select All", fontSize = 11.sp)
                                            }
                                        }

                                        // Scrollable checkbox list
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 160.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            pages.forEachIndexed { index, page ->
                                                val isChecked = selectedPageMap[page.id] ?: false
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedPageMap[page.id] = !isChecked }
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = { checked ->
                                                            selectedPageMap[page.id] = checked
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Page ${index + 1}" + if (index == currentPageIndex) " (Current)" else "",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (index == currentPageIndex) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isExporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Rendering Vector PDF...", fontSize = 14.sp)
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canExport) {
                                onSaveToDownloads(finalSelectedPageIds)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (canExport) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    "Save PDF to Downloads",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (canExport) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    if (canExport) {
                                        "Save $finalCount page${if (finalCount > 1) "s" else ""} to device Downloads"
                                    } else if (selectionMode == PageSelectionMode.TYPE_RANGE && !rangeParseResult.isValid) {
                                        rangeParseResult.errorMessage ?: "Please enter a valid page range"
                                    } else {
                                        "Select at least 1 page"
                                    },
                                    fontSize = 11.sp,
                                    color = if (canExport) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canExport) {
                                onExportPdf(finalSelectedPageIds)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (canExport) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    "Share PDF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (canExport) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    if (canExport) {
                                        "Send $finalCount page${if (finalCount > 1) "s" else ""} via messaging or email"
                                    } else if (selectionMode == PageSelectionMode.TYPE_RANGE && !rangeParseResult.isValid) {
                                        rangeParseResult.errorMessage ?: "Please enter a valid page range"
                                    } else {
                                        "Select at least 1 page"
                                    },
                                    fontSize = 11.sp,
                                    color = if (canExport) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AudioRecordingsListDialog(
    recordings: List<AudioRecordingEntity>,
    activeRecordingId: Long?,
    isPlaying: Boolean,
    onPlayRecording: (AudioRecordingEntity) -> Unit,
    onDeleteRecording: (AudioRecordingEntity) -> Unit,
    onRenameRecording: (AudioRecordingEntity, String) -> Unit,
    onTranscribeRecording: (AudioRecordingEntity) -> Unit,
    onDownloadRecording: ((AudioRecordingEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var recordingToRename by remember { mutableStateOf<AudioRecordingEntity?>(null) }
    var recordingToDelete by remember { mutableStateOf<AudioRecordingEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Voice & Lecture Recordings", fontWeight = FontWeight.Bold) },
        text = {
            if (recordings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.MicNone, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("No voice recordings yet.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Tap the red mic in the top bar to record audio while writing notes. All strokes will sync with the audio timestamp!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recordings, key = { it.id }) { rec ->
                        val isCurrent = rec.id == activeRecordingId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    IconButton(
                                        onClick = { onPlayRecording(rec) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isCurrent && isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                                            tint = if (isCurrent && isPlaying) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = rec.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        val totalSeconds = rec.durationMs / 1000
                                        Text(
                                            text = String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (onDownloadRecording != null) {
                                        IconButton(
                                            onClick = { onDownloadRecording(rec) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Save to Downloads", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onTranscribeRecording(rec) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Transcribe, contentDescription = "Transcribe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { recordingToRename = rec },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { recordingToDelete = rec },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (recordingToRename != null) {
        val rec = recordingToRename!!
        var newTitle by remember { mutableStateOf(rec.title) }
        AlertDialog(
            onDismissRequest = { recordingToRename = null },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTitle.isNotBlank()) {
                        onRenameRecording(rec, newTitle.trim())
                    }
                    recordingToRename = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (recordingToDelete != null) {
        val rec = recordingToDelete!!
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Voice Recording?") },
            text = { Text("Are you sure you want to delete \"${rec.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecording(rec)
                        recordingToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pageInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val parsed = pageInput.trim().toIntOrNull()
        if (parsed == null) {
            errorMessage = "Please enter a valid page number"
        } else if (parsed <= 0) {
            errorMessage = "Page number must be at least 1"
        } else if (parsed > totalPages) {
            errorMessage = "This notebook only has $totalPages pages"
        } else {
            onJump(parsed - 1)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FindInPage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Jump to Page", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Current: Page ${currentPage + 1} of $totalPages",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = {
                        pageInput = it
                        errorMessage = null
                    },
                    label = { Text("Page Number (1 to $totalPages)") },
                    placeholder = { Text("e.g. 550") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { submit() }
                    ),
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { submit() }) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameNoteDialog(
    currentTitle: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember { mutableStateOf(currentTitle) }
    var isError by remember { mutableStateOf(false) }

    fun submit() {
        val trimmed = titleInput.trim()
        if (trimmed.isEmpty()) {
            isError = true
        } else {
            onRename(trimmed)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Rename Note", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = {
                        titleInput = it
                        isError = false
                    },
                    label = { Text("Note Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submit() }
                    ),
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("Title cannot be empty", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { submit() }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

