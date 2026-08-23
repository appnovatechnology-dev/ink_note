package com.example.inknote.ui.canvas.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.db.entities.AudioRecordingEntity
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
                    Triple("MUSIC", "Music Staves", Icons.Default.MusicNote)
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

@Composable
fun ExportDialog(
    isExporting: Boolean,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export & Share Note", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Flatten all handwriting, drawings, geometric shapes, and annotations into a high-quality PDF to share or print fully offline.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isExporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Rendering Vector PDF...", fontSize = 14.sp)
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExportPdf() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Column {
                                Text("Export as PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("High-resolution vector PDF export", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
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
