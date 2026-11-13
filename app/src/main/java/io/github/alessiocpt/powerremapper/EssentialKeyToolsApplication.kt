package io.github.alessiocpt.powerremapper

import android.app.Application
import io.github.alessiocpt.powerremapper.diagnostics.installCrashReporting

/**
 * Exists to install crash capture before anything else runs. The accessibility service shares this
 * process, so a crash raised while handling a key press is covered by the same handler.
 */
class EssentialKeyToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashReporting(this)
    }
}
