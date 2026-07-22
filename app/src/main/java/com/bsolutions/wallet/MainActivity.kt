package com.bsolutions.wallet

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.bsolutions.wallet.core.designsystem.WalletTheme
import com.bsolutions.wallet.presentation.accounts.AccountsScreen
import com.bsolutions.wallet.presentation.auth.LoginScreen
import com.bsolutions.wallet.presentation.auth.RecoverPasswordScreen
import com.bsolutions.wallet.presentation.auth.RegisterScreen
import com.bsolutions.wallet.presentation.auth.SplashScreen
import com.bsolutions.wallet.presentation.budgets.BudgetsScreen
import com.bsolutions.wallet.presentation.categories.CategoriesScreen
import com.bsolutions.wallet.presentation.categoryrules.CategoryRulesScreen

import com.bsolutions.wallet.presentation.dashboard.DashboardScreen
import com.bsolutions.wallet.presentation.debts.DebtsScreen
import com.bsolutions.wallet.presentation.emailconnections.EmailConnectionsScreen
import com.bsolutions.wallet.presentation.emailconnections.EmailOAuthReturnContract
import com.bsolutions.wallet.presentation.goals.GoalsScreen
import com.bsolutions.wallet.presentation.importcsv.ImportCsvScreen
import com.bsolutions.wallet.presentation.plannedpayments.PlannedPaymentsScreen
import com.bsolutions.wallet.presentation.navigation.AppDrawerContent
import com.bsolutions.wallet.presentation.notifications.NotificationsScreen
import com.bsolutions.wallet.presentation.profile.ProfileScreen
import com.bsolutions.wallet.presentation.reports.ReportsScreen
import com.bsolutions.wallet.presentation.security.SecurityScreen
import com.bsolutions.wallet.presentation.settings.SettingsScreen
import com.bsolutions.wallet.presentation.sync_settings.FindBankScreen
import com.bsolutions.wallet.presentation.transactions.TransactionsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val emailOAuthReturnNonce = MutableStateFlow(0L)

    private fun recordEmailOAuthReturn(intent: Intent?): Boolean {
        if (!EmailOAuthReturnContract.matches(intent?.dataString)) return false
        emailOAuthReturnNonce.value += 1L
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordEmailOAuthReturn(intent)
    }

    /** Permiso de notificaciones (Android 13+) para los recordatorios de pagos. */
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Estado de desbloqueo del candado biométrico (por proceso, no persistido). */
    private val unlocked = androidx.compose.runtime.mutableStateOf(false)

    override fun onStop() {
        super.onStop()
        // Re-bloquear al pasar a segundo plano: en Recents o al volver, el candado
        // biométrico vuelve a exigir autenticación (antes quedaba abierto hasta matar el proceso).
        unlocked.value = false
    }

    private fun showBiometricPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked.value = true
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchedFromEmailOAuth = recordEmailOAuthReturn(intent)
        // Edge-to-edge: en Android 15+ window.statusBarColor se ignora; el contenido
        // (TopAppBar verde del dashboard) se dibuja detrás de la status bar.
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            WalletTheme {
                val profile by mainViewModel.profile.collectAsState()

                SideEffect {
                    if (profile.screenCaptureProtectionEnabled) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // Candado biométrico: bloquea el contenido hasta autenticar
                if (profile.biometricLockEnabled && !unlocked.value) {
                    com.bsolutions.wallet.presentation.security.LockScreen(
                        onUnlockClick = { showBiometricPrompt() }
                    )
                    androidx.compose.runtime.LaunchedEffect(Unit) { showBiometricPrompt() }
                    return@WalletTheme
                }

                val navController = rememberNavController()
                val oauthReturnNonce by emailOAuthReturnNonce.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                LaunchedEffect(oauthReturnNonce, currentRoute) {
                    if (oauthReturnNonce > 0L && currentRoute != null && currentRoute != "email_connections") {
                        navController.navigate("email_connections") { launchSingleTop = true }
                    }
                }

                // Todas las pantallas tienen header verde (walletTopBarColors) → iconos de
                // status bar siempre claros. El fondo lo pinta el contenido (edge-to-edge).
                SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = false
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // Las secciones del drawer son destinos hermanos: muestran hamburguesa
                // (abre el menú), no flecha. La flecha queda para pantallas hijas apiladas.
                val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

                val mainRoutes = listOf(
                    "dashboard",
                    "accounts",
                    "transactions",
                    "budgets",
                    "reports",
                    "goals",
                    "debts",
                    "planned_payments"
                )

                // El drawer solo se puede abrir con gesto en las pantallas principales
                val drawerEnabled = currentRoute in mainRoutes

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerEnabled || drawerState.isOpen,
                    drawerContent = {
                        AppDrawerContent(
                            userName = profile.userName,
                            walletName = profile.walletName,
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                scope.launch { drawerState.close() }
                                if (route != currentRoute) {
                                    if (route in mainRoutes) {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        // Sin insets aquí: cada pantalla tiene su propio Scaffold/TopAppBar
                        // que ya gestiona la status bar (necesario para edge-to-edge)
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = if (launchedFromEmailOAuth) "email_connections" else "splash",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("splash") {
                                // MVP offline-first: entra directo, sin login obligatorio (auth llega en Fase 2)
                                SplashScreen(onFinished = {
                                    navController.navigate("dashboard") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                })

                            }
                            composable("login") {
                                LoginScreen(
                                    onNavigateToRegister = { navController.navigate("register") },
                                    onNavigateToRecover = { navController.navigate("recover_password") },
                                    onLoginSuccess = {
                                        // Volver a la pantalla anterior (Ajustes) o al Dashboard si no hay pila
                                        if (!navController.popBackStack()) {
                                            navController.navigate("dashboard")
                                        }
                                    }

                                )
                            }
                            composable("register") {
                                RegisterScreen(
                                    onNavigateToLogin = { navController.popBackStack() },
                                    onRegisterSuccess = {
                                        // Salir de register y login de una vez
                                        navController.popBackStack("login", inclusive = true)
                                    }

                                )
                            }
                            composable("recover_password") {
                                RecoverPasswordScreen(
                                    onNavigateToLogin = { navController.popBackStack() }
                                )
                            }
                            composable("dashboard") {
                                DashboardScreen(
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNavigateToNotifications = { navController.navigate("notifications") },
                                    onNavigateToTransactions = { navController.navigate("transactions") }
                                )
                            }

                            composable("accounts") {
                                AccountsScreen(onOpenDrawer = openDrawer)
                            }
                            composable("transactions") {
                                TransactionsScreen(onOpenDrawer = openDrawer)
                            }
                            composable("budgets") {
                                BudgetsScreen(onOpenDrawer = openDrawer)
                            }
                            composable("reports") {
                                ReportsScreen(onOpenDrawer = openDrawer)
                            }

                            composable("settings") {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToProfile = { navController.navigate("profile") },
                                    onNavigateToAccounts = { navController.navigate("accounts") },
                                    onNavigateToCategories = { navController.navigate("categories") },
                                    onNavigateToCategoryRules = { navController.navigate("category_rules") },
                                    onNavigateToSecurity = { navController.navigate("security") },
                                    onNavigateToSyncSettings = { navController.navigate("email_connections") },
                                    onNavigateToImportCsv = { navController.navigate("import_csv") },

                                    onNavigateToLogin = { navController.navigate("login") },
                                    onLogout = {}
                                )
                            }
                            composable("notifications") {
                                NotificationsScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("profile") {
                                ProfileScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("security") {
                                SecurityScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("sync_settings") {
                                EmailConnectionsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    oauthReturnNonce = oauthReturnNonce
                                )
                            }
                            composable("email_connections") {
                                EmailConnectionsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    oauthReturnNonce = oauthReturnNonce
                                )
                            }
                            composable("find_bank") {
                                FindBankScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("categories") {
                                CategoriesScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("category_rules") {
                                CategoryRulesScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("import_csv") {
                                ImportCsvScreen(onNavigateBack = { navController.popBackStack() })
                            }

                            composable("planned_payments") {
                                PlannedPaymentsScreen(onOpenDrawer = openDrawer)
                            }
                            composable("debts") {
                                DebtsScreen(onOpenDrawer = openDrawer)
                            }
                            composable("goals") {
                                GoalsScreen(onOpenDrawer = openDrawer)
                            }
                        }
                    }
                }
            }
        }
    }
}

