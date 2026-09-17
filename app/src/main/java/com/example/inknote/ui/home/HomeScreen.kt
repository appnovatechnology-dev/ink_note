package com.example.inknote.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inknote.data.db.entities.NotebookEntity
import com.example.inknote.data.db.entities.SubjectEntity
import com.example.inknote.ui.canvas.components.SecurityPinDialog
import com.example.ui.theme.NoteInkColors
import com.example.inknote.viewmodel.HomeUiState
import com.example.inknote.viewmodel.HomeViewModel
import com.example.inknote.viewmodel.NoteFilter
import com.example.inknote.viewmodel.ViewMode
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNotebook: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importPdf(uri) { newNotebookId ->
                onOpenNotebook(newNotebookId)
            }
        }
    }

    var showRenameDialogFor by remember { mutableStateOf<NotebookEntity?>(null) }
    var showMoveDialogFor by remember { mutableStateOf<NotebookEntity?>(null) }
    var notebookToUnlock by remember { mutableStateOf<NotebookEntity?>(null) }

    fun handleNotebookClick(notebook: NotebookEntity) {
        if (notebook.isLocked && !notebook.pinCode.isNullOrBlank()) {
            notebookToUnlock = notebook
        } else {
            onOpenNotebook(notebook.id)
        }
    }

    // Drag-and-Drop state tracking
    var draggedNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var activeDropTargetId by remember { mutableStateOf<Long?>(null) } // -999L = Unassigned, or subjectId
    val dropTargetBounds = remember { mutableStateMapOf<Long, Rect>() }

    fun handleDrop(targetSubjectId: Long?) {
        val nb = draggedNotebook ?: return
        viewModel.moveNotebookToSubject(nb, targetSubjectId)
        val targetName = if (targetSubjectId == null) {
            "All Notes (Unassigned)"
        } else {
            uiState.subjects.find { it.id == targetSubjectId }?.name ?: "Folder"
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Moved \"${nb.title}\" to $targetName")
        }
        draggedNotebook = null
        activeDropTargetId = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Sidebar (Subjects & Dividers + Drop targets)
            AnimatedVisibility(visible = uiState.isSidebarOpen || draggedNotebook != null) {
                HomeSidebar(
                    subjects = uiState.subjects,
                    selectedFilter = uiState.selectedFilter,
                    selectedSubjectId = uiState.selectedSubjectId,
                    draggedNotebook = draggedNotebook,
                    activeDropTargetId = activeDropTargetId,
                    onRegisterDropTarget = { id, rect ->
                        dropTargetBounds[id] = rect
                    },
                    onSelectFilter = { filter, subId -> viewModel.setFilter(filter, subId) },
                    onAddSubject = { viewModel.showNewSubjectDialog(true) },
                    onDeleteSubject = { viewModel.deleteSubject(it) },
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            }

            // 2. Main Notebook Content Area
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { viewModel.toggleSidebar() }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar")
                                }
                                Text(
                                    text = when (uiState.selectedFilter) {
                                        NoteFilter.ALL -> "All Notes"
                                        NoteFilter.FAVORITES -> "Favorites ⭐"
                                        NoteFilter.RECENT -> "Recent Notes"
                                        NoteFilter.SUBJECT -> uiState.subjects.find { it.id == uiState.selectedSubjectId }?.name ?: "Subject Notes"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (uiState.viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle View"
                                )
                            }
                            IconButton(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Import PDF", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                },
                floatingActionButton = {
                    if (draggedNotebook == null) {
                        FloatingActionButton(
                            onClick = { viewModel.showNewNotebookDialog(true) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "New Note")
                                Text("New Note", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    // Quick Drop Ribbon when dragging
                    AnimatedVisibility(visible = draggedNotebook != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        "Dragging \"${draggedNotebook?.title ?: ""}\" — Drop on a folder below or in the sidebar:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        val isTarget = activeDropTargetId == -999L
                                        Surface(
                                            modifier = Modifier
                                                .onGloballyPositioned { dropTargetBounds[-999L] = it.boundsInRoot() }
                                                .clickable { handleDrop(null) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(if (isTarget) 2.dp else 1.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.FolderOff, contentDescription = null, tint = if (isTarget) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                                Text("🚫 Unassigned", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isTarget) Color.White else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                    items(uiState.subjects, key = { it.id }) { sub ->
                                        val isTarget = activeDropTargetId == sub.id
                                        val subColor = try { Color(android.graphics.Color.parseColor(sub.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                                        Surface(
                                            modifier = Modifier
                                                .onGloballyPositioned { dropTargetBounds[sub.id] = it.boundsInRoot() }
                                                .clickable { handleDrop(sub.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isTarget) subColor else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(if (isTarget) 2.dp else 1.dp, subColor)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isTarget) Color.White else subColor))
                                                Text(sub.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isTarget) Color.White else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search notes and typed text...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.notebooks.isEmpty()) {
                        // Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = if (uiState.searchQuery.isNotBlank()) "No notes matching \"${uiState.searchQuery}\"" else "No notes here yet",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { viewModel.showNewNotebookDialog(true) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create First Note")
                                }
                            }
                        }
                    } else {
                        // Notebook List or Grid
                        if (uiState.viewMode == ViewMode.GRID) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(170.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)
                            ) {
                                items(uiState.notebooks, key = { it.id }) { notebook ->
                                    val isBeingDragged = draggedNotebook?.id == notebook.id
                                    NotebookGridCard(
                                        notebook = notebook,
                                        subjects = uiState.subjects,
                                        isBeingDragged = isBeingDragged,
                                        onClick = { handleNotebookClick(notebook) },
                                        onToggleFavorite = { viewModel.toggleFavorite(notebook.id) },
                                        onRename = { showRenameDialogFor = notebook },
                                        onMove = { showMoveDialogFor = notebook },
                                        onQuickMove = { targetSubId ->
                                            viewModel.moveNotebookToSubject(notebook, targetSubId)
                                            val name = if (targetSubId == null) "All Notes" else uiState.subjects.find { it.id == targetSubId }?.name ?: "Folder"
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Moved \"${notebook.title}\" to $name")
                                            }
                                        },
                                        onDelete = { viewModel.deleteNotebook(notebook) },
                                        onDragStart = { offset ->
                                            draggedNotebook = notebook
                                            dragPosition = offset
                                        },
                                        onDrag = { delta ->
                                            dragPosition += delta
                                            activeDropTargetId = dropTargetBounds.entries.firstOrNull { (_, rect) ->
                                                rect.contains(dragPosition)
                                            }?.key
                                        },
                                        onDragEnd = {
                                            if (activeDropTargetId != null) {
                                                val targetId = if (activeDropTargetId == -999L) null else activeDropTargetId
                                                handleDrop(targetId)
                                            } else {
                                                draggedNotebook = null
                                                activeDropTargetId = null
                                            }
                                        },
                                        onDragCancel = {
                                            draggedNotebook = null
                                            activeDropTargetId = null
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)
                            ) {
                                items(uiState.notebooks, key = { it.id }) { notebook ->
                                    val isBeingDragged = draggedNotebook?.id == notebook.id
                                    NotebookListCard(
                                        notebook = notebook,
                                        subjects = uiState.subjects,
                                        isBeingDragged = isBeingDragged,
                                        onClick = { handleNotebookClick(notebook) },
                                        onToggleFavorite = { viewModel.toggleFavorite(notebook.id) },
                                        onRename = { showRenameDialogFor = notebook },
                                        onMove = { showMoveDialogFor = notebook },
                                        onQuickMove = { targetSubId ->
                                            viewModel.moveNotebookToSubject(notebook, targetSubId)
                                            val name = if (targetSubId == null) "All Notes" else uiState.subjects.find { it.id == targetSubId }?.name ?: "Folder"
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Moved \"${notebook.title}\" to $name")
                                            }
                                        },
                                        onDelete = { viewModel.deleteNotebook(notebook) },
                                        onDragStart = { offset ->
                                            draggedNotebook = notebook
                                            dragPosition = offset
                                        },
                                        onDrag = { delta ->
                                            dragPosition += delta
                                            activeDropTargetId = dropTargetBounds.entries.firstOrNull { (_, rect) ->
                                                rect.contains(dragPosition)
                                            }?.key
                                        },
                                        onDragEnd = {
                                            if (activeDropTargetId != null) {
                                                val targetId = if (activeDropTargetId == -999L) null else activeDropTargetId
                                                handleDrop(targetId)
                                            } else {
                                                draggedNotebook = null
                                                activeDropTargetId = null
                                            }
                                        },
                                        onDragCancel = {
                                            draggedNotebook = null
                                            activeDropTargetId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Dragged Ghost Card representation
        if (draggedNotebook != null) {
            val dragged = draggedNotebook!!
            val coverColor = try {
                Color(android.graphics.Color.parseColor(dragged.coverColorHex))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(dragPosition.x.roundToInt() - 80, dragPosition.y.roundToInt() - 40) }
                    .zIndex(100f)
                    .shadow(16.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .rotate(3f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(coverColor, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = dragged.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Moving Note...",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showNewNotebookDialog) {
        NewNotebookDialog(
            subjects = uiState.subjects,
            selectedSubjectId = uiState.selectedSubjectId,
            onDismiss = { viewModel.showNewNotebookDialog(false) },
            onCreate = { title, subId, color, template ->
                viewModel.createNotebook(title, subId, color, template) { id ->
                    viewModel.showNewNotebookDialog(false)
                    onOpenNotebook(id)
                }
            }
        )
    }

    if (uiState.showNewSubjectDialog) {
        NewSubjectDialog(
            onDismiss = { viewModel.showNewSubjectDialog(false) },
            onCreate = { name, color ->
                viewModel.createSubject(name, color)
                viewModel.showNewSubjectDialog(false)
            }
        )
    }

    if (showRenameDialogFor != null) {
        val nb = showRenameDialogFor!!
        var newTitle by remember { mutableStateOf(nb.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            title = { Text("Rename Note") },
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
                    viewModel.renameNotebook(nb, newTitle)
                    showRenameDialogFor = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) { Text("Cancel") }
            }
        )
    }

    if (showMoveDialogFor != null) {
        val nb = showMoveDialogFor!!
        MoveToFolderDialog(
            notebook = nb,
            subjects = uiState.subjects,
            onMove = { targetSubjectId ->
                viewModel.moveNotebookToSubject(nb, targetSubjectId)
                showMoveDialogFor = null
            },
            onCreateSubjectAndMove = { name, colorHex ->
                viewModel.createSubject(name, colorHex)
                showMoveDialogFor = null
            },
            onDismiss = { showMoveDialogFor = null }
        )
    }

    if (notebookToUnlock != null) {
        val lockedNb = notebookToUnlock!!
        SecurityPinDialog(
            currentPin = lockedNb.pinCode,
            isUnlocking = true,
            onVerifyOrSetPin = { enteredPin ->
                if (enteredPin == lockedNb.pinCode) {
                    val id = lockedNb.id
                    notebookToUnlock = null
                    onOpenNotebook(id)
                    true
                } else {
                    false
                }
            },
            onDismiss = { notebookToUnlock = null }
        )
    }
}

@Composable
fun HomeSidebar(
    subjects: List<SubjectEntity>,
    selectedFilter: NoteFilter,
    selectedSubjectId: Long?,
    draggedNotebook: NotebookEntity? = null,
    activeDropTargetId: Long? = null,
    onRegisterDropTarget: (Long, Rect) -> Unit = { _, _ -> },
    onSelectFilter: (NoteFilter, Long?) -> Unit,
    onAddSubject: () -> Unit,
    onDeleteSubject: (SubjectEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // App Title Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text("InkNote", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Standard Navigation Tabs
            val isAllNotesTarget = activeDropTargetId == -999L
            SidebarItem(
                label = "All Notes",
                icon = if (isAllNotesTarget) Icons.Default.Download else Icons.Default.Folder,
                isSelected = selectedFilter == NoteFilter.ALL || isAllNotesTarget,
                isDropActive = isAllNotesTarget,
                modifier = Modifier.onGloballyPositioned {
                    onRegisterDropTarget(-999L, it.boundsInRoot())
                },
                onClick = { onSelectFilter(NoteFilter.ALL, null) }
            )
            SidebarItem(
                label = "Favorites",
                icon = Icons.Default.Star,
                isSelected = selectedFilter == NoteFilter.FAVORITES,
                iconColor = Color(0xFFF59E0B),
                onClick = { onSelectFilter(NoteFilter.FAVORITES, null) }
            )
            SidebarItem(
                label = "Recent",
                icon = Icons.Default.History,
                isSelected = selectedFilter == NoteFilter.RECENT,
                onClick = { onSelectFilter(NoteFilter.RECENT, null) }
            )

            Spacer(Modifier.height(12.dp))

            // Subjects Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SUBJECTS / DIVIDERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onAddSubject, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subject", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Subject List
            var subjectToDelete by remember { mutableStateOf<SubjectEntity?>(null) }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(subjects) { subject ->
                    val isSelected = selectedFilter == NoteFilter.SUBJECT && selectedSubjectId == subject.id
                    val isDropTarget = activeDropTargetId == subject.id
                    val subColor = try {
                        Color(android.graphics.Color.parseColor(subject.colorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .onGloballyPositioned {
                                onRegisterDropTarget(subject.id, it.boundsInRoot())
                            }
                            .clickable { onSelectFilter(NoteFilter.SUBJECT, subject.id) }
                            .then(
                                if (isDropTarget) {
                                    Modifier.border(2.dp, subColor, RoundedCornerShape(10.dp))
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isDropTarget -> subColor.copy(alpha = 0.25f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(subColor, CircleShape)
                                )
                                Text(
                                    text = subject.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isDropTarget) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isDropTarget) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Drop Here",
                                    tint = subColor,
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                )
                            } else {
                                // Delete subject action
                                IconButton(
                                    onClick = { subjectToDelete = subject },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Subject",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (subjectToDelete != null) {
                val sub = subjectToDelete!!
                AlertDialog(
                    onDismissRequest = { subjectToDelete = null },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Delete Subject / Divider?") },
                    text = { Text("Are you sure you want to delete \"${sub.name}\"? Any notes assigned to this subject will be unassigned but not deleted.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                onDeleteSubject(sub)
                                subjectToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { subjectToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // Offline Status Badge at bottom
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
            Text("100% Local & Offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isDropActive: Boolean = false,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isDropActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier
            ),
        shape = RoundedCornerShape(10.dp),
        color = when {
            isDropActive -> MaterialTheme.colorScheme.primaryContainer
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected || isDropActive) MaterialTheme.colorScheme.primary else iconColor, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isDropActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookGridCard(
    notebook: NotebookEntity,
    subjects: List<SubjectEntity> = emptyList(),
    isBeingDragged: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onQuickMove: (Long?) -> Unit = {},
    onDelete: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var cardCenterOffset by remember { mutableStateOf(Offset.Zero) }

    val coverColor = try {
        Color(android.graphics.Color.parseColor(notebook.coverColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardCenterOffset = it.boundsInRoot().center }
            .graphicsLayer {
                alpha = if (isBeingDragged) 0.35f else 1.0f
                scaleX = if (isBeingDragged) 0.95f else 1.0f
                scaleY = if (isBeingDragged) 0.95f else 1.0f
            }
            .pointerInput(notebook.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        onDragStart(cardCenterOffset + localOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBeingDragged) 0.dp else 2.dp)
    ) {
        Column {
            // Notebook Cover Top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(coverColor)
                    .padding(8.dp)
            ) {
                // Favorite Star
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                ) {
                    Icon(
                        imageVector = if (notebook.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (notebook.isFavorite) Color(0xFFF59E0B) else Color.White
                    )
                }

                // Drag indicator hint top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0x55000000), RoundedCornerShape(6.dp))
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.DragIndicator,
                        contentDescription = "Hold to Drag",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Page count badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color(0x66000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${notebook.pageCount} pages", color = Color.White, fontSize = 11.sp)
                }

                // Lock badge if locked
                if (notebook.isLocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0x88000000), CircleShape)
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Protected Note", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // Audio recording badge if exists
                if (notebook.audioDurationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0xCCDC2626), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text("Audio", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Notebook Title & Date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notebook.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(notebook.updatedAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    NotebookContextMenu(
                        expanded = showMenu,
                        notebook = notebook,
                        subjects = subjects,
                        onDismiss = { showMenu = false },
                        onMove = { showMenu = false; onMove() },
                        onQuickMove = { subId ->
                            showMenu = false
                            onQuickMove(subId)
                        },
                        onRename = { showMenu = false; onRename() },
                        onToggleFavorite = { showMenu = false; onToggleFavorite() },
                        onDelete = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookListCard(
    notebook: NotebookEntity,
    subjects: List<SubjectEntity> = emptyList(),
    isBeingDragged: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onQuickMove: (Long?) -> Unit = {},
    onDelete: () -> Unit,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var cardCenterOffset by remember { mutableStateOf(Offset.Zero) }

    val coverColor = try {
        Color(android.graphics.Color.parseColor(notebook.coverColorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardCenterOffset = it.boundsInRoot().center }
            .graphicsLayer {
                alpha = if (isBeingDragged) 0.35f else 1.0f
                scaleX = if (isBeingDragged) 0.98f else 1.0f
                scaleY = if (isBeingDragged) 0.98f else 1.0f
            }
            .pointerInput(notebook.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        onDragStart(cardCenterOffset + localOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBeingDragged) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                // Drag handle icon
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Hold to Drag",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(coverColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (notebook.isLocked) Icons.Default.Lock else Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(notebook.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${notebook.pageCount} pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (notebook.audioDurationMs > 0) {
                            Text("• 🎙️ Audio", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                        }
                        Text("• ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(notebook.updatedAt))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (notebook.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (notebook.isFavorite) Color(0xFFF59E0B) else Color.Gray
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }

                    NotebookContextMenu(
                        expanded = showMenu,
                        notebook = notebook,
                        subjects = subjects,
                        onDismiss = { showMenu = false },
                        onMove = { showMenu = false; onMove() },
                        onQuickMove = { subId ->
                            showMenu = false
                            onQuickMove(subId)
                        },
                        onRename = { showMenu = false; onRename() },
                        onToggleFavorite = { showMenu = false; onToggleFavorite() },
                        onDelete = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun NotebookContextMenu(
    expanded: Boolean,
    notebook: NotebookEntity,
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onQuickMove: (Long?) -> Unit,
    onRename: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var showQuickMoveSubmenu by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showQuickMoveSubmenu = false
            onDismiss()
        }
    ) {
        if (!showQuickMoveSubmenu) {
            // Quick Move Section
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Move to Folder", fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { showQuickMoveSubmenu = true }
            )

            DropdownMenuItem(
                text = { Text(if (notebook.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                leadingIcon = {
                    Icon(
                        if (notebook.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                        null,
                        tint = Color(0xFFF59E0B)
                    )
                },
                onClick = onToggleFavorite
            )

            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = onRename
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Delete Note", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = onDelete
            )
        } else {
            // Quick Move Submenu Listing Folders
            DropdownMenuItem(
                text = { Text("‹ Back", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                onClick = { showQuickMoveSubmenu = false }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Unassign option
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🚫 Unassigned (All Notes)")
                        if (notebook.subjectId == null) {
                            Icon(Icons.Default.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                onClick = {
                    showQuickMoveSubmenu = false
                    onQuickMove(null)
                }
            )

            subjects.forEach { subject ->
                val subColor = try {
                    Color(android.graphics.Color.parseColor(subject.colorHex))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primary
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(subColor))
                            Text(
                                subject.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (notebook.subjectId == subject.id) {
                                Icon(Icons.Default.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = {
                        showQuickMoveSubmenu = false
                        onQuickMove(subject.id)
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            DropdownMenuItem(
                text = { Text("More folder options...") },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                onClick = {
                    showQuickMoveSubmenu = false
                    onMove()
                }
            )
        }
    }
}

@Composable
fun NewNotebookDialog(
    subjects: List<SubjectEntity>,
    selectedSubjectId: Long?,
    onDismiss: () -> Unit,
    onCreate: (String, Long?, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var chosenSubjectId by remember { mutableStateOf(selectedSubjectId) }
    var chosenColorHex by remember { mutableStateOf("#4F46E5") }
    var chosenTemplate by remember { mutableStateOf("BLANK") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Notebook", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notebook Title") },
                    placeholder = { Text("e.g. Physics Lecture 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Cover Color:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val colors = listOf("#4F46E5", "#0D9488", "#EC4899", "#F59E0B", "#10B981", "#6366F1", "#8B5CF6", "#0F172A")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (chosenColorHex == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { chosenColorHex = hex }
                        )
                    }
                }

                Text("Initial Template:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val templates = listOf("BLANK" to "Blank", "LINED_WIDE" to "Lined Wide", "GRID_SMALL" to "Math Grid", "DOTTED" to "Dotted", "CORNELL" to "Cornell")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(templates) { (key, label) ->
                        val isSelected = chosenTemplate == key
                        Button(
                            onClick = { chosenTemplate = key },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.ifBlank { "Untitled Note" }, chosenSubjectId, chosenColorHex, chosenTemplate) },
                enabled = true
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    notebook: NotebookEntity,
    subjects: List<SubjectEntity>,
    onMove: (Long?) -> Unit,
    onCreateSubjectAndMove: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateSubfield by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var chosenColorHex by remember { mutableStateOf("#4F46E5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Move \"${notebook.title}\"", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Select a folder destination:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option: No Folder (All Notes)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMove(null) },
                    color = if (notebook.subjectId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = if (notebook.subjectId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "No Folder (Unassigned)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Visible in All Notes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (notebook.subjectId == null) {
                            Icon(Icons.Default.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Existing Folders list
                if (subjects.isNotEmpty()) {
                    Text("Folders / Subjects:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(subjects, key = { it.id }) { subject ->
                            val isCurrent = notebook.subjectId == subject.id
                            val subColor = try {
                                Color(android.graphics.Color.parseColor(subject.colorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onMove(subject.id) },
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(subColor)
                                    )
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = subColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = subject.name,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCurrent) {
                                        Icon(Icons.Default.Check, contentDescription = "Current Folder", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Create new folder & move
                if (!showCreateSubfield) {
                    OutlinedButton(
                        onClick = { showCreateSubfield = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ Create New Folder")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val folderColors = listOf("#4F46E5", "#0D9488", "#EC4899", "#F59E0B", "#10B981", "#6366F1", "#8B5CF6", "#DC2626")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(folderColors) { hex ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (chosenColorHex == hex) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        .clickable { chosenColorHex = hex }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCreateSubfield = false }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (newFolderName.isNotBlank()) {
                                        onCreateSubjectAndMove(newFolderName.trim(), chosenColorHex)
                                    }
                                },
                                enabled = newFolderName.isNotBlank()
                            ) {
                                Text("Create")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun NewSubjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#4F46E5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Subject / Divider", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name") },
                    placeholder = { Text("e.g. Computer Science") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Subject Color:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val colors = listOf("#4F46E5", "#0D9488", "#EC4899", "#F59E0B", "#10B981", "#6366F1", "#8B5CF6", "#DC2626")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (colorHex == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, colorHex) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


