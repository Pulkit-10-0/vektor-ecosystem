package com.vektor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vektor.data.repository.AuthRepository
import com.vektor.qr.QrManager
import com.vektor.ui.screens.auth.AuthViewModel
import com.vektor.ui.screens.auth.HealthOnboardingScreen
import com.vektor.ui.screens.auth.LoginScreen
import com.vektor.ui.screens.auth.SessionViewModel
import com.vektor.ui.screens.auth.SignupScreen
import com.vektor.ui.screens.emergency.CountdownScreen
import com.vektor.ui.screens.gemma.GemmaChatScreen
import com.vektor.ui.screens.gemma.GemmaChatViewModel
import com.vektor.ui.screens.health.HealthScreen
import com.vektor.ui.screens.home.HomeScreen
import com.vektor.ui.screens.onboarding.ModelDownloadScreen
import com.vektor.ui.screens.qr.QrCardScreen
import javax.inject.Inject

// Routes that show the bottom nav bar
private val MAIN_ROUTES = setOf("home/{uid}", "chat", "health", "qr/{uid}")

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun VektorNavGraph(
    qrManager: QrManager,
    triggerCountdown: Boolean = false,
    onCountdownTriggered: () -> Unit = {}
) {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val startDestination by sessionViewModel.startDestination.collectAsState()

    // Show splash while checking login state
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Detect current uid from back stack for nav items
    val currentUid = remember(navBackStackEntry) {
        navBackStackEntry?.arguments?.getString("uid") ?: "1"
    }

    // React to fall-detected broadcast from MainActivity
    LaunchedEffect(triggerCountdown) {
        if (triggerCountdown) {
            navController.navigate("countdown")
            onCountdownTriggered()
        }
    }

    val showBottomBar = MAIN_ROUTES.any { route ->
        navBackStackEntry?.destination?.hierarchy?.any { it.route == route } == true
    }

    val navItems = listOf(
        NavItem("home/$currentUid", "Home", Icons.Default.Home),
        NavItem("chat", "AI Chat", Icons.Default.Chat),
        NavItem("health", "Health", Icons.Default.Favorite),
        NavItem("qr/$currentUid", "QR Card", Icons.Default.QrCode)
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                val authViewModel: AuthViewModel = hiltViewModel()
                LoginScreen(
                    onLoginSuccess = { uid ->
                        navController.navigate("model_download/$uid") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToSignup = { navController.navigate("signup") },
                    viewModel = authViewModel
                )
            }
            composable("signup") {
                val authViewModel: AuthViewModel = hiltViewModel()
                SignupScreen(
                    onSignupSuccess = { uid ->
                        navController.navigate("health_onboarding/$uid") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onBackToLogin = { navController.popBackStack() },
                    viewModel = authViewModel
                )
            }
            composable("health_onboarding/{uid}") { backStackEntry ->
                val uid = backStackEntry.arguments?.getString("uid") ?: "1"
                HealthOnboardingScreen(
                    uid = uid,
                    onComplete = {
                        navController.navigate("model_download/$uid") {
                            popUpTo("health_onboarding/$uid") { inclusive = true }
                        }
                    }
                )
            }
            composable("model_download/{uid}") { backStackEntry ->
                val uid = backStackEntry.arguments?.getString("uid") ?: "1"
                ModelDownloadScreen(
                    onContinue = {
                        navController.navigate("home/$uid") {
                            popUpTo("model_download/$uid") { inclusive = true }
                        }
                    }
                )
            }
            composable("home/{uid}") {
                HomeScreen(
                    onNavigateToChat = {
                        navController.navigate("chat") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToQr = {
                        navController.navigate("qr/$currentUid") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToHealth = {
                        navController.navigate("health") {
                            launchSingleTop = true
                        }
                    },
                    onManualSos = { navController.navigate("countdown") },
                    onLogout = {
                        // AuthRepository logout is called from HomeViewModel or directly
                        // Navigate back to login clearing the entire back stack
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("countdown") {
                CountdownScreen(
                    onSosDispatched = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable("chat") {
                val vm: GemmaChatViewModel = hiltViewModel()
                val messages by vm.messages.collectAsState()
                val modelState by vm.modelState.collectAsState()
                val poweredBy by vm.poweredBy.collectAsState()
                GemmaChatScreen(
                    onSendMessage = vm::sendMessage,
                    messages = messages,
                    modelState = modelState,
                    poweredBy = poweredBy,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("health") {
                HealthScreen(onBack = { navController.popBackStack() })
            }
            composable("qr/{uid}") { backStackEntry ->
                val uid = backStackEntry.arguments?.getString("uid") ?: "unknown"
                QrCardScreen(
                    qrManager = qrManager,
                    uid = uid,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
