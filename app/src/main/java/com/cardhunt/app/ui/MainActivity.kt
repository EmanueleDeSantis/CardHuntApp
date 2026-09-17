package com.cardhunt.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cardhunt.app.data.local.ThemeStore
import com.cardhunt.app.ui.auth.AuthViewModel
import com.cardhunt.app.ui.auth.LoginScreen
import com.cardhunt.app.ui.camera.CaptureScreen
import com.cardhunt.app.ui.collection.CollectionScreen
import com.cardhunt.app.ui.map.CreateCardScreen
import com.cardhunt.app.ui.map.MapScreen
import com.cardhunt.app.ui.map.MapViewModel
import com.cardhunt.app.ui.notifications.NotificationsScreen
import com.cardhunt.app.ui.players.PlayersScreen
import com.cardhunt.app.ui.profile.ProfileScreen
import com.cardhunt.app.ui.staff.CardPlacementScreen
import com.cardhunt.app.ui.staff.StaffDashboardScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT

        if (Build.VERSION.SDK_INT >= 26) {
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            CardHuntRoot()
        }
    }
}

@Composable
fun CardHuntRoot(
    vm: AuthViewModel = hiltViewModel(),
) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle(initialValue = ThemeStore.SYSTEM)

    val useDark = when (themeMode) {
        ThemeStore.DARK -> true
        ThemeStore.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val session by vm.session.collectAsStateWithLifecycle(initialValue = vm.snapshot())

    val view = LocalView.current

    LaunchedEffect(useDark, session == null) {
        if (session == null) {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view)
                    .isAppearanceLightStatusBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = if (useDark) darkColorScheme() else lightColorScheme()
    ) {
        if (session == null) {
            LoginScreen()
        } else {
            MainApp(
                onLogout = { vm.logout() },
                useDark = useDark
            )
        }
    }
}

@Composable
fun MainApp(
    onLogout: () -> Unit,
    useDark: Boolean,
) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(0) }

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val darkStatusIcons = when {
        currentRoute == "home" && tab == 0 -> true
        currentRoute != null && currentRoute.startsWith("place_card") -> true
        else -> !useDark
    }

    val view = LocalView.current

    LaunchedEffect(darkStatusIcons) {
        val window = (view.context as? Activity)?.window
        window?.let {
            WindowCompat.getInsetsController(it, view)
                .isAppearanceLightStatusBars = darkStatusIcons
        }
    }

    NavHost(
        navController = nav,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                nav = nav,
                onLogout = onLogout,
                tab = tab,
                onTabChange = { tab = it }
            )
        }

        composable("staff") {
            StaffDashboardScreen(navController = nav)
        }

        composable("place_card") {
            CardPlacementScreen(nav)
        }

        composable(
            route = "place_card/{lat}/{lng}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lng") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            CardPlacementScreen(
                nav = nav,
                initialLat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull(),
                initialLng = backStackEntry.arguments?.getString("lng")?.toDoubleOrNull()
            )
        }

        composable("capture/{cardId}/{cardName}/{rarity}") {
            CaptureScreen(nav)
        }

        composable("create_card/{lat}/{lng}") {
            CreateCardScreen(nav)
        }
    }
}

private data class Tab(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun HomeScreen(
    nav: NavController,
    onLogout: () -> Unit,
    tab: Int,
    onTabChange: (Int) -> Unit,
) {
    BackHandler {
        /* Back does nothing on the home screen. */
    }

    val mapVm: MapViewModel = hiltViewModel()

    val tabs = listOf(
        Tab("Map", Icons.Filled.LocationOn),
        Tab("Cards", Icons.Filled.CreditCard),
        Tab("Inbox", Icons.Filled.Email),
        Tab("Players", Icons.Filled.Face),
        Tab("Profile", Icons.Filled.Person),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { onTabChange(index) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(item.label)
                        },
                    )
                }
            }
        }
    ) { padding ->

        val contentModifier = if (tab == 0) {
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        } else {
            Modifier
                .fillMaxSize()
                .padding(padding)
        }

        Box(contentModifier) {
            when (tab) {
                0 -> MapScreen(
                    nav = nav,
                    vm = mapVm
                )

                1 -> CollectionScreen()

                2 -> NotificationsScreen(
                    onOpenTab = { onTabChange(it) },
                    onFocusCard = { lat, lng ->
                        mapVm.focusOnCard(lat, lng)
                        onTabChange(0)
                    }
                )

                3 -> PlayersScreen()

                4 -> ProfileScreen(
                    navController = nav,
                    onLogout = onLogout
                )
            }
        }
    }
}