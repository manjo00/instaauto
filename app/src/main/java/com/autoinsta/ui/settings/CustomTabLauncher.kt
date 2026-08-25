package com.autoinsta.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens Instagram's login in **Chrome Custom Tabs** — the real browser, rendered by
 * Chrome, sharing Chrome's cookie jar.
 *
 * ## Why not a WebView
 * The first version of this screen embedded a WebView. Instagram's login page loads and
 * runs there, but their client-side framework refuses to *display* inside an embedded
 * browser: the DOM is fully present while the page paints pure white. See
 * `docs/STATUS.md` for the full diagnosis. It is deliberate on Meta's part, and no
 * WebView setting works around it.
 *
 * Custom Tabs also happen to be the safer choice: the login runs in Chrome's process, so
 * this app never has the opportunity to observe the password being typed.
 */
object CustomTabLauncher {

    /**
     * @return true if a browser opened; false when the device has none, in which case
     *         the caller should show an error rather than leave the user waiting.
     */
    fun openLogin(context: Context, url: String): Boolean {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            // Instagram's login is a single page; there is nothing to share or bookmark,
            // and hiding those keeps the flow feeling like part of the app.
            .setUrlBarHidingEnabled(false)
            .build()

        return try {
            intent.launchUrl(context, Uri.parse(url))
            true
        } catch (e: ActivityNotFoundException) {
            // No Custom Tabs provider (rare, but possible on stripped-down devices).
            // A plain browser Intent still completes the flow — the redirect back into
            // the app works the same way.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
