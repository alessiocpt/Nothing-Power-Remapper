package io.github.alessiocpt.powerremapper.unlock

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * Runs shell commands through Shizuku: enabling and disabling consumer packages, for builds where
 * App Info greys out the Disable button, and sending the camera shutter key. Owns every call into
 * the Shizuku API so the rest of the app only sees [ShizukuStatus], [setPackageEnabled] and
 * [injectKey].
 */
class ShizukuShell(context: Context) {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellService::class.java.name)
    )
        .processNameSuffix(SERVICE_PROCESS_SUFFIX)
        .daemon(false)

    private val userId = Process.myUid() / PER_USER_UID_RANGE

    // Kept bound between key presses: starting the service process takes long enough to make a
    // camera shutter feel broken. Package toggling stays one bind per call, as it is rare.
    private var keyService: IShellService? = null
    private var keyConnection: ServiceConnection? = null

    fun status(): ShizukuStatus = when {
        !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
        Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.PERMISSION_DENIED
        else -> ShizukuStatus.PERMISSION_REQUIRED
    }

    fun requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    /** Calls [onChange] when Shizuku starts, stops, or answers the permission request. */
    fun observeStatus(onChange: () -> Unit): () -> Unit {
        val binderReceived = Shizuku.OnBinderReceivedListener { onChange() }
        val binderDead = Shizuku.OnBinderDeadListener { onChange() }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> onChange() }
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        return {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }

    /** Returns whether `pm` accepted the change. Binds a fresh service for each call and releases it. */
    suspend fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean {
        val service = CompletableDeferred<IShellService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service.complete(IShellService.Stub.asInterface(binder))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service.completeExceptionally(RemoteException("Shizuku user service disconnected"))
            }
        }
        Shizuku.bindUserService(userServiceArgs, connection)
        return try {
            val toggleService = withTimeout(BIND_TIMEOUT_MS) { service.await() }
            // The service call blocks until `pm` exits, so it must stay off the main thread.
            withContext(Dispatchers.IO) { toggleService.setPackageEnabled(packageName, enabled, userId) }
        } catch (error: RemoteException) {
            Log.w(TAG, "Shizuku could not toggle $packageName", error)
            false
        } catch (error: TimeoutCancellationException) {
            Log.w(TAG, "Shizuku user service did not start for $packageName", error)
            false
        } finally {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        }
    }

    /**
     * Sends an allowlisted key through the shell. Returns false when Shizuku is not ready, the
     * service cannot start, or the key is refused.
     */
    suspend fun injectKey(keyCode: Int): Boolean {
        if (status() != ShizukuStatus.READY) return false
        return try {
            val service = keyService ?: bindKeyService()
            withContext(Dispatchers.IO) { service.injectKey(keyCode) }
        } catch (error: RemoteException) {
            Log.w(TAG, "Shizuku could not inject key $keyCode", error)
            releaseKeyService()
            false
        } catch (error: TimeoutCancellationException) {
            Log.w(TAG, "Shizuku user service did not start for key $keyCode", error)
            releaseKeyService()
            false
        }
    }

    private suspend fun bindKeyService(): IShellService {
        val service = CompletableDeferred<IShellService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service.complete(IShellService.Stub.asInterface(binder))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                keyService = null
                service.completeExceptionally(RemoteException("Shizuku user service disconnected"))
            }
        }
        keyConnection = connection
        Shizuku.bindUserService(userServiceArgs, connection)
        return withTimeout(BIND_TIMEOUT_MS) { service.await() }.also { keyService = it }
    }

    private fun releaseKeyService() {
        keyConnection?.let { connection ->
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
        }
        keyConnection = null
        keyService = null
    }

    private companion object {
        const val TAG = "ShizukuShell"
        const val SERVICE_PROCESS_SUFFIX = "package_toggle"
        const val PERMISSION_REQUEST_CODE = 1
        const val BIND_TIMEOUT_MS = 10_000L

        // Android derives the user id from an app uid by dividing out this per-user range.
        const val PER_USER_UID_RANGE = 100_000
    }
}
