package com.thedesitadka.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thedesitadka.app.navigation.Screen
import com.thedesitadka.app.ui.screens.DetailsScreen
import com.thedesitadka.app.ui.screens.DiagnosticsScreen
import com.thedesitadka.app.ui.screens.DownloadsScreen
import com.thedesitadka.app.ui.screens.HomeScreen
import com.thedesitadka.app.ui.screens.PlayerScreen
import com.thedesitadka.app.ui.screens.ProviderScreen
import com.thedesitadka.app.ui.screens.SearchScreen
import com.thedesitadka.app.ui.screens.SettingsScreen
import com.thedesitadka.app.ui.theme.TheDesiTadkaTheme
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.VideoItem

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TheDesiTadkaApp
        val container = app.container

        lifecycle.addObserver(container.monetizationManager.lifecycleManager)

        setContent {
            TheDesiTadkaTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppMainContent(container)
                }
            }
        }
    }
}

@Composable
fun AppMainContent(container: AppContainer) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Active state holders for screens that need objects
    var activeVideoItem by remember { mutableStateOf<VideoItem?>(null) }
    var activeMediaSource by remember { mutableStateOf<MediaSource?>(null) }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Downloads.route,
        Screen.Settings.route
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val navItems = listOf(
                        Triple(Screen.Home.route, "Home", Icons.Default.Home),
                        Triple(Screen.Search.route, "Search", Icons.Default.Search),
                        Triple(Screen.Downloads.route, "Downloads", Icons.Default.Download),
                        Triple(Screen.Settings.route, "Settings", Icons.Default.Settings)
                    )

                    navItems.forEach { (route, label, icon) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = icon, contentDescription = label) },
                            label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                val context = LocalContext.current
                val activity = context as? Activity
                BackHandler(enabled = currentRoute == Screen.Home.route) {
                    activity?.finish()
                }

                HomeScreen(
                    providerEngine = container.providerEngine,
                    dashboardRepository = container.dashboardRepository,
                    watchHistoryFlow = container.database.watchHistoryDao().getRecentHistory(),
                    monetizationManager = container.monetizationManager,
                    onVideoClick = { video ->
                        activeVideoItem = video
                        navController.navigate(Screen.Details.route)
                    },
                    onProviderClick = { providerId ->
                        navController.navigate(Screen.Provider.createRoute(providerId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            composable(Screen.Provider.route) { backStackEntry ->
                val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
                ProviderScreen(
                    providerId = providerId,
                    providerEngine = container.providerEngine,
                    onVideoClick = { video ->
                        activeVideoItem = video
                        navController.navigate(Screen.Details.route)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    providerEngine = container.providerEngine,
                    onVideoClick = { video ->
                        activeVideoItem = video
                        navController.navigate(Screen.Details.route)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Details.route) {
                activeVideoItem?.let { video ->
                    DetailsScreen(
                        videoItem = video,
                        providerEngine = container.providerEngine,
                        favoriteDao = container.database.favoriteDao(),
                        downloadRepository = container.downloadRepository,
                        monetizationManager = container.monetizationManager,
                        onPlayClick = { vid, src ->
                            activeVideoItem = vid
                            activeMediaSource = src
                            navController.navigate(Screen.Player.route)
                        },
                        onRelatedClick = { relVideo ->
                            activeVideoItem = relVideo
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.Player.route) {
                if (activeVideoItem != null && activeMediaSource != null) {
                    PlayerScreen(
                        videoItem = activeVideoItem!!,
                        mediaSource = activeMediaSource!!,
                        watchHistoryDao = container.database.watchHistoryDao(),
                        monetizationManager = container.monetizationManager,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    downloadRepository = container.downloadRepository,
                    monetizationManager = container.monetizationManager,
                    onPlayOfflineClick = { video, source ->
                        activeVideoItem = video
                        activeMediaSource = source
                        navController.navigate(Screen.Player.route)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    configRepository = container.configRepository,
                    providerEngine = container.providerEngine,
                    preferenceStore = container.preferenceStore,
                    monetizationManager = container.monetizationManager,
                    onResetToDefault = {
                        val defaultManifest = container.getDefaultManifest()
                        container.configRepository.updateManifest(defaultManifest)
                        container.providerEngine.updateFromManifest(defaultManifest)
                    },
                    onDiagnosticsClick = { navController.navigate(Screen.Diagnostics.route) },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(
                    configRepository = container.configRepository,
                    providerEngine = container.providerEngine,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
