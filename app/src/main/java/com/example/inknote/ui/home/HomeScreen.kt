package com.example.inknote.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inknote.data.db.entities.NotebookEntity
import com.example.inknote.data.db.entities.SubjectEntity
import com.example.inknote.data.model.PalmRejectionMode
import com.example.inknote.ui.settings.AppSettingsDialog
import com.example.inknote.ui.canvas.components.SecurityPinDialog
import com.example.inknote.ui.canvas.components.TemplateLayoutRenderer
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
    val context = LocalContext.current
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
    var showSettingsDialog by remember { mutableStateOf(false) }

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
    val dropTargetBounds = remember { mutableMapOf<Long, Rect>() }

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

    val handleDropLambda: (Long?) -> Unit = remember(viewModel, uiState.subjects, draggedNotebook) {
        { targetSubjectId -> handleDrop(targetSubjectId) }
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
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.viewMode == ViewMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(170.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "home_top_header", contentType = "header") {
                                HomeHeaderSection(
                                    draggedNotebook = draggedNotebook,
                                    activeDropTargetId = activeDropTargetId,
                                    dropTargetBounds = dropTargetBounds,
                                    handleDrop = handleDropLambda,
                                    subjects = uiState.subjects,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onSelectTemplate = { templateType, defaultTitle ->
                                        viewModel.createNotebook(defaultTitle, uiState.selectedSubjectId, "#4F46E5", templateType) { id ->
                                            onOpenNotebook(id)
                                        }
                                    },
                                    onSeeMoreTemplates = {
                                        viewModel.showNewNotebookDialog(true)
                                    }
                                )
                            }

                            if (uiState.notebooks.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state", contentType = "empty") {
                                    EmptyNotebooksState(
                                        searchQuery = uiState.searchQuery,
                                        onCreateFirst = { viewModel.showNewNotebookDialog(true) }
                                    )
                                }
                            } else {
                                items(uiState.notebooks, key = { it.id }, contentType = { "notebook_card" }) { notebook ->
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
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item(key = "home_top_header", contentType = "header") {
                                HomeHeaderSection(
                                    draggedNotebook = draggedNotebook,
                                    activeDropTargetId = activeDropTargetId,
                                    dropTargetBounds = dropTargetBounds,
                                    handleDrop = handleDropLambda,
                                    subjects = uiState.subjects,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onSelectTemplate = { templateType, defaultTitle ->
                                        viewModel.createNotebook(defaultTitle, uiState.selectedSubjectId, "#4F46E5", templateType) { id ->
                                            onOpenNotebook(id)
                                        }
                                    },
                                    onSeeMoreTemplates = {
                                        viewModel.showNewNotebookDialog(true)
                                    }
                                )
                            }

                            if (uiState.notebooks.isEmpty()) {
                                item(key = "empty_state", contentType = "empty") {
                                    EmptyNotebooksState(
                                        searchQuery = uiState.searchQuery,
                                        onCreateFirst = { viewModel.showNewNotebookDialog(true) }
                                    )
                                }
                            } else {
                                items(uiState.notebooks, key = { it.id }, contentType = { "notebook_card" }) { notebook ->
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

    if (showSettingsDialog) {
        val settings = remember { com.example.inknote.data.preferences.SettingsManager(context) }
        var currentPalmMode by remember { mutableStateOf(settings.palmRejectionMode) }
        AppSettingsDialog(
            currentPalmMode = currentPalmMode,
            onSetPalmMode = {
                currentPalmMode = it
                settings.palmRejectionMode = it
            },
            onResetToolbar = {
                settings.resetToolbarPosition()
            },
            onDismiss = { showSettingsDialog = false }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Image(
                    painter = painterResource(com.example.R.drawable.inkstead_logo_mark_1790172297033),
                    contentDescription = "Inkstead Logo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Text("Inkstead", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
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
            Text("Offline-First", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val layoutCoordsRef = remember { arrayOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val coverColor = remember(notebook.coverColorHex) {
        try {
            Color(android.graphics.Color.parseColor(notebook.coverColorHex))
        } catch (e: Exception) {
            Color(0xFF6366F1)
        }
    }

    val formattedDate = remember(notebook.updatedAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(notebook.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { layoutCoordsRef[0] = it }
            .graphicsLayer {
                alpha = if (isBeingDragged) 0.35f else 1.0f
                scaleX = if (isBeingDragged) 0.95f else 1.0f
                scaleY = if (isBeingDragged) 0.95f else 1.0f
            }
            .pointerInput(notebook.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        val rootCenter = layoutCoordsRef[0]?.boundsInRoot()?.center ?: Offset.Zero
                        onDragStart(rootCenter + localOffset)
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
                        text = formattedDate,
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
    val layoutCoordsRef = remember { arrayOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val coverColor = remember(notebook.coverColorHex) {
        try {
            Color(android.graphics.Color.parseColor(notebook.coverColorHex))
        } catch (e: Exception) {
            Color(0xFF6366F1)
        }
    }

    val formattedDate = remember(notebook.updatedAt) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(notebook.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { layoutCoordsRef[0] = it }
            .graphicsLayer {
                alpha = if (isBeingDragged) 0.35f else 1.0f
                scaleX = if (isBeingDragged) 0.98f else 1.0f
                scaleY = if (isBeingDragged) 0.98f else 1.0f
            }
            .pointerInput(notebook.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        val rootCenter = layoutCoordsRef[0]?.boundsInRoot()?.center ?: Offset.Zero
                        onDragStart(rootCenter + localOffset)
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
                        Text("• $formattedDate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                val templates = listOf(
                    "BLANK" to "Blank",
                    "LINED_WIDE" to "Lined Wide",
                    "LINED_NARROW" to "Lined Narrow",
                    "GRID_SMALL" to "Math Grid",
                    "GRID_LARGE" to "Large Grid",
                    "DOTTED" to "Dotted",
                    "CORNELL" to "Cornell",
                    "ISOMETRIC" to "Isometric",
                    "MUSIC" to "Music Staff",
                    "HABIT_TRACKER" to "Habit Tracker",
                    "DAILY_GOALS" to "Daily Goals",
                    "WEEKLY_PLANNER" to "Weekly Planner",
                    "DAILY_PLANNER" to "Daily Planner",
                    "GOAL_SETTING" to "Goal Setting",
                    "ASSIGNMENT_PLANNER" to "Assignment Planner",
                    "MIND_MAP" to "Mind Map",
                    "SEMESTER_OVERVIEW" to "Semester Overview",
                    "MONTHLY_PLANNER" to "Monthly Planner"
                )
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

private val HomeTemplateItems = listOf(
    TemplateCardItem("HABIT_TRACKER", "Habit Tracker", "Habit Tracker"),
    TemplateCardItem("DAILY_GOALS", "Daily Goals", "Daily Goals"),
    TemplateCardItem("WEEKLY_PLANNER", "Weekly Planner", "Weekly Planner"),
    TemplateCardItem("DAILY_PLANNER", "Daily Planner", "Daily Planner"),
    TemplateCardItem("GOAL_SETTING", "Goal Setting", "Goal Setting"),
    TemplateCardItem("ASSIGNMENT_PLANNER", "Assignment Planner", "Assignment Planner"),
    TemplateCardItem("MIND_MAP", "Mind Map", "Mind Map"),
    TemplateCardItem("SEMESTER_OVERVIEW", "Semester Overview", "Semester Overview"),
    TemplateCardItem("MONTHLY_PLANNER", "Monthly Planner", "Monthly Planner"),
    TemplateCardItem("CORNELL", "Cornell Notes", "Cornell Notes"),
    TemplateCardItem("PLANNER", "Daily Schedule", "Daily Planner"),
    TemplateCardItem("BLANK", "Blank Page", "Blank Note"),
    TemplateCardItem("LINED_WIDE", "Lined Wide", "Lined Note"),
    TemplateCardItem("GRID_SMALL", "Math Grid", "Grid Note"),
    TemplateCardItem("DOTTED", "Dotted", "Dotted Note"),
    TemplateCardItem("MUSIC", "Music Staff", "Music Sheet"),
    TemplateCardItem("ISOMETRIC", "Isometric", "Isometric Sketch")
)

@Composable
private fun StartWithTemplateSection(
    onSelectTemplate: (templateType: String, defaultTitle: String) -> Unit,
    onSeeMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Start with a template",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = onSeeMore,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "See more",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.2f

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(HomeTemplateItems, key = { it.type }) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(136.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTemplate(item.type, item.title) }
                ) {
                    TemplateThumbnailPreview(
                        templateType = item.type,
                        isDarkTheme = isDarkTheme
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateThumbnailPreview(
    templateType: String,
    isDarkTheme: Boolean
) {
    val paperBg = if (isDarkTheme) Color(0xFF232328) else Color.White
    val borderColor = if (isDarkTheme) Color(0xFF383842) else Color(0xFFE2E8F0)
    val lineColor = if (isDarkTheme) Color(0x38E4E4E7) else Color(0x3364748B)
    val accentColor = when (templateType) {
        "HABIT_TRACKER" -> Color(0xFFF97316)
        "DAILY_GOALS", "GOAL_SETTING" -> Color(0xFF3B82F6)
        "WEEKLY_PLANNER", "DAILY_PLANNER", "PLANNER", "MONTHLY_PLANNER" -> Color(0xFF6366F1)
        "MIND_MAP" -> Color(0xFF8B5CF6)
        "ASSIGNMENT_PLANNER", "SEMESTER_OVERVIEW" -> Color(0xFFEC4899)
        "CORNELL" -> Color(0xFF0D9488)
        "GRID_SMALL", "GRID_LARGE" -> Color(0xFF10B981)
        "DOTTED", "ISOMETRIC" -> Color(0xFFF59E0B)
        "MUSIC" -> Color(0xFFE11D48)
        else -> Color(0xFF64748B)
    }
    val icon = when (templateType) {
        "HABIT_TRACKER" -> Icons.Default.TrackChanges
        "DAILY_GOALS", "GOAL_SETTING" -> Icons.Default.Flag
        "WEEKLY_PLANNER", "DAILY_PLANNER", "PLANNER", "MONTHLY_PLANNER" -> Icons.Default.DateRange
        "MIND_MAP" -> Icons.Default.AccountTree
        "ASSIGNMENT_PLANNER", "SEMESTER_OVERVIEW" -> Icons.Default.School
        "CORNELL" -> Icons.Default.MenuBook
        "GRID_SMALL", "GRID_LARGE" -> Icons.Default.GridOn
        "DOTTED", "ISOMETRIC" -> Icons.Default.Grain
        "MUSIC" -> Icons.Default.MusicNote
        "LINED_WIDE", "LINED_NARROW", "RULED" -> Icons.Default.FormatAlignLeft
        else -> Icons.Default.Description
    }

    Surface(
        modifier = Modifier
            .width(136.dp)
            .height(184.dp),
        shape = RoundedCornerShape(12.dp),
        color = paperBg,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header icon badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(6.dp)
                        .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                )
            }

            // Simulated paper pattern
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    when (templateType) {
                        "HABIT_TRACKER" -> {
                            val cx = w / 2f
                            val cy = h / 2f
                            val r = minOf(w, h) * 0.42f
                            drawCircle(accentColor.copy(alpha = 0.12f), radius = r, center = Offset(cx, cy))
                            drawCircle(lineColor, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                            drawCircle(lineColor, radius = r * 0.65f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                            drawCircle(lineColor, radius = r * 0.35f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(0.8f))
                        }
                        "GRID_SMALL", "GRID_LARGE" -> {
                            val spacing = if (templateType == "GRID_SMALL") 12f else 18f
                            var x = spacing
                            while (x < w) {
                                drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.8f)
                                x += spacing
                            }
                            var y = spacing
                            while (y < h) {
                                drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.8f)
                                y += spacing
                            }
                        }
                        "DOTTED", "ISOMETRIC" -> {
                            val spacing = 14f
                            var x = spacing
                            while (x < w) {
                                var y = spacing
                                while (y < h) {
                                    drawCircle(lineColor, radius = 1.2f, center = Offset(x, y))
                                    y += spacing
                                }
                                x += spacing
                            }
                        }
                        "CORNELL" -> {
                            val cueX = w * 0.32f
                            val summaryY = h * 0.75f
                            drawLine(accentColor.copy(alpha = 0.4f), Offset(cueX, 0f), Offset(cueX, summaryY), strokeWidth = 1.2f)
                            drawLine(accentColor.copy(alpha = 0.4f), Offset(0f, summaryY), Offset(w, summaryY), strokeWidth = 1.2f)
                            val step = 14f
                            var y = step
                            while (y < summaryY) {
                                drawLine(lineColor, Offset(cueX + 6f, y), Offset(w - 4f, y), strokeWidth = 0.8f)
                                y += step
                            }
                        }
                        "WEEKLY_PLANNER", "DAILY_PLANNER", "PLANNER", "MONTHLY_PLANNER" -> {
                            val colW = w / 3f
                            drawLine(lineColor, Offset(colW, 0f), Offset(colW, h), strokeWidth = 1f)
                            drawLine(lineColor, Offset(colW * 2f, 0f), Offset(colW * 2f, h), strokeWidth = 1f)
                            drawLine(lineColor, Offset(0f, 16f), Offset(w, 16f), strokeWidth = 1.2f)
                            var y = 30f
                            while (y < h) {
                                drawLine(lineColor.copy(alpha = 0.5f), Offset(4f, y), Offset(w - 4f, y), strokeWidth = 0.7f)
                                y += 14f
                            }
                        }
                        "MIND_MAP" -> {
                            val cx = w / 2f
                            val cy = h / 2f
                            drawCircle(accentColor.copy(alpha = 0.25f), radius = 12f, center = Offset(cx, cy))
                            drawCircle(accentColor, radius = 12f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                            val nodes = listOf(
                                Offset(w * 0.2f, h * 0.25f),
                                Offset(w * 0.8f, h * 0.25f),
                                Offset(w * 0.2f, h * 0.75f),
                                Offset(w * 0.8f, h * 0.75f)
                            )
                            for (n in nodes) {
                                drawLine(accentColor.copy(alpha = 0.5f), Offset(cx, cy), n, strokeWidth = 1f)
                                drawCircle(accentColor.copy(alpha = 0.2f), radius = 8f, center = n)
                                drawCircle(accentColor, radius = 8f, center = n, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                            }
                        }
                        "MUSIC" -> {
                            val staffLines = 5
                            val spacing = 7f
                            val topY = (h - (staffLines * spacing * 2f)) / 2f
                            for (staff in 0..1) {
                                val sY = topY + staff * (staffLines * spacing + 14f)
                                for (l in 0 until staffLines) {
                                    val y = sY + l * spacing
                                    drawLine(lineColor, Offset(4f, y), Offset(w - 4f, y), strokeWidth = 0.9f)
                                }
                            }
                        }
                        else -> {
                            // Ruled / Lined / Goals / Default
                            val step = 14f
                            var y = step
                            while (y < h) {
                                drawLine(lineColor, Offset(4f, y), Offset(w - 4f, y), strokeWidth = 0.8f)
                                y += step
                            }
                        }
                    }
                }
            }

            // Bottom subtle indicator bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun HomeHeaderSection(
    draggedNotebook: NotebookEntity?,
    activeDropTargetId: Long?,
    dropTargetBounds: MutableMap<Long, Rect>,
    handleDrop: (Long?) -> Unit,
    subjects: List<SubjectEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectTemplate: (String, String) -> Unit,
    onSeeMoreTemplates: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                        items(subjects, key = { it.id }) { sub ->
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
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search notes and typed text...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
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

        // Start with a template carousel section
        if (searchQuery.isBlank()) {
            StartWithTemplateSection(
                onSelectTemplate = onSelectTemplate,
                onSeeMore = onSeeMoreTemplates
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EmptyNotebooksState(
    searchQuery: String,
    onCreateFirst: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
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
                text = if (searchQuery.isNotBlank()) "No notes matching \"$searchQuery\"" else "No notes here yet",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCreateFirst) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create First Note")
            }
        }
    }
}

private data class TemplateCardItem(
    val type: String,
    val label: String,
    val title: String
)



