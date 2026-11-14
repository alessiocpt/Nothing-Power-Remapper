package io.github.alessiocpt.powerremapper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.alessiocpt.powerremapper.unlock.ShizukuShell
import io.github.alessiocpt.powerremapper.unlock.ShizukuStatus

/**
 * Whether Shizuku is running and allows this app, so Camera Shutter can work. Follows Shizuku
 * starting, stopping and answering a permission request, and re-checks whenever the screen resumes,
 * since Shizuku can stop while the app is in the background (it does on every reboot).
 */
@Composable
fun rememberShizukuReady(): Boolean {
    val context = LocalContext.current
    val shizuku = remember(context) { ShizukuShell(context) }
    var ready by remember { mutableStateOf(shizuku.status() == ShizukuStatus.READY) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(shizuku, lifecycleOwner) {
        val refresh = { ready = shizuku.status() == ShizukuStatus.READY }
        val stopObserving = shizuku.observeStatus(refresh)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            stopObserving()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return ready
}
