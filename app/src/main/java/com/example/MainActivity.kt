package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
                    InksteadApp()
                }
            }
        }
    }
}

@Composable
fun InksteadApp() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()
    val canvasViewModel: NoteCanvasViewModel = viewModel()

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
}
