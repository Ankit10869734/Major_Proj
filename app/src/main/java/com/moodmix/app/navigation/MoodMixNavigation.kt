package com.moodmix.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moodmix.app.ui.screens.generate.GenerateScreen
import com.moodmix.app.ui.screens.home.HomeScreen
import com.moodmix.app.ui.screens.library.LibraryScreen
import com.moodmix.app.ui.screens.tools.ToolsScreen

@Composable
fun MoodMixNavigation(
    modifier: Modifier = Modifier
) {

    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(

        bottomBar = {

            NavigationBar {

                NavigationBarItem(
                    selected = currentRoute?.startsWith("home") == true,
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Text("⌂")
                    },
                    label = {
                        Text("Home")
                    }
                )

                NavigationBarItem(
                    selected = currentRoute?.startsWith("generate") == true,
                    onClick = {
                        navController.navigate("generate") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Text("♫")
                    },
                    label = {
                        Text("Generate")
                    }
                )

                NavigationBarItem(
                    selected = currentRoute == "tools",
                    onClick = {
                        navController.navigate("tools") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Text("⚙")
                    },
                    label = {
                        Text("Tools")
                    }
                )

                NavigationBarItem(
                    selected = currentRoute == "library",
                    onClick = {
                        navController.navigate("library") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Text("♥")
                    },
                    label = {
                        Text("Library")
                    }
                )
            }
        }

    ) { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = modifier.fillMaxSize()
        ) {

            composable("home") {

                HomeScreen(
                    paddingValues = paddingValues,
                    onNavigateToGenerate = { prompt ->
                        val encodedPrompt = Uri.encode(prompt)
                        navController.navigate("generate?initialPrompt=$encodedPrompt")
                    }
                )
            }

            composable(
                route = "generate?initialPrompt={initialPrompt}",
                arguments = listOf(
                    navArgument("initialPrompt") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->

                val initialPrompt = entry.arguments?.getString("initialPrompt").orEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    GenerateScreen(initialPrompt = initialPrompt)
                }
            }

            composable("tools") {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    ToolsScreen(
                        onNavigateToGenerate = { prompt ->
                            val encodedPrompt = Uri.encode(prompt)
                            navController.navigate("generate?initialPrompt=$encodedPrompt")
                        }
                    )
                }
            }

            composable("library") {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    LibraryScreen(
                        onNavigateToGenerate = { prompt ->
                            val encodedPrompt = Uri.encode(prompt)
                            navController.navigate("generate?initialPrompt=$encodedPrompt")
                        }
                    )
                }
            }
        }
    }
}
