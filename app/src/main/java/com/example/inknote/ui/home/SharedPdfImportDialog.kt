package com.example.inknote.ui.home

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.db.entities.NotebookEntity
import com.example.inknote.data.db.entities.SubjectEntity
import com.example.inknote.pdf.PdfManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPdfImportDialog(
    uris: List<Uri>,
    notebooks: List<NotebookEntity>,
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onCreateNewNote: (title: String, subjectId: Long?) -> Unit,
    onAddToExistingNote: (notebookId: Long) -> Unit
) {
    val context = LocalContext.current
    val fileNames = remember(uris) {
        uris.map { PdfManager.getFileNameFromUri(context, it) }
    }
    val defaultTitle = remember(fileNames) {
        if (fileNames.isNotEmpty()) {
            fileNames.first().removeSuffix(".pdf").removeSuffix(".PDF")
        } else "Shared Document"
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Create New, 1 = Add to Existing
    var newNoteTitle by remember { mutableStateOf(defaultTitle) }
    var selectedSubjectId by remember { mutableStateOf<Long?>(null) }
    var selectedNotebookId by remember { mutableStateOf<Long?>(notebooks.firstOrNull()?.id) }
    var notebookSearchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = if (uris.size > 1) "Import Shared PDFs" else "Import Shared PDF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (uris.size > 1) "${uris.size} PDF files received" else fileNames.firstOrNull() ?: "PDF Document",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Segmented Tab Toggle
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create New", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Existing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (selectedTab == 0) {
                    // Option A: Create New Note
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newNoteTitle,
                            onValueChange = { newNoteTitle = it },
                            label = { Text("Note Title") },
                            placeholder = { Text("Enter title for new note...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Destination Folder:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSubjectId == null,
                                    onClick = { selectedSubjectId = null },
                                    label = { Text("All Notes", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                            items(subjects) { sub ->
                                val subColor = try {
                                    Color(android.graphics.Color.parseColor(sub.colorHex))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                FilterChip(
                                    selected = selectedSubjectId == sub.id,
                                    onClick = { selectedSubjectId = sub.id },
                                    label = { Text(sub.name, fontSize = 11.sp) },
                                    leadingIcon = {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(subColor))
                                    }
                                )
                            }
                        }

                        Text(
                            text = "• Appends PDF pages as annotated background canvas pages.\n• Works 100% offline without network.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    // Option B: Add to Existing Note
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (notebooks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No existing notebooks found.\nPlease select 'Create New'.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            if (notebooks.size > 4) {
                                OutlinedTextField(
                                    value = notebookSearchQuery,
                                    onValueChange = { notebookSearchQuery = it },
                                    placeholder = { Text("Search notebooks...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }

                            Text(
                                text = "Select note to append pages to:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val filteredNotebooks = remember(notebooks, notebookSearchQuery) {
                                if (notebookSearchQuery.isBlank()) notebooks
                                else notebooks.filter { it.title.contains(notebookSearchQuery, ignoreCase = true) }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredNotebooks, key = { it.id }) { notebook ->
                                    val isSelected = selectedNotebookId == notebook.id
                                    val subjectName = subjects.find { it.id == notebook.subjectId }?.name ?: "All Notes"
                                    val coverColor = try {
                                        Color(android.graphics.Color.parseColor(notebook.coverColorHex))
                                    } catch (_: Exception) {
                                        MaterialTheme.colorScheme.primary
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedNotebookId = notebook.id },
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(coverColor)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = notebook.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$subjectName • ${notebook.pageCount} page${if (notebook.pageCount != 1) "s" else ""}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onCreateNewNote(newNoteTitle.ifBlank { "Shared Document" }, selectedSubjectId)
                    } else {
                        val targetId = selectedNotebookId ?: notebooks.firstOrNull()?.id
                        if (targetId != null) {
                            onAddToExistingNote(targetId)
                        }
                    }
                },
                enabled = if (selectedTab == 0) newNoteTitle.isNotBlank() else (selectedNotebookId != null || notebooks.isNotEmpty())
            ) {
                Text(if (selectedTab == 0) "Create & Open" else "Append & Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
