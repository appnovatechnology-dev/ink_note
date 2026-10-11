package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inknote.ui.canvas.NoteCanvasScreen
import com.example.inknote.ui.home.HomeScreen
import com.example.inknote.ui.home.SharedPdfImportDialog
import com.example.inknote.viewmodel.HomeViewModel
import com.example.inknote.viewmodel.NoteCanvasViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val sharedPdfUris = mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            val themeMode by com.example.inknote.data.preferences.ThemeStateHolder.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InksteadApp(
                        sharedUris = sharedPdfUris.value,
                        onClearSharedUris = { sharedPdfUris.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        val isPdfType = type != null && (type == "application/pdf" || type == "application/octet-stream" || type == "*/*")
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = extractPdfUris(intent)
            if (uris.isNotEmpty()) {
                sharedPdfUris.value = uris
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractPdfUris(intent: Intent): List<Uri> {
        val result = mutableListOf<Uri>()

        // 1. ClipData items
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { result.add(it) }
            }
        }

        // 2. EXTRA_STREAM single Uri
        if (result.isEmpty()) {
            val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                result.add(streamUri)
            }
        }

        // 3. EXTRA_STREAM ArrayList
        if (result.isEmpty()) {
            val streamList: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (!streamList.isNullOrEmpty()) {
                result.addAll(streamList.filterNotNull())
            }
        }

        // 4. Intent data fallback
        if (result.isEmpty() && intent.data != null) {
            result.add(intent.data!!)
        }

        return result.distinct()
    }
}

@Composable
fun InksteadApp(
    sharedUris: List<Uri>?,
    onClearSharedUris: () -> Unit
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()
    val canvasViewModel: NoteCanvasViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenNotebook = { notebookId ->
                    navController.navigate("canvas/$notebookId")
                }
            )
        }

        composable(
            route = "canvas/{notebookId}",
            arguments = listOf(
                navArgument("notebookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getLong("notebookId") ?: 0L
            NoteCanvasScreen(
                notebookId = notebookId,
                viewModel = canvasViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    // Shared PDF receiver dialog when launched or shared from another app
    if (!sharedUris.isNullOrEmpty()) {
        SharedPdfImportDialog(
            uris = sharedUris,
            notebooks = homeUiState.notebooks,
            subjects = homeUiState.subjects,
            onDismiss = onClearSharedUris,
            onCreateNewNote = { title, subjectId ->
                homeViewModel.importSharedPdfs(sharedUris, title, subjectId) { newNotebookId ->
                    onClearSharedUris()
                    navController.navigate("canvas/$newNotebookId")
                }
            },
            onAddToExistingNote = { targetNotebookId ->
                homeViewModel.appendSharedPdfs(sharedUris, targetNotebookId) { notebookId ->
                    onClearSharedUris()
                    navController.navigate("canvas/$notebookId")
                }
            }
        )
    }
}
