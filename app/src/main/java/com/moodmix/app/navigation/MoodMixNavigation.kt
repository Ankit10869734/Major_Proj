package com.moodmix.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodmix.app.data.OnboardingDataStore
import com.moodmix.app.data.OnboardingState
import com.moodmix.app.ui.playback.MiniPlayer
import com.moodmix.app.ui.playback.NowPlayingScreen
import com.moodmix.app.ui.screens.generate.GenerateScreen
import com.moodmix.app.ui.screens.home.HomeScreen
import com.moodmix.app.ui.screens.library.LibraryScreen
import com.moodmix.app.ui.screens.onboarding.OnboardingScreen
import com.moodmix.app.ui.screens.search.SearchScreen
import com.moodmix.app.ui.screens.tools.ToolsScreen
import com.moodmix.app.ui.theme.MintPrimary
import kotlinx.coroutines.launch

@Composable
fun MoodMixNavigation(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onboardingDataStore = remember { OnboardingDataStore(context) }
    val onboardingState by onboardingDataStore.onboardingState.collectAsStateWithLifecycle(initialValue = OnboardingState.Loading)

    var isReplayingTutorial by remember { mutableStateOf(false) }

    val shouldShowOnboarding = isReplayingTutorial || onboardingState == OnboardingState.NotCompleted

    if (onboardingState == OnboardingState.Loading && !isReplayingTutorial) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    } else if (shouldShowOnboarding) {
        OnboardingScreen(
            onFinishOnboarding = {
                scope.launch {
                    onboardingDataStore.setOnboardingCompleted(true)
                    isReplayingTutorial = false
                }
            }
        )
    } else {
        val pagerState = rememberPagerState(initialPage = 0) { 5 }
        var isNowPlayingExpanded by remember { mutableStateOf(false) }
        var pendingInitialPrompt by remember { mutableStateOf("") }

        BackHandler(enabled = pagerState.currentPage != 0) {
            scope.launch {
                pagerState.animateScrollToPage(0)
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                Column {
                    MiniPlayer(
                        onExpand = { isNowPlayingExpanded = true }
                    )

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        // 1. Discover Tab
                        NavigationBarItem(
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(0) }
                            },
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                            label = { Text("Discover", fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintPrimary,
                                selectedTextColor = MintPrimary,
                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                            )
                        )

                        // 2. Search Tab
                        NavigationBarItem(
                            selected = pagerState.currentPage == 1,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search", fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintPrimary,
                                selectedTextColor = MintPrimary,
                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                            )
                        )

                        // 3. Create Tab (Central Prominent Destination)
                        NavigationBarItem(
                            selected = pagerState.currentPage == 2,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(2) }
                            },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (pagerState.currentPage == 2) MintPrimary else MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Create",
                                        tint = if (pagerState.currentPage == 2) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            label = { Text("Create", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintPrimary,
                                selectedTextColor = MintPrimary,
                                indicatorColor = Color.Transparent
                            )
                        )

                        // 4. Extras Tab
                        NavigationBarItem(
                            selected = pagerState.currentPage == 3,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(3) }
                            },
                            icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Extras") },
                            label = { Text("Extras", fontWeight = if (pagerState.currentPage == 3) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintPrimary,
                                selectedTextColor = MintPrimary,
                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                            )
                        )

                        // 5. Library Tab
                        NavigationBarItem(
                            selected = pagerState.currentPage == 4,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(4) }
                            },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library", fontWeight = if (pagerState.currentPage == 4) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MintPrimary,
                                selectedTextColor = MintPrimary,
                                indicatorColor = MintPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        paddingValues = PaddingValues(0.dp),
                        onNavigateToGenerate = { prompt ->
                            pendingInitialPrompt = prompt
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onNavigateToTools = {
                            scope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )

                    1 -> SearchScreen(
                        onNavigateToGenerate = { prompt ->
                            pendingInitialPrompt = prompt
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                    )

                    2 -> GenerateScreen(
                        initialPrompt = pendingInitialPrompt
                    )

                    3 -> ToolsScreen(
                        onNavigateToGenerate = { prompt ->
                            pendingInitialPrompt = prompt
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onReplayTutorial = {
                            isReplayingTutorial = true
                        }
                    )

                    4 -> LibraryScreen(
                        onNavigateToGenerate = { prompt ->
                            pendingInitialPrompt = prompt
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                    )
                }
            }

            if (isNowPlayingExpanded) {
                NowPlayingScreen(onDismiss = { isNowPlayingExpanded = false })
            }
        }
    }
}
