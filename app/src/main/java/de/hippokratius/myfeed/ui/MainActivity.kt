package de.hippokratius.myfeed.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nextcloud.android.sso.AccountImporter
import de.hippokratius.myfeed.MyFeedApp
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    /**
     * Rückweg der Nextcloud-Kontoauswahl: Die SSO-Bibliothek arbeitet mit dem
     * klassischen onActivityResult-Protokoll (sie besitzt den Request-Code,
     * daher keine Umstellung auf die Activity-Result-API möglich).
     */
    @Deprecated("SSO-Bibliothek nutzt das klassische onActivityResult-Protokoll")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        runCatching {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this) { account ->
                (application as MyFeedApp).graph.ssoSessionManager.onAccountGranted(account)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = routeFor(intent)

        val graph = (application as MyFeedApp).graph
        setContent {
            AppTheme {
                val navController = rememberNavController()

                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let { route ->
                        navController.navigate(route)
                        pendingRoute = null
                    }
                }

                NavHost(navController = navController, startDestination = "reader") {
                    composable("reader") {
                        ReaderScreen(
                            graph = graph,
                            onOpenFeeds = { navController.navigate("feeds") },
                            onOpenDiscover = { navController.navigate("feeds?tab=discover") },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenGroup = { groupId ->
                                navController.navigate("group/${URLEncoder.encode(groupId, "UTF-8")}")
                            },
                            onOpenSaved = { navController.navigate("saved") },
                        )
                    }
                    composable("saved") {
                        SavedScreen(graph = graph, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "feeds?tab={tab}",
                        arguments = listOf(navArgument("tab") { defaultValue = "manage" }),
                    ) { backStackEntry ->
                        FeedsScreen(
                            graph = graph,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate("settings") },
                            initialTab = if (backStackEntry.arguments?.getString("tab") == "discover") {
                                FeedsTab.DISCOVER
                            } else {
                                FeedsTab.MANAGE
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(graph = graph, onBack = { navController.popBackStack() })
                    }
                    composable("group/{groupId}") { backStackEntry ->
                        GroupScreen(
                            graph = graph,
                            groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFor(intent)?.let { pendingRoute = it }
    }

    /** Ziel-Route aus dem Start-Intent: Themen-Gruppe (Widget) oder Screen (App-Shortcut). */
    private fun routeFor(intent: Intent?): String? {
        intent?.getStringExtra(EXTRA_GROUP_ID)?.let { groupId ->
            return "group/${URLEncoder.encode(groupId, "UTF-8")}"
        }
        return when (intent?.getStringExtra(EXTRA_SCREEN)) {
            SCREEN_FEEDS -> "feeds"
            else -> null
        }
    }

    companion object {
        const val EXTRA_GROUP_ID = "de.hippokratius.myfeed.extra.GROUP_ID"
        const val EXTRA_SCREEN = "de.hippokratius.myfeed.extra.SCREEN"
        const val SCREEN_FEEDS = "feeds"
    }
}

@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
