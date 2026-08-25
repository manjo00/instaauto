package com.autoinsta.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.autoinsta.BuildConfig
import com.autoinsta.data.remote.InstagramAuthApi

/**
 * The Instagram login page, hosted in a WebView so the app can catch the redirect.
 *
 * ## Why a WebView rather than a browser tab
 * Meta only accepts HTTPS redirect URIs, and this app has no server, so there is nowhere
 * for a real callback to land. Instead we watch every navigation this WebView attempts.
 * When Instagram tries to go to our registered redirect URI, we **cancel that navigation**
 * and read the `code` parameter straight out of the URL. Nothing is ever loaded from it,
 * which is why it does not matter that `https://localhost/oauth` hosts nothing.
 *
 * Cookies are cleared before and after, so this never leaves an Instagram session behind
 * inside the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginScreen(
    onCodeReceived: (String) -> Unit,
    onCancelled: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }
    // Guards against the callback firing twice — a redirect can be observed by more than
    // one WebViewClient hook, and exchanging the same code twice fails.
    var handled by remember { mutableStateOf(false) }

    BackHandler { onCancelled() }

    DisposableEffect(Unit) {
        // Start from a clean slate so the user is actually asked to log in, rather than
        // being silently reconnected as whoever used this WebView last.
        CookieManager.getInstance().removeAllCookies(null)
        onDispose {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun handleUrl(url: String?): Boolean {
        if (url == null || handled) return false
        if (!url.startsWith(BuildConfig.OAUTH_REDIRECT_URI)) return false

        handled = true
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error_description")
            ?: uri.getQueryParameter("error")

        when {
            !code.isNullOrBlank() -> onCodeReceived(code)
            !error.isNullOrBlank() -> onError(error)
            else -> onError("Instagram redirected without a login code.")
        }
        return true // cancel the navigation — there is nothing to load there
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Connect Instagram") },
                navigationIcon = {
                    IconButton(onClick = onCancelled) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true       // Instagram's login needs it
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = handleUrl(request?.url?.toString())

                            override fun onPageStarted(v: WebView?, url: String?, f: Bitmap?) {
                                // Belt and braces: some redirects surface here rather than
                                // in shouldOverrideUrlLoading. `handled` stops a double call.
                                if (handleUrl(url)) {
                                    v?.stopLoading()
                                    return
                                }
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }
                        }

                        loadUrl(InstagramAuthApi.authorizationUrl())
                    }
                },
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
