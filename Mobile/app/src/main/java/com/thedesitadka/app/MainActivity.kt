package com.thedesitadka.app

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.thedesitadka.app.download.StoragePermissionHelper
import com.thedesitadka.app.download.NotificationPermissionHelper
import com.thedesitadka.app.storage.PreferenceStore
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.thedesitadka.app.ui.theme.LocalAppPalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.thedesitadka.app.ui.screens.CategoryContentScreen
import com.thedesitadka.app.ui.screens.CategoryListScreen
import com.thedesitadka.app.ui.screens.DetailsScreen
import com.thedesitadka.app.ui.screens.DiagnosticsScreen
import com.thedesitadka.app.ui.screens.DownloadsScreen
import com.thedesitadka.app.ui.screens.HomeScreen
import com.thedesitadka.app.ui.screens.HomeSourceSelectionScreen
import com.thedesitadka.app.ui.screens.PlayerScreen
import com.thedesitadka.app.ui.screens.PrmoviesFilterScreen
import com.thedesitadka.app.ui.screens.ProviderScreen
import com.thedesitadka.app.ui.screens.SearchScreen
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.app.ui.screens.SettingsScreen
import com.thedesitadka.app.ui.theme.TheDesiTadkaTheme
import com.thedesitadka.app.update.AppUpdateManager
import com.thedesitadka.app.update.UpdateInfo
import com.thedesitadka.app.navigation.PlaylistNavigationContext
import com.thedesitadka.app.navigation.PlaylistSourceType
import com.thedesitadka.app.navigation.VideoPlaylistHelper
import com.thedesitadka.app.storage.NavigationStateStore
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
            val colorPalette by container.preferenceStore.colorPaletteFlow.collectAsState(initial = "sunset_amber")
            TheDesiTadkaTheme(darkTheme = true, paletteId = colorPalette) {
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
    var installMessage by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<java.io.File?>(null) }
    var hasInstallPermission by remember { mutableStateOf(AppUpdateManager.canRequestPackageInstalls(context)) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val activity = context as? ComponentActivity
    androidx.compose.runtime.DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasInstallPermission = AppUpdateManager.canRequestPackageInstalls(context)
                pendingUpdate?.let { info ->
                    downloadedApkFile = AppUpdateManager.getDownloadedUpdateFile(context, info.latestVersionName)
                }
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

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
        if (BuildConfig.DEBUG) {
            StreamHubLogger.i("MainActivity", "Debug build (v${BuildConfig.VERSION_NAME}) — bypassing update gate for testing.")
            val configActivated = container.activateConfigurationIfValid()
            if (!configActivated) {
                blockReason = "Configuration incompatible with application version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}).\n\nPlease update to the latest official release."
                gateState = GateState.BLOCKED
            } else {
                gateState = GateState.PASSED
            }
            return@LaunchedEffect
        }

        val result = AppUpdateManager.checkForUpdates()
        result.onSuccess { info ->
            if (info.isUpdateAvailable) {
                // Mandatory update required — block until installed
                pendingUpdate = info
                downloadedApkFile = AppUpdateManager.getDownloadedUpdateFile(context, info.latestVersionName)
                hasInstallPermission = AppUpdateManager.canRequestPackageInstalls(context)
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

                    // Background auto-sync of newer catalog configurations (safe and non-blocking)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val success = container.configRepository.syncRemoteConfig(PreferenceStore.DEFAULT_CONFIG_URL)
                            if (success) {
                                val updated = container.configRepository.manifestFlow.value
                                container.providerEngine.updateFromManifest(updated)
                                StreamHubLogger.i("MainActivity", "Catalog automatically synchronized to v${updated.configVersion} (${updated.providers.size} providers)")
                            }
                        } catch (e: Exception) {
                            StreamHubLogger.w("MainActivity", "Background auto-sync note: ${e.message}")
                        }
                    }
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

                        // Installation status message
                        installMessage?.let { msg ->
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
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
                            val info = pendingUpdate!!
                            val isReadyToInstall = downloadedApkFile != null && downloadedApkFile!!.exists()

                            if (isReadyToInstall && !hasInstallPermission) {
                                Button(
                                    onClick = {
                                        AppUpdateManager.openUnknownAppSourcesSettings(context)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Grant Permission to Install",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Tap above to allow app installs from this source, then return here to install.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            } else if (isReadyToInstall) {
                                Button(
                                    onClick = {
                                        downloadError = null
                                        val launched = AppUpdateManager.launchInstallIntent(context, downloadedApkFile!!)
                                        if (launched) {
                                            installMessage = "Package installer launched. Please follow on-screen prompts to complete installation."
                                        } else {
                                            downloadError = "Failed to launch package installer. Please check app permissions."
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
                                        "Install Update Package",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isDownloading = true
                                            downloadProgress = 0f
                                            downloadError = null
                                            installMessage = null
                                            val downloadResult = AppUpdateManager.downloadAndVerifyUpdate(
                                                context = context,
                                                updateInfo = info,
                                                onProgress = { p -> downloadProgress = p }
                                            )
                                            isDownloading = false
                                            downloadResult.onSuccess { apkFile ->
                                                downloadedApkFile = apkFile
                                                hasInstallPermission = AppUpdateManager.canRequestPackageInstalls(context)
                                                if (hasInstallPermission) {
                                                    val launched = AppUpdateManager.launchInstallIntent(context, apkFile)
                                                    if (launched) {
                                                        installMessage = "Package installer launched. Please follow on-screen prompts to complete installation."
                                                    } else {
                                                        downloadError = "Failed to launch package installer. Tap 'Install Update Package' below to retry."
                                                    }
                                                } else {
                                                    AppUpdateManager.openUnknownAppSourcesSettings(context)
                                                }
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
    val isHomeSelectionCompleted by container.preferenceStore.homeSelectionCompletedFlow.collectAsState(initial = null)

    if (isHomeSelectionCompleted == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (isHomeSelectionCompleted == false) {
        HomeSourceSelectionScreen(
            providerEngine = container.providerEngine,
            preferenceStore = container.preferenceStore,
            onCompleted = {
                container.dashboardRepository.clearCacheAndReload()
            }
        )
        return
    }

    val isInitialStoragePromptDone by container.preferenceStore.initialStoragePromptDoneFlow.collectAsState(initial = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showInitialStorageDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        StreamHubLogger.i("MainActivity", "Notification permission result: $isGranted")
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Returned from storage settings
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Returned from runtime permission dialog
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationPermissionHelper.hasNotificationPermission(context)
        ) {
            showNotificationDialog = true
        }
    }

    LaunchedEffect(isInitialStoragePromptDone) {
        if (!isInitialStoragePromptDone && !StoragePermissionHelper.hasFullStorageAccess(context)) {
            showInitialStorageDialog = true
        }
    }

    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = {
                showNotificationDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Notifications", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(
                    "To show active download progress, speed, and completion status directly in your notification bar and status bar, TheDesiTadka requires notification permission.\n\nPlease enable notifications so you can monitor your downloads in real time.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Allow Notifications", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationDialog = false
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showInitialStorageDialog) {
        AlertDialog(
            onDismissRequest = {
                showInitialStorageDialog = false
                coroutineScope.launch { container.preferenceStore.setInitialStoragePromptDone(true) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Storage Permission", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(
                    "TheDesiTadka requires storage access to download and save media directly to your device storage for offline playback.\n\nWould you like to grant permission now?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInitialStorageDialog = false
                        coroutineScope.launch { container.preferenceStore.setInitialStoragePromptDone(true) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            storagePermissionLauncher.launch(StoragePermissionHelper.createFullStorageAccessIntent(context))
                        } else {
                            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Grant Access", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInitialStorageDialog = false
                        coroutineScope.launch { container.preferenceStore.setInitialStoragePromptDone(true) }
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    val activeNotice by container.adminNoticeRepository.activeNoticeFlow.collectAsState()

    LaunchedEffect(Unit) {
        container.adminNoticeRepository.checkNotice()
    }

    if (activeNotice != null) {
        val notice = activeNotice!!
        AlertDialog(
            onDismissRequest = {
                if (notice.dismissible) {
                    coroutineScope.launch {
                        container.adminNoticeRepository.dismissNotice(notice)
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notice.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column {
                    val noticeDate = notice.date
                    if (!noticeDate.isNullOrBlank()) {
                        Text(
                            text = noticeDate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = notice.message,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                if (!notice.actionUrl.isNullOrBlank()) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(notice.actionUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = notice.actionText ?: "View Details",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                if (notice.dismissible) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                container.adminNoticeRepository.dismissNotice(notice)
                            }
                        }
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var activeVideoItem by remember { mutableStateOf<VideoItem?>(null) }
    var activeMediaSource by remember { mutableStateOf<MediaSource?>(null) }
    var activePlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var activePlaylistIndex by remember { mutableIntStateOf(0) }

    var playlistSourceType by remember { mutableStateOf(PlaylistSourceType.DASHBOARD) }
    var playlistProviderId by remember { mutableStateOf("") }
    var playlistCategoryUrl by remember { mutableStateOf("") }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var playlistCurrentPage by remember { mutableIntStateOf(1) }
    var playlistHasNextPage by remember { mutableStateOf(false) }
    var isFetchingNextPage by remember { mutableStateOf(false) }
    var isSwitchingVideo by remember { mutableStateOf(false) }

    val playVideoItemInternal: (VideoItem) -> Unit = { targetVid ->
        isSwitchingVideo = true
        coroutineScope.launch {
            try {
                val directStream = if (targetVid.detailUrl.contains(".mp4") || targetVid.detailUrl.contains(".m3u8")) {
                    MediaSource(
                        url = targetVid.detailUrl,
                        type = if (targetVid.detailUrl.contains(".m3u8")) com.thedesitadka.core.model.MediaSourceType.HLS else com.thedesitadka.core.model.MediaSourceType.PROGRESSIVE_MP4,
                        canPlay = true
                    )
                } else null

                val sourceToPlay = directStream ?: run {
                    val adapter = container.providerEngine.getAdapter(targetVid.providerId)
                    val result = adapter?.getPlayableMedia(targetVid.detailUrl)
                    if (result != null && result.isSuccess && result.getOrThrow().isNotEmpty()) {
                        result.getOrThrow().first()
                    } else {
                        val detResult = adapter?.getDetails(targetVid.detailUrl)
                        if (detResult != null && detResult.isSuccess) {
                            val det = detResult.getOrThrow()
                            val epUrl = det.seasons.firstOrNull()?.episodes?.firstOrNull()?.let { it.streamUrl.ifBlank { it.detailUrl } }
                            if (!epUrl.isNullOrBlank()) {
                                adapter.getPlayableMedia(epUrl).getOrNull()?.firstOrNull()
                            } else null
                        } else null
                    }
                }

                if (sourceToPlay != null) {
                    activeVideoItem = targetVid
                    activeMediaSource = sourceToPlay
                } else {
                    android.widget.Toast.makeText(context, "Could not load video stream: ${targetVid.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSwitchingVideo = false
            }
        }
    }

    val handleNextVideoNavigation: () -> Unit = {
        if (isSwitchingVideo || isFetchingNextPage) {
            // Loading in progress
        } else if (activePlaylistIndex < activePlaylist.size - 1) {
            val nextIndex = activePlaylistIndex + 1
            val nextVid = activePlaylist[nextIndex]
            activePlaylistIndex = nextIndex
            playVideoItemInternal(nextVid)
        } else if (playlistHasNextPage && !isFetchingNextPage) {
            isFetchingNextPage = true
            android.widget.Toast.makeText(context, "Loading next page...", android.widget.Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                try {
                    val nextPage = playlistCurrentPage + 1
                    val result: Result<com.thedesitadka.core.model.FeedPage>? = when (playlistSourceType) {
                        PlaylistSourceType.CATEGORY -> {
                            container.providerEngine.getCategoryFeed(playlistProviderId, playlistCategoryUrl, nextPage)
                        }
                        PlaylistSourceType.PROVIDER_FEED -> {
                            if (playlistSearchQuery.isNotBlank()) {
                                val adapter = container.providerEngine.getAdapter(playlistProviderId)
                                adapter?.search(playlistSearchQuery.trim(), nextPage) ?: container.providerEngine.search(playlistSearchQuery.trim(), playlistProviderId, nextPage)
                            } else if (playlistCategoryUrl.isNotBlank()) {
                                container.providerEngine.getCategoryFeed(playlistProviderId, playlistCategoryUrl, nextPage)
                            } else {
                                container.providerEngine.getHomeFeed(playlistProviderId, nextPage)
                            }
                        }
                        PlaylistSourceType.SEARCH -> {
                            container.providerEngine.search(playlistSearchQuery.trim(), playlistProviderId.ifBlank { null }, nextPage)
                        }
                        else -> null
                    }

                    if (result != null && result.isSuccess) {
                        val feedPage = result.getOrThrow()
                        val validItems = feedPage.items.filter { it.title.isNotBlank() && it.detailUrl.isNotBlank() }
                        val existingUrls = activePlaylist.map { VideoPlaylistHelper.normalizeUrl(it.detailUrl) }.toSet()
                        val newUniqueItems = validItems.filter { VideoPlaylistHelper.normalizeUrl(it.detailUrl) !in existingUrls }

                        if (newUniqueItems.isNotEmpty()) {
                            val updatedList = activePlaylist + newUniqueItems
                            activePlaylist = updatedList
                            playlistCurrentPage = nextPage
                            playlistHasNextPage = feedPage.hasNextPage && newUniqueItems.isNotEmpty()

                            if (playlistSourceType == PlaylistSourceType.CATEGORY) {
                                val cacheKey = "cat_content:$playlistProviderId:$playlistCategoryUrl"
                                NavigationStateStore.updateItems(cacheKey, updatedList, nextPage, playlistHasNextPage)
                            } else if (playlistSourceType == PlaylistSourceType.PROVIDER_FEED) {
                                val cacheKey = "provider:$playlistProviderId"
                                NavigationStateStore.updateItems(cacheKey, updatedList, nextPage, playlistHasNextPage)
                            }

                            val nextIndex = activePlaylistIndex + 1
                            val nextVid = updatedList[nextIndex]
                            activePlaylistIndex = nextIndex
                            playVideoItemInternal(nextVid)
                        } else {
                            playlistHasNextPage = false
                            android.widget.Toast.makeText(context, "No more videos left", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        playlistHasNextPage = false
                        android.widget.Toast.makeText(context, "No more videos left", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    playlistHasNextPage = false
                    android.widget.Toast.makeText(context, "Failed to load next page: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isFetchingNextPage = false
                }
            }
        } else {
            android.widget.Toast.makeText(context, "No next video in playlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val handlePreviousVideoNavigation: () -> Unit = {
        if (isSwitchingVideo || isFetchingNextPage) {
            // Loading in progress
        } else if (activePlaylistIndex > 0) {
            val prevIndex = activePlaylistIndex - 1
            val prevVid = activePlaylist[prevIndex]
            activePlaylistIndex = prevIndex
            playVideoItemInternal(prevVid)
        } else {
            android.widget.Toast.makeText(context, "No previous video in playlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val setPlaylistAndNavigate: (VideoItem, List<VideoItem>, PlaylistNavigationContext?) -> Unit = { video, playlist, navCtx ->
        if (video.isCategory || isCategoryUrl(video.detailUrl)) {
            if (video.detailUrl.endsWith("/categories/") || video.detailUrl.endsWith("/categories") || video.title.equals("All Categories", ignoreCase = true)) {
                navController.navigate(Screen.CategoryList.createRoute(video.providerId))
            } else {
                navController.navigate(Screen.CategoryContent.createRoute(video.providerId, video.title, video.detailUrl))
            }
        } else {
            activeVideoItem = video
            activePlaylist = playlist
            val idx = VideoPlaylistHelper.findVideoIndex(playlist, video)
            activePlaylistIndex = if (idx >= 0) idx else 0
            if (navCtx != null) {
                playlistSourceType = navCtx.sourceType
                playlistProviderId = navCtx.providerId
                playlistCategoryUrl = navCtx.categoryUrl
                playlistSearchQuery = navCtx.searchQuery
                playlistCurrentPage = navCtx.currentPage
                playlistHasNextPage = navCtx.hasNextPage
            } else {
                playlistSourceType = PlaylistSourceType.DASHBOARD
                playlistProviderId = video.providerId
                playlistCategoryUrl = ""
                playlistSearchQuery = ""
                playlistCurrentPage = 1
                playlistHasNextPage = false
            }
            navController.navigate(Screen.Details.route)
        }
    }

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
                val navItems = listOf(
                    Triple(Screen.Home.route, "Home", Icons.Default.Home),
                    Triple(Screen.Search.route, "Search", Icons.Default.Search),
                    Triple(Screen.Downloads.route, "Downloads", Icons.Default.Download),
                    Triple(Screen.Settings.route, "Settings", Icons.Default.Settings)
                )
                val palette = LocalAppPalette.current
                // Modern 3D Glassmorphic Floating Dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = palette.glowColor,
                                spotColor = palette.primary
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .background(palette.glassSurface)
                            .border(
                                border = BorderStroke(1.2.dp, palette.glassBorder),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEach { (route, label, icon) ->
                            val selected = currentRoute == route
                            val scale by animateFloatAsState(
                                targetValue = if (selected) 1.06f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "nav_scale_$route"
                            )
                            val iconAlpha by animateFloatAsState(
                                targetValue = if (selected) 1.0f else 0.70f,
                                label = "nav_alpha_$route"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(scale)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) palette.gradientBrush
                                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                        )
                                        .padding(horizontal = 16.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (selected) palette.onPrimary else Color(0xFF94A3B8).copy(alpha = iconAlpha),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (selected) palette.primary else Color(0xFF94A3B8).copy(alpha = iconAlpha)
                                )
                            }
                        }
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
                    preferenceStore = container.preferenceStore,
                    monetizationManager = container.monetizationManager,
                    onVideoClick = { video ->
                        setPlaylistAndNavigate(video, if (activePlaylist.isNotEmpty()) activePlaylist else listOf(video), null)
                    },
                    onVideoClickWithPlaylist = { video, playlist, navCtx ->
                        setPlaylistAndNavigate(video, playlist, navCtx)
                    },
                    onProviderClick = { providerId ->
                        navController.navigate(Screen.Provider.createRoute(providerId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onCustomizeSourcesClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Provider.route) { backStackEntry ->
                val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
                ProviderScreen(
                    providerId = providerId,
                    providerEngine = container.providerEngine,
                    onVideoClick = { video ->
                        setPlaylistAndNavigate(video, if (activePlaylist.isNotEmpty()) activePlaylist else listOf(video), null)
                    },
                    onVideoClickWithPlaylist = { video, playlist, navCtx ->
                        setPlaylistAndNavigate(video, playlist, navCtx)
                    },
                    onAllCategoriesClick = {
                        try {
                            navController.navigate(Screen.CategoryList.createRoute(providerId))
                        } catch (e: Throwable) {
                            android.widget.Toast.makeText(context, "No categories found", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCategoryItemClick = { title, url ->
                        try {
                            navController.navigate(Screen.CategoryContent.createRoute(providerId, title, url))
                        } catch (e: Throwable) {
                            android.widget.Toast.makeText(context, "Unable to open category", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFilterClick = {
                        navController.navigate(Screen.Filter.createRoute(providerId))
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Filter.route) { backStackEntry ->
                val providerId = backStackEntry.arguments?.getString("providerId") ?: "prmovies_church"
                PrmoviesFilterScreen(
                    providerId = providerId,
                    providerEngine = container.providerEngine,
                    onApplyFilter = { title, url ->
                        try {
                            navController.navigate(Screen.CategoryContent.createRoute(providerId, title, url))
                        } catch (e: Throwable) {
                            android.widget.Toast.makeText(context, "Unable to open filtered content", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CategoryList.route,
                arguments = listOf(
                    androidx.navigation.navArgument("providerId") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val rawId = backStackEntry.arguments?.getString("providerId") ?: ""
                val providerId = try { java.net.URLDecoder.decode(rawId, "UTF-8") } catch (_: Exception) { rawId }
                CategoryListScreen(
                    providerId = providerId,
                    providerEngine = container.providerEngine,
                    onCategoryClick = { title, url ->
                        try {
                            navController.navigate(Screen.CategoryContent.createRoute(providerId, title, url))
                        } catch (e: Throwable) {
                            android.widget.Toast.makeText(context, "Unable to open category", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CategoryContent.route,
                arguments = listOf(
                    androidx.navigation.navArgument("providerId") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    },
                    androidx.navigation.navArgument("title") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = "Category"
                    },
                    androidx.navigation.navArgument("url") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val rawId = backStackEntry.arguments?.getString("providerId") ?: ""
                val providerId = try { java.net.URLDecoder.decode(rawId, "UTF-8") } catch (_: Exception) { rawId }
                val rawTitle = backStackEntry.arguments?.getString("title") ?: "Category"
                val categoryTitle = try {
                    java.net.URLDecoder.decode(rawTitle, "UTF-8")
                } catch (_: Exception) {
                    rawTitle
                }
                val rawUrl = backStackEntry.arguments?.getString("url") ?: ""
                val categoryUrl = try {
                    java.net.URLDecoder.decode(rawUrl, "UTF-8")
                } catch (_: Exception) {
                    rawUrl
                }
                CategoryContentScreen(
                    providerId = providerId,
                    categoryTitle = categoryTitle,
                    categoryUrl = categoryUrl,
                    providerEngine = container.providerEngine,
                    onVideoClick = { video ->
                        setPlaylistAndNavigate(video, if (activePlaylist.isNotEmpty()) activePlaylist else listOf(video), null)
                    },
                    onVideoClickWithPlaylist = { video, playlist, navCtx ->
                        setPlaylistAndNavigate(video, playlist, navCtx)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }


            composable(Screen.Search.route) {
                SearchScreen(
                    providerEngine = container.providerEngine,
                    onVideoClick = { video ->
                        setPlaylistAndNavigate(video, if (activePlaylist.isNotEmpty()) activePlaylist else listOf(video), null)
                    },
                    onVideoClickWithPlaylist = { video, playlist, navCtx ->
                        setPlaylistAndNavigate(video, playlist, navCtx)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Details.route) {
                activeVideoItem?.let { video ->
                    key(video.id, video.detailUrl) {
                        DetailsScreen(
                            videoItem = video,
                            providerEngine = container.providerEngine,
                            favoriteDao = container.database.favoriteDao(),
                            downloadRepository = container.downloadRepository,
                            monetizationManager = container.monetizationManager,
                            onOpenDownloadsClick = {
                                navController.navigate(Screen.Downloads.route)
                            },
                            onNextVideoClick = { currentVid ->
                                if (activePlaylistIndex < activePlaylist.size - 1) {
                                    activePlaylistIndex++
                                    val nextVid = activePlaylist[activePlaylistIndex]
                                    activeVideoItem = nextVid
                                    android.widget.Toast.makeText(context, "Next: ${nextVid.title}", android.widget.Toast.LENGTH_SHORT).show()
                                    true
                                } else if (playlistHasNextPage && !isFetchingNextPage) {
                                    isFetchingNextPage = true
                                    android.widget.Toast.makeText(context, "Loading next page...", android.widget.Toast.LENGTH_SHORT).show()
                                    coroutineScope.launch {
                                        try {
                                            val nextPage = playlistCurrentPage + 1
                                            val result = when (playlistSourceType) {
                                                PlaylistSourceType.CATEGORY -> container.providerEngine.getCategoryFeed(playlistProviderId, playlistCategoryUrl, nextPage)
                                                PlaylistSourceType.PROVIDER_FEED -> {
                                                    if (playlistSearchQuery.isNotBlank()) {
                                                        container.providerEngine.search(playlistSearchQuery.trim(), playlistProviderId, nextPage)
                                                    } else if (playlistCategoryUrl.isNotBlank()) {
                                                        container.providerEngine.getCategoryFeed(playlistProviderId, playlistCategoryUrl, nextPage)
                                                    } else {
                                                        container.providerEngine.getHomeFeed(playlistProviderId, nextPage)
                                                    }
                                                }
                                                else -> null
                                            }
                                            if (result != null && result.isSuccess) {
                                                val feedPage = result.getOrThrow()
                                                val validItems = feedPage.items.filter { it.title.isNotBlank() && it.detailUrl.isNotBlank() }
                                                val existingUrls = activePlaylist.map { VideoPlaylistHelper.normalizeUrl(it.detailUrl) }.toSet()
                                                val newUniqueItems = validItems.filter { VideoPlaylistHelper.normalizeUrl(it.detailUrl) !in existingUrls }
                                                if (newUniqueItems.isNotEmpty()) {
                                                    val updatedList = activePlaylist + newUniqueItems
                                                    activePlaylist = updatedList
                                                    playlistCurrentPage = nextPage
                                                    playlistHasNextPage = feedPage.hasNextPage && newUniqueItems.isNotEmpty()

                                                    if (playlistSourceType == PlaylistSourceType.CATEGORY) {
                                                        val cacheKey = "cat_content:$playlistProviderId:$playlistCategoryUrl"
                                                        NavigationStateStore.updateItems(cacheKey, updatedList, nextPage, playlistHasNextPage)
                                                    } else if (playlistSourceType == PlaylistSourceType.PROVIDER_FEED) {
                                                        val cacheKey = "provider:$playlistProviderId"
                                                        NavigationStateStore.updateItems(cacheKey, updatedList, nextPage, playlistHasNextPage)
                                                    }

                                                    val nextIndex = activePlaylistIndex + 1
                                                    activePlaylistIndex = nextIndex
                                                    activeVideoItem = updatedList[nextIndex]
                                                    android.widget.Toast.makeText(context, "Next: ${updatedList[nextIndex].title}", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    playlistHasNextPage = false
                                                    android.widget.Toast.makeText(context, "No more videos left", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                playlistHasNextPage = false
                                                android.widget.Toast.makeText(context, "No more videos left", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            playlistHasNextPage = false
                                            android.widget.Toast.makeText(context, "Failed to load next page: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isFetchingNextPage = false
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                            onPreviousVideoClick = { currentVid ->
                                if (activePlaylistIndex > 0) {
                                    activePlaylistIndex--
                                    val prevVid = activePlaylist[activePlaylistIndex]
                                    activeVideoItem = prevVid
                                    android.widget.Toast.makeText(context, "Previous: ${prevVid.title}", android.widget.Toast.LENGTH_SHORT).show()
                                    true
                                } else {
                                    false
                                }
                            },
                            onPlaylistUpdated = { playlist ->
                                if (activePlaylist.size <= 1 && playlistSourceType != PlaylistSourceType.CATEGORY && playlistSourceType != PlaylistSourceType.PROVIDER_FEED) {
                                    activePlaylist = playlist
                                    val idx = VideoPlaylistHelper.findVideoIndex(playlist, video)
                                    activePlaylistIndex = if (idx >= 0) idx else 0
                                    playlistSourceType = PlaylistSourceType.RELATED
                                }
                            },
                            onPlayClick = { vid, src ->
                                activeVideoItem = vid
                                activeMediaSource = src
                                val existingIndex = VideoPlaylistHelper.findVideoIndex(activePlaylist, vid)
                                if (existingIndex >= 0) {
                                    activePlaylistIndex = existingIndex
                                } else if (activePlaylist.size > 1) {
                                    val fallbackIdx = activeVideoItem?.let { VideoPlaylistHelper.findVideoIndex(activePlaylist, it) } ?: -1
                                    activePlaylistIndex = if (fallbackIdx >= 0) fallbackIdx else 0
                                } else {
                                    activePlaylist = listOf(vid)
                                    activePlaylistIndex = 0
                                }
                                navController.navigate(Screen.Player.route)
                            },
                            onPlayClickWithPlaylist = { vid, src, playlist ->
                                activeVideoItem = vid
                                activeMediaSource = src
                                if (playlist.size > 1) {
                                    activePlaylist = playlist
                                    val idx = VideoPlaylistHelper.findVideoIndex(playlist, vid)
                                    activePlaylistIndex = if (idx >= 0) idx else 0
                                    playlistSourceType = PlaylistSourceType.SERIES
                                    playlistHasNextPage = false
                                } else {
                                    val existingIndex = VideoPlaylistHelper.findVideoIndex(activePlaylist, vid)
                                    if (existingIndex >= 0) {
                                        activePlaylistIndex = existingIndex
                                    } else if (activePlaylist.size > 1) {
                                        val fallbackIdx = activeVideoItem?.let { VideoPlaylistHelper.findVideoIndex(activePlaylist, it) } ?: -1
                                        if (fallbackIdx >= 0) {
                                            activePlaylistIndex = fallbackIdx
                                        } else {
                                            activePlaylistIndex = 0
                                        }
                                    } else if (playlist.isNotEmpty()) {
                                        activePlaylist = playlist
                                        activePlaylistIndex = 0
                                    } else {
                                        activePlaylist = listOf(vid)
                                        activePlaylistIndex = 0
                                    }
                                }
                                navController.navigate(Screen.Player.route)
                            },
                            onRelatedClick = { relVideo ->
                                activeVideoItem = relVideo
                                val existingIdx = VideoPlaylistHelper.findVideoIndex(activePlaylist, relVideo)
                                if (existingIdx >= 0) {
                                    activePlaylistIndex = existingIdx
                                } else {
                                    activePlaylist = activePlaylist + relVideo
                                    activePlaylistIndex = activePlaylist.size - 1
                                }
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }

            composable(Screen.Player.route) {
                if (activeVideoItem != null && activeMediaSource != null) {
                    key(activeVideoItem!!.id, activeMediaSource!!.url) {
                        val canGoNext = (activePlaylistIndex < activePlaylist.size - 1) || (playlistHasNextPage && !isFetchingNextPage)
                        val canGoPrevious = activePlaylistIndex > 0
                        PlayerScreen(
                            videoItem = activeVideoItem!!,
                            mediaSource = activeMediaSource!!,
                            watchHistoryDao = container.database.watchHistoryDao(),
                            monetizationManager = container.monetizationManager,
                            hasNext = canGoNext,
                            hasPrevious = canGoPrevious,
                            onNextClick = if (canGoNext) handleNextVideoNavigation else null,
                            onPreviousClick = if (canGoPrevious) handlePreviousVideoNavigation else null,
                            onOpenDownloadsClick = {
                                navController.navigate(Screen.Downloads.route)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
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
                    dashboardRepository = container.dashboardRepository,
                    monetizationManager = container.monetizationManager,
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
