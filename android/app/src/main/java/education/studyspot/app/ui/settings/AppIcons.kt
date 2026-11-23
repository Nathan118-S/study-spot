package education.studyspot.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import education.studyspot.app.MainActivity
import education.studyspot.app.R

/**
 * Alternate launcher icons. Each is an `<activity-alias android:name=".Launcher<Name>">`
 * of MainActivity in AndroidManifest.xml carrying the MAIN/LAUNCHER filter;
 * exactly one is enabled. MainActivity itself keeps the studyspot:// deep-link
 * filter, and explicit intents (notifications, widgets) target it directly, so
 * they work whichever icon is chosen.
 */
object AppIcons {
    enum class Icon(val alias: String, val label: String, @ColorRes val background: Int, @DrawableRes val foreground: Int) {
        Default("LauncherDefault", "Study Spot", R.color.ic_launcher_background, R.drawable.ic_launcher_foreground),
        Navy("LauncherNavy", "Navy", R.color.ic_launcher_background_navy, R.drawable.ic_launcher_foreground),
        Midnight("LauncherMidnight", "Midnight", R.color.ic_launcher_background_midnight, R.drawable.ic_launcher_foreground),
        Mono("LauncherMono", "Mono", R.color.ic_launcher_background_mono, R.drawable.ic_launcher_foreground_mono),
        Ocean("LauncherOcean", "Ocean", R.color.ic_launcher_background_ocean, R.drawable.ic_launcher_foreground_ocean),
        Grape("LauncherGrape", "Grape", R.color.ic_launcher_background_grape, R.drawable.ic_launcher_foreground_grape),
    }

    // Aliases are declared relative to the namespace (MainActivity's package),
    // which can differ from the application id; the component's package is the app's.
    private fun component(context: Context, icon: Icon) =
        ComponentName(context, MainActivity::class.java.name.substringBeforeLast('.') + "." + icon.alias)

    private fun isEnabled(context: Context, icon: Icon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == Icon.Default // the manifest's default
            else -> false
        }

    fun current(context: Context): Icon = Icon.entries.firstOrNull { isEnabled(context, it) } ?: Icon.Default

    /** Enables [icon]'s alias, then disables the rest, without killing the app. */
    fun set(context: Context, icon: Icon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, icon),
            if (icon == Icon.Default) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        Icon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                if (it == Icon.Default) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
