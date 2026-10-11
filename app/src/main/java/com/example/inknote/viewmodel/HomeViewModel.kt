package com.example.inknote.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inknote.data.db.AppDatabase
import com.example.inknote.data.db.entities.NotebookEntity
import com.example.inknote.data.db.entities.SubjectEntity
import com.example.inknote.data.repository.NoteRepository
import com.example.inknote.pdf.PdfManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NoteFilter {
    ALL,
    FAVORITES,
    RECENT,
    SUBJECT
}

enum class ViewMode {
    GRID,
    LIST
}

data class HomeUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val notebooks: List<NotebookEntity> = emptyList(),
    val selectedFilter: NoteFilter = NoteFilter.ALL,
    val selectedSubjectId: Long? = null,
    val searchQuery: String = "",
    val viewMode: ViewMode = ViewMode.GRID,
    val isSidebarOpen: Boolean = true,
    val isLoading: Boolean = false,
    val showNewNotebookDialog: Boolean = false,
    val showNewSubjectDialog: Boolean = false,
    val selectedNotebookForMenu: NotebookEntity? = null,
    val searchResultsTextCount: Int = 0,
    val pdfImportProgress: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(NoteFilter.ALL)
    private val _selectedSubjectId = MutableStateFlow<Long?>(null)

    init {
        loadData()
        migrateWelcomeNoteIfNeeded()
    }

    private fun migrateWelcomeNoteIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val db = AppDatabase.getInstance(context)
            AppDatabase.updateWelcomeContentIfOld(db)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.allSubjects,
                repository.allNotebooks,
                _searchQuery,
                _selectedFilter,
                _selectedSubjectId
            ) { subjects, notebooks, query, filter, subjectId ->
                var filtered = notebooks

                if (query.isNotBlank()) {
                    filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
                } else {
                    filtered = when (filter) {
                        NoteFilter.ALL -> filtered
                        NoteFilter.FAVORITES -> filtered.filter { it.isFavorite }
                        NoteFilter.RECENT -> filtered.sortedByDescending { it.updatedAt }.take(20)
                        NoteFilter.SUBJECT -> filtered.filter { it.subjectId == subjectId }
                    }
                }

                _uiState.update { current ->
                    current.copy(
                        subjects = subjects,
                        notebooks = filtered,
                        selectedFilter = filter,
                        selectedSubjectId = subjectId,
                        searchQuery = query
                    )
                }
            }.collect()
        }
    }

    fun setFilter(filter: NoteFilter, subjectId: Long? = null) {
        _selectedFilter.value = filter
        _selectedSubjectId.value = subjectId
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    }

    fun showNewNotebookDialog(show: Boolean) {
        _uiState.update { it.copy(showNewNotebookDialog = show) }
    }

    fun showNewSubjectDialog(show: Boolean) {
        _uiState.update { it.copy(showNewSubjectDialog = show) }
    }

    fun setSelectedNotebookForMenu(notebook: NotebookEntity?) {
        _uiState.update { it.copy(selectedNotebookForMenu = notebook) }
    }

    fun createSubject(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createSubject(name.trim(), colorHex)
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
            if (_selectedSubjectId.value == subject.id) {
                setFilter(NoteFilter.ALL)
            }
        }
    }

    fun createNotebook(
        title: String,
        subjectId: Long?,
        coverColorHex: String,
        templateType: String,
        onCreated: (Long) -> Unit
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repository.createNotebook(
                title = title.trim(),
                subjectId = subjectId,
                coverColorHex = coverColorHex,
                templateType = templateType
            )
            onCreated(id)
        }
    }

    fun toggleFavorite(notebookId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(notebookId)
        }
    }

    fun deleteNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.deleteNotebook(notebook)
        }
    }

    fun renameNotebook(notebook: NotebookEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveNotebookToSubject(notebook: NotebookEntity, targetSubjectId: Long?) {
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(subjectId = targetSubjectId, updatedAt = System.currentTimeMillis()))
        }
    }

    fun importPdf(uri: Uri, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pdfImportProgress = "Preparing PDF...") }
            val fileName = PdfManager.getFileNameFromUri(getApplication(), uri)
            val cleanTitle = fileName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Imported Document" }

            val notebookId = repository.createNotebook(
                title = cleanTitle,
                subjectId = _selectedSubjectId.value,
                coverColorHex = "#EC4899",
                templateType = "BLANK"
            )

            // Remove default blank initial page so PDF pages fill in
            val defaultPages = repository.getPagesList(notebookId)
            defaultPages.forEach { repository.deletePage(it) }

            val localUri = withContext(Dispatchers.IO) {
                PdfManager.copyUriToTempFile(getApplication(), uri)
            }

            var hasNavigated = false
            viewModelScope.launch(Dispatchers.IO) {
                PdfManager.importPdfToNotebook(
                    context = getApplication(),
                    pdfUri = localUri,
                    notebookId = notebookId,
                    repository = repository,
                    onFirstPageReady = {
                        if (!hasNavigated) {
                            hasNavigated = true
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isLoading = false) }
                                onComplete(notebookId)
                            }
                        }
                    },
                    onProgress = { current, total ->
                        _uiState.update { it.copy(pdfImportProgress = "Importing page $current of $total") }
                    }
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, pdfImportProgress = null) }
                    if (!hasNavigated) {
                        hasNavigated = true
                        onComplete(notebookId)
                    }
                }
            }
        }
    }

    fun appendPdfToNotebook(uri: Uri, notebookId: Long, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pdfImportProgress = "Preparing PDF...") }
            val localUri = withContext(Dispatchers.IO) {
                PdfManager.copyUriToTempFile(getApplication(), uri)
            }
            var hasNavigated = false
            viewModelScope.launch(Dispatchers.IO) {
                PdfManager.importPdfToNotebook(
                    context = getApplication(),
                    pdfUri = localUri,
                    notebookId = notebookId,
                    repository = repository,
                    isAppending = true,
                    onFirstPageReady = {
                        if (!hasNavigated) {
                            hasNavigated = true
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isLoading = false) }
                                onComplete(notebookId)
                            }
                        }
                    },
                    onProgress = { current, total ->
                        _uiState.update { it.copy(pdfImportProgress = "Appending page $current of $total") }
                    }
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, pdfImportProgress = null) }
                    if (!hasNavigated) {
                        hasNavigated = true
                        onComplete(notebookId)
                    }
                }
            }
        }
    }

    fun importSharedPdfs(uris: List<Uri>, title: String, subjectId: Long?, onComplete: (Long) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pdfImportProgress = "Preparing PDF...") }
            val cleanTitle = title.ifBlank { "Shared Document" }

            val notebookId = repository.createNotebook(
                title = cleanTitle,
                subjectId = subjectId,
                coverColorHex = "#6366F1",
                templateType = "BLANK"
            )

            // Remove default blank initial page so PDF pages fill in
            val defaultPages = repository.getPagesList(notebookId)
            defaultPages.forEach { repository.deletePage(it) }

            val localUris = withContext(Dispatchers.IO) {
                uris.map { PdfManager.copyUriToTempFile(getApplication(), it) }
            }

            var hasNavigated = false
            viewModelScope.launch(Dispatchers.IO) {
                localUris.forEachIndexed { index, localUri ->
                    PdfManager.importPdfToNotebook(
                        context = getApplication(),
                        pdfUri = localUri,
                        notebookId = notebookId,
                        repository = repository,
                        isAppending = index > 0,
                        onFirstPageReady = {
                            if (!hasNavigated) {
                                hasNavigated = true
                                withContext(Dispatchers.Main) {
                                    _uiState.update { it.copy(isLoading = false) }
                                    onComplete(notebookId)
                                }
                            }
                        },
                        onProgress = { current, total ->
                            val prefix = if (localUris.size > 1) "File ${index + 1}/${localUris.size}: " else ""
                            _uiState.update { it.copy(pdfImportProgress = "${prefix}Importing page $current of $total") }
                        }
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, pdfImportProgress = null) }
                    if (!hasNavigated) {
                        hasNavigated = true
                        onComplete(notebookId)
                    }
                }
            }
        }
    }

    fun appendSharedPdfs(uris: List<Uri>, notebookId: Long, onComplete: (Long) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pdfImportProgress = "Preparing PDF...") }
            val localUris = withContext(Dispatchers.IO) {
                uris.map { PdfManager.copyUriToTempFile(getApplication(), it) }
            }

            var hasNavigated = false
            viewModelScope.launch(Dispatchers.IO) {
                localUris.forEachIndexed { index, localUri ->
                    PdfManager.importPdfToNotebook(
                        context = getApplication(),
                        pdfUri = localUri,
                        notebookId = notebookId,
                        repository = repository,
                        isAppending = true,
                        onFirstPageReady = {
                            if (!hasNavigated) {
                                hasNavigated = true
                                withContext(Dispatchers.Main) {
                                    _uiState.update { it.copy(isLoading = false) }
                                    onComplete(notebookId)
                                }
                            }
                        },
                        onProgress = { current, total ->
                            val prefix = if (localUris.size > 1) "File ${index + 1}/${localUris.size}: " else ""
                            _uiState.update { it.copy(pdfImportProgress = "${prefix}Appending page $current of $total") }
                        }
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, pdfImportProgress = null) }
                    if (!hasNavigated) {
                        hasNavigated = true
                        onComplete(notebookId)
                    }
                }
            }
        }
    }
}
