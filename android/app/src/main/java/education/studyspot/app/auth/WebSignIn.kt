package education.studyspot.app.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import education.studyspot.app.core.AppConfig

/**
 * Users sign in on the Study Spot website, opened in a Custom Tab at
 * `<web>/login?client=android` (or /register). Once any sign-in method succeeds
 * the website redirects to studyspot://oauth/callback#token=… (see
 * src/lib/iosHandoff.js), which MainActivity receives and passes to
 * [AuthStore.didSignIn] via navigation.DeepLinkHandler. Two-factor verification
 * then happens natively (ui/auth/Verify2FAScreen.kt).
 */
object WebSignIn {
    enum class Page(val path: String) { Login("login"), Register("register") }

    fun url(config: AppConfig, page: Page): Uri =
        "${config.webUrl}/${page.path}?client=${AppConfig.CLIENT_ID}".toUri()

    /** Opens the page; returns false if no browser could be found. */
    fun open(context: Context, config: AppConfig, page: Page, toolbarColor: Int? = null): Boolean =
        openInCustomTab(context, url(config, page), toolbarColor)

    /** Opens any URL (Google/Blackboard connections, etc.) in a Custom Tab. */
    fun openInCustomTab(context: Context, uri: Uri, toolbarColor: Int? = null): Boolean {
        val builder = CustomTabsIntent.Builder().setShowTitle(true)
        toolbarColor?.let {
            builder.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it).build())
        }
        val intent = builder.build()
        if (context !is android.app.Activity) intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            intent.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Reads a value from the query string or the #fragment of a callback URL. */
    fun param(uri: Uri, name: String): String? {
        uri.getQueryParameter(name)?.let { return it }
        val fragment = uri.encodedFragment ?: return null
        return fragment.split("&").firstNotNullOfOrNull { pair ->
            val i = pair.indexOf('=')
            if (i > 0 && Uri.decode(pair.substring(0, i)) == name) Uri.decode(pair.substring(i + 1)) else null
        }
    }
}
