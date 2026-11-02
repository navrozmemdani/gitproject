package com.visualtrace.ai.features.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.visualtrace.ai.core.designsystem.VisualTraceTheme
import com.visualtrace.ai.core.ui.FullScreenCentered
import com.visualtrace.ai.core.ui.OfflineBanner
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.SessionState
import com.visualtrace.ai.domain.repository.AuthRepository
import com.visualtrace.ai.domain.repository.ConnectivityMonitor
import com.visualtrace.ai.domain.repository.SettingsRepository
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.auth.AuthScreen
import com.visualtrace.ai.features.common.AppInfo
import com.visualtrace.ai.features.favorites.FavoritesScreen
import com.visualtrace.ai.features.history.HistoryScreen
import com.visualtrace.ai.features.home.HomeScreen
import com.visualtrace.ai.features.legal.LegalScreen
import com.visualtrace.ai.features.profile.ProfileScreen
import com.visualtrace.ai.features.results.ResultsScreen
import com.visualtrace.ai.features.settings.ProviderSettingsScreen
import com.visualtrace.ai.features.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class AppRootViewModel @Inject constructor(
    auth: AuthRepository,
    settings: SettingsRepository,
    connectivity: ConnectivityMonitor,
) : ViewModel() {
    val session: StateFlow<SessionState> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)
    val settings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val online: StateFlow<Boolean> = connectivity.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)
}

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TopLevel(HistoryRoute, HistoryRoute::class, R.string.nav_history, Icons.Outlined.History, Icons.Filled.History),
    TopLevel(SavedRoute, SavedRoute::class, R.string.nav_saved, Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    TopLevel(ProfileRoute, ProfileRoute::class, R.string.nav_profile, Icons.Outlined.Person, Icons.Filled.Person),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppRoot(appInfo: AppInfo, viewModel: AppRootViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()

    VisualTraceTheme(themeMode = s.theme, dynamicColor = s.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (session) {
                SessionState.Loading -> FullScreenCentered { CircularProgressIndicator() }
                SessionState.SignedOut -> AuthScreen(hiltViewModel(), appInfo.googleWebClientId)
                else -> MainNavigation(appInfo, online)
            }
        }
    }
}

@Composable
private fun MainNavigation(appInfo: AppInfo, online: Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = TOP_LEVEL.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    fun navigateTopLevel(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(showBar, enter = fadeIn(), exit = fadeOut()) {
                NavigationBar {
                    TOP_LEVEL.forEach { t ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(t.route) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(!online) { OfflineBanner(stringResource(R.string.offline_banner)) }
            NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.weight(1f)) {
                composable<HomeRoute> {
                    HomeScreen(
                        viewModel = hiltViewModel(),
                        onOpenResults = { nav.navigate(ResultsRoute(ticket = it)) },
                        onOpenSearch = { nav.navigate(ResultsRoute(searchId = it)) },
                        onOpenHistory = { navigateTopLevel(HistoryRoute) },
                        onOpenSaved = { navigateTopLevel(SavedRoute) },
                        onOpenProviders = { nav.navigate(ProviderSettingsRoute) },
                    )
                }
                composable<ResultsRoute> {
                    ResultsScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onOpenTicket = { nav.navigate(ResultsRoute(ticket = it)) },
                    )
                }
                composable<HistoryRoute> {
                    HistoryScreen(hiltViewModel(), onOpen = { nav.navigate(ResultsRoute(searchId = it)) })
                }
                composable<SavedRoute> {
                    FavoritesScreen(hiltViewModel(), onOpenSearch = { nav.navigate(ResultsRoute(searchId = it)) })
                }
                composable<ProfileRoute> { ProfileScreen(hiltViewModel()) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = hiltViewModel(), appInfo = appInfo,
                        onOpenProviders = { nav.navigate(ProviderSettingsRoute) },
                        onOpenProfile = { navigateTopLevel(ProfileRoute) },
                        onOpenLegal = { nav.navigate(LegalRoute(it)) },
                    )
                }
                composable<ProviderSettingsRoute> { ProviderSettingsScreen(hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<LegalRoute> { entry ->
                    LegalScreen(entry.toRoute<LegalRoute>().document, appInfo.versionName, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
