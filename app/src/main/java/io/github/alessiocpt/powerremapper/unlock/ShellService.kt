package io.github.alessiocpt.powerremapper.unlock

import android.view.KeyEvent
import kotlin.system.exitProcess

/**
 * Runs the few shell commands this app needs, in the shell-privileged process Shizuku starts for it
 * by class name: enabling or disabling a Nothing consumer package through `pm` (which is why the
 * system-app Disable button being greyed out in App Info does not apply here), and sending an
 * allowlisted key through `input`. Every command is checked against its allowlist first.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun injectKey(keyCode: Int): Boolean {
        val command = keyInjectionCommand(keyCode) ?: return false
        return run(command)
    }

    override fun setPackageEnabled(packageName: String, enabled: Boolean, userId: Int): Boolean {
        // The process holds shell privileges, so it only ever touches the packages this app manages.
        if (packageName !in NothingConsumerPackages.CANDIDATES) {
            return false
        }
        return run(packageToggleCommand(packageName, enabled, userId))
    }

    private fun run(command: List<String>): Boolean {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        // Drain the output so the child cannot block on a full pipe before it exits.
        process.inputStream.bufferedReader().use { reader -> reader.readText() }
        return process.waitFor() == 0
    }
}

/** Keys the shell service may send: only the camera shutter key, for Camera Shutter. */
private val INJECTABLE_KEYS = setOf(KeyEvent.KEYCODE_CAMERA)

/** The `input` command sending [keyCode], or null when the key is not allowlisted. */
internal fun keyInjectionCommand(keyCode: Int): List<String>? =
    if (keyCode in INJECTABLE_KEYS) listOf("input", "keyevent", keyCode.toString()) else null

/** `disable-user` is the reversible disable that App Info's Disable button performs. */
internal fun packageToggleCommand(packageName: String, enabled: Boolean, userId: Int): List<String> =
    listOf("pm", if (enabled) "enable" else "disable-user", "--user", userId.toString(), packageName)
