package com.autoinsta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.autoinsta.data.remote.OAuthRedirectBus
import com.autoinsta.ui.composepost.ComposePostScreen
import com.autoinsta.ui.home.HomeScreen
import com.autoinsta.ui.queue.ScheduleScreen
import com.autoinsta.ui.settings.SettingsScreen
import com.autoinsta.ui.theme.AutoInstaTheme

/**
 * The link the GitHub Pages bounce page forwards to. Must match the intent-filter in
 * AndroidManifest.xml exactly. This is deliberately *not* the redirect URI registered
 * with Meta — Meta only accepts https, and Android can only receive an Intent.
 */
private const val APP_REDIRECT_URI = "autoinsta://oauth"

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SCHEDULE = "schedule"
private const val ARG_POST_ID = "postId"
private const val NO_POST_ID = -1L
private const val ROUTE_COMPOSE_POST = "composePost?$ARG_POST_ID={$ARG_POST_ID}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Covers the cold-start case: the app was not running when the browser
        // redirected, so the login result arrives in the launch Intent.
        handleOAuthRedirect(intent)
        setContent {
            AppRoot()
        }
    }

    /**
     * The usual case: the app was already running behind the browser, so Android hands
     * the redirect here rather than starting a new Activity (see launchMode=singleTop).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    /**
     * Pull the login result out of the redirect and hand it to whatever is waiting.
     *
     * Instagram sends either `?code=...` on success or `?error=...&error_description=...`
     * when the user declines. Anything else means we were opened by a link we do not
     * understand, and is ignored.
     */
    private fun handleOAuthRedirect(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        // NOT BuildConfig.OAUTH_REDIRECT_URI: that is the https address Meta redirects
        // to. The browser lands there and the page forwards to this custom scheme, so
        // this is what actually arrives as an Intent.
        if (!data.toString().startsWith(APP_REDIRECT_URI)) return

        val code = data.getQueryParameter("code")
        val error = data.getQueryParameter("error_description")
            ?: data.getQueryParameter("error")

        when {
            !code.isNullOrBlank() ->
                OAuthRedirectBus.publish(OAuthRedirectBus.Result.Code(code))
            !error.isNullOrBlank() ->
                OAuthRedirectBus.publish(OAuthRedirectBus.Result.Error(error))
        }
    }
}

@Composable
private fun AppRoot() {
    AutoInstaTheme {
        RequestNotificationPermissionOnce()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onOpenSchedule = { navController.navigate(ROUTE_SCHEDULE) },
                        onCreatePost = {
                            navController.navigate("composePost?$ARG_POST_ID=$NO_POST_ID")
                        },
                        onEditPost = { postId ->
                            navController.navigate("composePost?$ARG_POST_ID=$postId")
                        },
                    )
                }
                composable(
                    route = ROUTE_COMPOSE_POST,
                    arguments = listOf(
                        navArgument(ARG_POST_ID) {
                            type = NavType.LongType
                            defaultValue = NO_POST_ID
                        }
                    ),
                ) { backStackEntry ->
                    val rawId = backStackEntry.arguments?.getLong(ARG_POST_ID) ?: NO_POST_ID
                    val postId = if (rawId == NO_POST_ID) null else rawId
                    ComposePostScreen(
                        postId = postId,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSchedule = { navController.navigate(ROUTE_SCHEDULE) },
                    )
                }
                composable(ROUTE_SCHEDULE) {
                    ScheduleScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/**
 * Asks for notification access once, on first launch.
 *
 * The app's whole value is that something happens while you are not watching, so being
 * unable to report the outcome is a real loss. It is still only a request: if refused,
 * posts publish exactly the same, they just go out quietly.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* granted or not, the app works either way */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
