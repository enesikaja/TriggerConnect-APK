package com.triggerconnect.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.ui.composer.TriggerComposerViewModel
import com.triggerconnect.app.ui.screens.code.CodeScreen
import com.triggerconnect.app.ui.screens.groups.CreateGroupScreen
import com.triggerconnect.app.ui.screens.groups.GroupDetailsScreen
import com.triggerconnect.app.ui.screens.groups.GroupInviteScreen
import com.triggerconnect.app.ui.screens.groups.GroupsScreen
import com.triggerconnect.app.ui.screens.history.HistoryScreen
import com.triggerconnect.app.ui.screens.home.HomeScreen
import com.triggerconnect.app.ui.screens.login.LoginScreen
import com.triggerconnect.app.ui.screens.message.MessageEditorScreen
import com.triggerconnect.app.ui.screens.notifications.NotificationsScreen
import com.triggerconnect.app.ui.screens.people.PeopleScreen
import com.triggerconnect.app.ui.screens.qr.PersonalQrScreen
import com.triggerconnect.app.ui.screens.scan.ScanScreen
import com.triggerconnect.app.ui.screens.settings.SettingsScreen
import com.triggerconnect.app.ui.screens.trigger.TriggerDetailsScreen
import com.triggerconnect.app.ui.screens.welcome.WelcomeScreen

private const val GRAPH_MAIN = "main"

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "Home", Icons.Default.Home),
    TabItem(Routes.PEOPLE, "People", Icons.Default.People),
    TabItem(Routes.GROUPS, "Groups", Icons.Default.GroupWork),
    TabItem(Routes.SCAN, "Scan", Icons.Default.QrCodeScanner),
    TabItem(Routes.HISTORY, "History", Icons.AutoMirrored.Filled.List),
)

@Composable
fun AppRoot(
    authState: AuthState,
    pendingEventId: String?,
    onPendingEventConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val start = remember { if (authState is AuthState.Authenticated) Routes.HOME else Routes.WELCOME }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.tabRoutes

    // On sign-out / session expiry, return to the welcome screen from anywhere.
    // (Login and the one-time code screen navigate forward explicitly so that
    // the Authenticated flip never skips the code display.)
    val authRoutes = setOf(Routes.WELCOME, Routes.LOGIN, Routes.CODE)
    androidx.compose.runtime.LaunchedEffect(authState, currentRoute) {
        if (authState is AuthState.Unauthenticated && currentRoute != null && currentRoute !in authRoutes) {
            navController.navigate(Routes.WELCOME) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Deep-link from a notification tap -> event details.
    androidx.compose.runtime.LaunchedEffect(pendingEventId, authState) {
        if (pendingEventId != null && authState is AuthState.Authenticated) {
            navController.navigate(Routes.triggerDetails(pendingEventId))
            onPendingEventConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            // -------- Auth graph --------
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onRegistered = { code -> navController.navigate(Routes.code(code)) },
                    onGoToLogin = { navController.navigate(Routes.LOGIN) },
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onGoToRegister = { navController.navigate(Routes.WELCOME) },
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Routes.CODE,
                arguments = listOf(navArgument(Routes.ARG_LOGIN_CODE) { type = NavType.StringType }),
            ) { entry ->
                CodeScreen(
                    loginCode = entry.arguments?.getString(Routes.ARG_LOGIN_CODE).orEmpty(),
                    onContinue = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            // -------- Main graph (shares the composer VM) --------
            navigation(startDestination = Routes.HOME, route = GRAPH_MAIN) {
                composable(Routes.HOME) { entry ->
                    HomeScreen(
                        composer = entry.sharedComposer(navController),
                        onPickRecipient = { navController.navigate(Routes.PEOPLE) },
                        onPickGroup = { navController.navigate(Routes.GROUPS) },
                        onEditMessage = { navController.navigate(Routes.MESSAGE_EDITOR) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onSent = { eventId -> navController.navigate(Routes.triggerDetails(eventId)) },
                    )
                }
                composable(Routes.PEOPLE) { entry ->
                    PeopleScreen(
                        composer = entry.sharedComposer(navController),
                        onShowMyQr = { navController.navigate(Routes.PERSONAL_QR) },
                        onScan = { navController.navigate(Routes.SCAN) },
                        onPickedRecipient = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                    )
                }
                composable(Routes.GROUPS) { entry ->
                    GroupsScreen(
                        composer = entry.sharedComposer(navController),
                        onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                        onOpenGroup = { id -> navController.navigate(Routes.groupDetails(id)) },
                        onScan = { navController.navigate(Routes.SCAN) },
                        onPickedRecipient = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                    )
                }
                composable(Routes.SCAN) {
                    ScanScreen(
                        onDone = { navController.popBackStack() },
                        onJoinedGroup = { id -> navController.navigate(Routes.groupDetails(id)) { popUpTo(Routes.SCAN) { inclusive = true } } },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onOpenEvent = { id -> navController.navigate(Routes.triggerDetails(id)) },
                    )
                }
                composable(Routes.PERSONAL_QR) {
                    PersonalQrScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.CREATE_GROUP) {
                    CreateGroupScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { id -> navController.navigate(Routes.groupDetails(id)) { popUpTo(Routes.GROUPS) } },
                    )
                }
                composable(
                    route = Routes.GROUP_DETAILS,
                    arguments = listOf(navArgument(Routes.ARG_GROUP_ID) { type = NavType.StringType }),
                ) { entry ->
                    GroupDetailsScreen(
                        groupId = entry.arguments?.getString(Routes.ARG_GROUP_ID).orEmpty(),
                        composer = entry.sharedComposer(navController),
                        onBack = { navController.popBackStack() },
                        onShowInvite = { id -> navController.navigate(Routes.groupInvite(id)) },
                        onTriggerGroup = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                        onOpenEvent = { id -> navController.navigate(Routes.triggerDetails(id)) },
                        onLeftOrDeleted = { navController.popBackStack(Routes.GROUPS, inclusive = false) },
                    )
                }
                composable(
                    route = Routes.GROUP_INVITE,
                    arguments = listOf(navArgument(Routes.ARG_GROUP_ID) { type = NavType.StringType }),
                ) { entry ->
                    GroupInviteScreen(
                        groupId = entry.arguments?.getString(Routes.ARG_GROUP_ID).orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.MESSAGE_EDITOR) { entry ->
                    MessageEditorScreen(
                        composer = entry.sharedComposer(navController),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.TRIGGER_DETAILS,
                    arguments = listOf(navArgument(Routes.ARG_EVENT_ID) { type = NavType.StringType }),
                ) { entry ->
                    TriggerDetailsScreen(
                        eventId = entry.arguments?.getString(Routes.ARG_EVENT_ID).orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenEvent = { id -> navController.navigate(Routes.triggerDetails(id)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onShowMyQr = { navController.navigate(Routes.PERSONAL_QR) },
                        onSavedMessages = { navController.navigate(Routes.MESSAGE_EDITOR) },
                    )
                }
            }
        }
    }
}

/** Retrieve the composer VM scoped to the whole main graph so all tabs share it. */
@Composable
private fun androidx.navigation.NavBackStackEntry.sharedComposer(
    navController: NavHostController,
): TriggerComposerViewModel {
    val parentEntry = remember(this) { navController.getBackStackEntry(GRAPH_MAIN) }
    return hiltViewModel(parentEntry)
}
