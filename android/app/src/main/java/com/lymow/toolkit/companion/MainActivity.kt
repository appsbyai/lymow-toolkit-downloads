package com.lymow.toolkit.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lymow.toolkit.companion.data.SettingsStore
import com.lymow.toolkit.companion.data.ThemeMode
import com.lymow.toolkit.companion.ui.ConnectScreen
import com.lymow.toolkit.companion.ui.HomeScreen
import com.lymow.toolkit.companion.ui.SettingsScreen
import com.lymow.toolkit.companion.ui.WebDashboardScreen
import com.lymow.toolkit.companion.ui.theme.LymowTheme

object Routes {
    const val CONNECT = "connect"
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val store = remember { SettingsStore(context.applicationContext) }
            val config by store.config.collectAsState(initial = null)
            val current = config

            LymowTheme(themeMode = current?.themeMode ?: ThemeMode.SYSTEM) {
                if (current != null) {
                    val start = if (current.connected && current.serverUrl.isNotBlank()) {
                        Routes.HOME
                    } else {
                        Routes.CONNECT
                    }
                    AppNav(store = store, startAt = start)
                }
            }
        }
    }
}

@Composable
fun AppNav(store: SettingsStore, startAt: String) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (route in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
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
            navController = nav,
            startDestination = startAt,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CONNECT) {
                ConnectScreen(
                    store = store,
                    onConnected = {
                        nav.navigate(Routes.HOME) {
                            popUpTo(Routes.CONNECT) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    store = store,
                    onOpenDashboard = { nav.navigate(Routes.DASHBOARD) },
                )
            }
            composable(Routes.DASHBOARD) {
                WebDashboardScreen(store = store)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    store = store,
                    onServerForgotten = {
                        nav.navigate(Routes.CONNECT) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
