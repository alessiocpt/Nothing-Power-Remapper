package io.github.alessiocpt.powerremapper.service

/** The package that owns the notification shade, Quick Settings and other system overlays. */
const val SYSTEM_UI_PACKAGE = "com.android.systemui"

/**
 * Keeps track of the Foreground App: the app the user is working in, which decides the Profile
 * that applies. Pure logic with no Android dependency, fed by the service's window-state events.
 *
 * Windows that sit over an app without replacing it never change the Foreground App: the system UI
 * (notification shade), the active keyboard, this app's own screens, and any window that is not an
 * activity (dialogs, popups). So opening the shade over the camera still counts as the camera.
 */
class ForegroundAppTracker(private val ownPackage: String) {

    var foregroundApp: String? = null
        private set

    /**
     * Considers one window-state change and returns whether the Foreground App changed.
     * [currentImePackage] is the active keyboard's package, or null if it is unknown.
     */
    fun onWindowStateChanged(
        packageName: String?,
        isActivity: Boolean,
        currentImePackage: String?
    ): Boolean {
        if (packageName.isNullOrEmpty() || !isActivity) return false
        if (packageName == SYSTEM_UI_PACKAGE || packageName == ownPackage) return false
        if (packageName == currentImePackage) return false
        if (packageName == foregroundApp) return false
        foregroundApp = packageName
        return true
    }
}
