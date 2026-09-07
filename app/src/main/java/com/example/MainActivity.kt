package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.inknote.data.auth.AuthRepository
import com.example.inknote.data.db.entities.UserAccountEntity
import com.example.inknote.ui.auth.AuthScreen
import com.example.inknote.ui.canvas.NoteCanvasScreen
import com.example.inknote.ui.home.HomeScreen
import com.example.inknote.viewmodel.HomeViewModel
import com.example.inknote.viewmodel.NoteCanvasViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val authRepository = remember { AuthRepository.getInstance(context) }
                    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
                    val isInitialized by authRepository.isInitialized.collectAsStateWithLifecycle()

                    if (!isInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (currentUser == null) {
                        AuthScreen(
                            authRepository = authRepository,
                            onAuthSuccess = {
                                // User logged in / signed up, currentUser state updates automatically
                            }
                        )
                    } else {
                        key(currentUser?.id) {
                            InkNoteApp(
                                authRepository = authRepository,
                                user = currentUser!!
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InkNoteApp(
    authRepository: AuthRepository,
    user: UserAccountEntity
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel(key = "home_${user.id}")

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = homeViewModel,
                authRepository = authRepository,
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
            val canvasViewModel: NoteCanvasViewModel = viewModel(key = "canvas_${user.id}_$notebookId")
            NoteCanvasScreen(
                notebookId = notebookId,
                viewModel = canvasViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
