package com.kascorp.webhooknotesender.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kascorp.webhooknotesender.MainActivity
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.ui.audio.AudioRecordingScreen
import com.kascorp.webhooknotesender.ui.profiles.ProfileEditScreen
import com.kascorp.webhooknotesender.ui.profiles.ProfilesScreen
import com.kascorp.webhooknotesender.ui.profiles.ProfilesViewModel
import com.kascorp.webhooknotesender.ui.queue.QueueScreen
import com.kascorp.webhooknotesender.ui.queue.QueueViewModel
import com.kascorp.webhooknotesender.ui.settings.SettingsScreen
import com.kascorp.webhooknotesender.ui.settings.SettingsViewModel
import com.kascorp.webhooknotesender.ui.theme.WebhookNoteSenderTheme

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Profiles : Screen("profiles", R.string.nav_profiles, Icons.Outlined.Person, Icons.Filled.Person)
    data object Queue : Screen("queue", R.string.nav_queue, Icons.Outlined.Queue, Icons.Filled.Queue)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

sealed class DetailScreen(val route: String) {
    data object ProfileEdit : DetailScreen("profile_edit/{profileId}") {
        fun createRoute(profileId: Long = -1L) = "profile_edit/$profileId"
    }
    data object AudioRecording : DetailScreen(
        "audio_recording/{profileId}/{profileName}/{profilePrompt}/{profileUrl}/{bearerToken}?isFromShortcut={isFromShortcut}"
    ) {
        fun createRoute(
            profileId: Long,
            profileName: String,
            profilePrompt: String,
            profileUrl: String,
            bearerToken: String?,
            isFromShortcut: Boolean = false
        ): String {
            val encodedName = Uri.encode(profileName)
            val encodedPrompt = Uri.encode(profilePrompt)
            val encodedUrl = Uri.encode(profileUrl)
            val encodedToken = Uri.encode(bearerToken ?: "")
            return "audio_recording/$profileId/$encodedName/$encodedPrompt/$encodedUrl/$encodedToken?isFromShortcut=$isFromShortcut"
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    initialAudioRoute: String? = null,
    onNavigated: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val themeMode by settingsViewModel.themeMode.collectAsState()

    WebhookNoteSenderTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavigationContent(navController, settingsViewModel, initialAudioRoute, onNavigated)
        }
    }
}

@Composable
private fun AppNavigationContent(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    initialAudioRoute: String? = null,
    onNavigated: () -> Unit = {}
) {
    val screens = listOf(Screen.Profiles, Screen.Queue, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Get queue pending count for badge
    val queueViewModel: QueueViewModel = hiltViewModel()
    val queuePendingCount by queueViewModel.pendingCount.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions result handled; app will check per-permission on capture
    }

    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            val neededPermissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.CAMERA)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                neededPermissions.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (neededPermissions.isNotEmpty()) {
                permissionLauncher.launch(neededPermissions.toTypedArray())
            }
            permissionsRequested = true
        }
    }

    // Navigate to audio recording screen if launched from shortcut
    LaunchedEffect(initialAudioRoute) {
        if (initialAudioRoute != null) {
            navController.navigate(initialAudioRoute)
            onNavigated()
        }
    }

    Scaffold(
        bottomBar = {
            if (screens.any { it.route == currentRoute }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    screens.forEachIndexed { index, screen ->
                        val isSelected = currentRoute == screen.route
                        val title = stringResource(screen.titleRes)

                        // Animated values for icons
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1f,
                            animationSpec = tween(200),
                            label = "iconScale"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                            label = "iconColor"
                        )

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedTabIndex = index
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                NavIconWithBadge(
                                    screen = screen,
                                    isSelected = isSelected,
                                    iconScale = iconScale,
                                    iconColor = iconColor,
                                    badgeCount = if (screen is Screen.Queue) queuePendingCount else 0
                                )
                            },
                            label = {
                                Text(
                                    title,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = if (isSelected) 13.sp else 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
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
            startDestination = Screen.Profiles.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Profiles.route) {
                val viewModel: ProfilesViewModel = hiltViewModel()
                ProfilesScreen(
                    viewModel = viewModel,
                    onEditProfile = { profileId ->
                        navController.navigate(DetailScreen.ProfileEdit.createRoute(profileId))
                    },
                    onCreateProfile = {
                        navController.navigate(DetailScreen.ProfileEdit.createRoute())
                    },
                    onAudioCapture = { id, name, prompt, url, token ->
                        navController.navigate(
                            DetailScreen.AudioRecording.createRoute(
                                profileId = id,
                                profileName = name,
                                profilePrompt = prompt,
                                profileUrl = url,
                                bearerToken = token
                            )
                        )
                    }
                )
            }

            composable(Screen.Queue.route) {
                val viewModel: QueueViewModel = hiltViewModel()
                QueueScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = DetailScreen.ProfileEdit.route,
                arguments = listOf(
                    navArgument("profileId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getLong("profileId") ?: -1L
                val viewModel: ProfilesViewModel = hiltViewModel()
                ProfileEditScreen(
                    profileId = profileId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = DetailScreen.AudioRecording.route,
                arguments = listOf(
                    navArgument("profileId") { type = NavType.LongType },
                    navArgument("profileName") { type = NavType.StringType },
                    navArgument("profilePrompt") { type = NavType.StringType },
                    navArgument("profileUrl") { type = NavType.StringType },
                    navArgument("bearerToken") { type = NavType.StringType; defaultValue = "" },
                    navArgument("isFromShortcut") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments ?: return@composable
                val isFromShortcut = args.getBoolean("isFromShortcut")
                AudioRecordingScreen(
                    profileId = args.getLong("profileId"),
                    profileName = Uri.decode(args.getString("profileName") ?: ""),
                    profilePrompt = Uri.decode(args.getString("profilePrompt") ?: ""),
                    profileUrl = Uri.decode(args.getString("profileUrl") ?: ""),
                    bearerToken = Uri.decode(args.getString("bearerToken") ?: "").ifEmpty { null },
                    profileType = "audio",
                    onNavigateBack = {
                        if (isFromShortcut) {
                            // From shortcut: close the app without showing profiles
                            (context as? MainActivity)?.finishAffinity()
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavIconWithBadge(
    screen: Screen,
    isSelected: Boolean,
    iconScale: Float,
    iconColor: Color,
    badgeCount: Int
) {
    val icon = if (isSelected) screen.selectedIcon else screen.icon

    BadgedBox(
        badge = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(screen.titleRes),
            tint = iconColor,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale)
        )
    }
}
