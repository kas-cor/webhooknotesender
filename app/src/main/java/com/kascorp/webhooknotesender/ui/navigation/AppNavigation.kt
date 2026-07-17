package com.kascorp.webhooknotesender.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kascorp.webhooknotesender.ui.profiles.ProfileEditScreen
import com.kascorp.webhooknotesender.ui.profiles.ProfilesScreen
import com.kascorp.webhooknotesender.ui.profiles.ProfilesViewModel
import com.kascorp.webhooknotesender.ui.queue.QueueScreen
import com.kascorp.webhooknotesender.ui.queue.QueueViewModel
import com.kascorp.webhooknotesender.ui.settings.SettingsScreen
import com.kascorp.webhooknotesender.ui.settings.SettingsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Profiles : Screen("profiles", "Profiles", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt)
    data object Queue : Screen("queue", "Queue", Icons.Outlined.Queue, Icons.Filled.Queue)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

sealed class DetailScreen(val route: String) {
    data object ProfileEdit : DetailScreen("profile_edit/{profileId}") {
        fun createRoute(profileId: Long = -1L) = "profile_edit/$profileId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val screens = listOf(Screen.Profiles, Screen.Queue, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            if (screens.any { it.route == currentRoute }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    screens.forEachIndexed { index, screen ->
                        val isSelected = currentRoute == screen.route

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
                                if (screen is Screen.Queue) {
                                    QueueIconWithBadge()
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) screen.selectedIcon else screen.icon,
                                        contentDescription = screen.title
                                    )
                                }
                            },
                            label = { Text(screen.title) },
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
                    }
                )
            }

            composable(Screen.Queue.route) {
                val viewModel: QueueViewModel = hiltViewModel()
                QueueScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = viewModel)
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
        }
    }
}

@Composable
private fun QueueIconWithBadge() {
    // Badge handled by the QueueScreen itself
    Icon(
        imageVector = Icons.Outlined.Queue,
        contentDescription = "Queue"
    )
}
