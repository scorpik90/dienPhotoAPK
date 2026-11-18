package ru.delivery.photo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ru.delivery.photo.domain.ThemeMode
import ru.delivery.photo.ui.capture.CaptureScreen
import ru.delivery.photo.ui.common.rememberShareCoordinator
import ru.delivery.photo.ui.history.HistoryScreen
import ru.delivery.photo.ui.photos.PhotosScreen
import ru.delivery.photo.ui.theme.DeliveryPhotoTheme

object Routes {
    const val CAPTURE = "capture"
    const val PHOTOS = "photos"
    const val HISTORY = "history"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    Tab(Routes.CAPTURE, "Съёмка", Icons.Filled.CameraAlt),
    Tab(Routes.PHOTOS, "Фото", Icons.Filled.PhotoLibrary),
    Tab(Routes.HISTORY, "История", Icons.Filled.History),
)

@Composable
fun AppRoot(
    vm: AppViewModel = viewModel(),
    initialRoute: String = Routes.CAPTURE,
) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    DeliveryPhotoTheme(darkTheme = darkTheme) {
        AppRootContent(vm = vm, initialRoute = initialRoute)
    }
}

@Composable
private fun AppRootContent(
    vm: AppViewModel,
    initialRoute: String,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val share = rememberShareCoordinator(vm)

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    LaunchedEffect(initialRoute) {
        if (initialRoute != Routes.CAPTURE) {
            navController.navigate(initialRoute) { launchSingleTop = true }
        }
    }

    val currentRoute by navController.currentBackStackEntryFlow
        .collectAsStateWithLifecycle(initialValue = Routes.CAPTURE)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAPTURE,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    vm = vm,
                    share = share,
                    onOpenPhotos = { navController.navigate(Routes.PHOTOS) },
                )
            }
            composable(Routes.PHOTOS) { PhotosScreen(vm = vm) }
            composable(Routes.HISTORY) { HistoryScreen(vm = vm) }
        }
    }
}
