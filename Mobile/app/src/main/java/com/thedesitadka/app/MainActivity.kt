package com.thedesitadka.app

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thedesitadka.app.navigation.Screen
import com.thedesitadka.app.security.AndroidApkIntegrityChecker
import com.thedesitadka.app.ui.screens.DetailsScreen
import com.thedesitadka.app.ui.screens.DiagnosticsScreen
import com.thedesitadka.app.ui.screens.DownloadsScreen
import com.thedesitadka.app.ui.screens.HomeScreen
import com.thedesitadka.app.ui.screens.PlayerScreen
import com.thedesitadka.app.ui.screens.ProviderScreen
import com.thedesitadka.app.ui.screens.SearchScreen
import com.thedesitadka.app.ui.screens.SettingsScreen
import com.thedesitadka.app.ui.theme.TheDesiTadkaTheme
import com.thedesitadka.app.update.AppUpdateManager
import com.thedesitadka.app.update.UpdateInfo
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.VideoItem
import kotlinx.coroutines.launch

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
                    UpdateGateScreen(container)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// UPDATE GATE — Blocks ALL content until online verification passes.
// Three states: CHECKING → BLOCKED / PASSED
// No Later, Skip, Dismiss, offline bypass, or navigation around it.
// ──────────────────────────────────────────────────────────────────────

private enum class GateState {
    CHECKING,   // Startup verification in progress
    BLOCKED,    // Offline / update required / check failed
    PASSED      // Verified — allow normal app usage
}

@Composable
private fun UpdateGateScreen(container: AppContainer) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var gateState by remember { mutableStateOf(GateState.CHECKING) }
    var blockReason by remember { mutableStateOf("") }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Block back button when gate is not passed — prevent exiting gate
    BackHandler(enabled = gateState != GateState.PASSED) {
        // Intentionally empty — back button does nothing while blocked
    }

    // Run update check on startup and on retry
    LaunchedEffect(retryTrigger) {
        gateState = GateState.CHECKING
        blockReason = ""
        pendingUpdate = null
        downloadError = null

        // 1. Check network connectivity
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val isOnline = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (!isOnline) {
            blockReason = "TheDesiTadka requires an active internet connection to verify application integrity and official release status.\n\nPlease connect to the internet and tap Retry."
            gateState = GateState.BLOCKED
            return@LaunchedEffect
        }

        // 2. Layered Validation: Verify application identity and signing integrity
        val integrity = AndroidApkIntegrityChecker.checkAppIntegrity(context)
        if (!integrity.isTrusted) {
            blockReason = "Application integrity check failed: ${integrity.details}.\n\nOfficial verified release required. Tampered or repackaged installations are not supported."
            gateState = GateState.BLOCKED
            return@LaunchedEffect
        }

        // 3. Check for updates from official GitHub release
        val result = AppUpdateManager.checkForUpdates()
        result.onSuccess { info ->
            if (info.isUpdateAvailable) {
                // Mandatory update required — block until installed
                pendingUpdate = info
                blockReason = "A mandatory update (v${info.latestVersionName}) must be installed to continue using TheDesiTadka.\n\nYour current version (v${info.currentVersionName}) is no longer supported."
                gateState = GateState.BLOCKED
            } else {
                // 4. Validate configuration compatibility with this installed app version
                val configActivated = container.activateConfigurationIfValid()
                if (!configActivated) {
                    blockReason = "Configuration incompatible with application version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}).\n\nPlease update to the latest official release."
                    gateState = GateState.BLOCKED
                } else {
                    // Up to date & verified — allow normal app usage
                    gateState = GateState.PASSED
                }
            }
        }.onFailure { err ->
            blockReason = "Unable to reach the official release service.\n\n${err.message ?: "Connection refused"}\n\nOffline and unverified use is prohibited."
            gateState = GateState.BLOCKED
        }
    }

    when (gateState) {
        GateState.CHECKING -> {
            // Full-screen loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "TheDesiTadka",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Verifying application integrity…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        GateState.BLOCKED -> {
            // Full-screen blocking overlay — no content behind, no navigation possible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Icon: update available vs offline/error
                        Icon(
                            imageVector = if (pendingUpdate != null) Icons.Default.SystemUpdate else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (pendingUpdate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )

                        Text(
                            text = if (pendingUpdate != null) "Mandatory Update Required" else "Verification Failed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = blockReason,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        // Show release info if update is pending
                        pendingUpdate?.let { info ->
                            if (info.releaseNotes.isNotBlank()) {
                                Text(
                                    text = info.releaseNotes.take(200),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Download progress
                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Downloading & verifying integrity…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Download error
                        downloadError?.let { err ->
                            Text(
                                text = err,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action buttons — ONLY update or retry, NEVER "Later" or "Skip"
                        if (pendingUpdate != null && !isDownloading) {
                            Button(
                                onClick = {
                                    val info = pendingUpdate ?: return@Button
                                    coroutineScope.launch {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        downloadError = null
                                        val downloadResult = AppUpdateManager.downloadAndVerifyUpdate(
                                            context = context,
                                            updateInfo = info,
                                            onProgress = { p -> downloadProgress = p }
                                        )
                                        isDownloading = false
                                        downloadResult.onSuccess { apkFile ->
                                            AppUpdateManager.launchInstallIntent(context, apkFile)
                                        }.onFailure { err ->
                                            downloadError = "Verification failed: ${err.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Download & Install Update",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isDownloading) {
                            Button(
                                onClick = { retryTrigger++ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pendingUpdate != null)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else
                                        MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = if (pendingUpdate != null) Color.White else Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Retry Verification",
                                    color = if (pendingUpdate != null) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        GateState.PASSED -> {
            // Verification passed — render normal app content
            AppNavigationContent(container)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// MAIN APP NAVIGATION — Only rendered after update gate passes.
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun AppNavigationContent(container: AppContainer) {
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
