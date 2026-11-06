package com.danza.noticontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.danza.noticontrol.ui.screens.AppSelectorScreen
import com.danza.noticontrol.ui.screens.HomeScreen
import com.danza.noticontrol.ui.screens.LogScreen
import com.danza.noticontrol.ui.theme.NotiControlTheme
import com.danza.noticontrol.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotiControlTheme {
                NotiControlApp()
            }
        }
    }
}

@Composable
fun NotiControlApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToLog = { navController.navigate("log") },
                onNavigateToAppSelector = { navController.navigate("app_selector") }
            )
        }
        composable("log") {
            LogScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("app_selector") {
            AppSelectorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
