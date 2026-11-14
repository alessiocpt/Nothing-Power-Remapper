package io.github.alessiocpt.powerremapper.actions

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import io.github.alessiocpt.powerremapper.R
import io.github.alessiocpt.powerremapper.glyph.GlyphDevice
import io.github.alessiocpt.powerremapper.glyph.GlyphLight
import io.github.alessiocpt.powerremapper.glyph.MatrixGlyphLight

/**
 * Runs a [KeyAction]. Actions that need system-level reach (screenshot, lock screen) require the
 * owning [AccessibilityService]; the rest only need a [Context]. Every handler fails soft: it logs
 * and surfaces a toast rather than throwing, because an action fires from a hardware key press with
 * no UI in the foreground.
 */
class ActionExecutor(
    private val context: Context,
    private val accessibilityService: AccessibilityService?
) {
    private companion object {
        const val TAG = "ActionExecutor"

        // Tracks the torch state ourselves: CameraManager has no synchronous "is torch on" query
        // that is reliable across devices, so we mirror the last toggle we issued.
    }

    private var torchEnabled = false
    private var silentUnavailableHintShown = false

    // Null on hardware without a Glyph Matrix. Bound lazily on first use rather than at
    // construction, since binding the service is pointless if the action is never triggered.
    private val glyphLight: GlyphLight? by lazy {
        GlyphDevice.current()?.let { MatrixGlyphLight(it.sdkDeviceId) }
    }
    // On the lock screen the shade's window fills the screen even when closed, so the window list
    // cannot tell open from closed there; the last toggle issued while locked stands in for it.
    private var shadeOpenedWhileLocked = false

    private var glyphLightBound = false
    private var glyphLightOn = false

    fun execute(action: KeyAction) {
        when (action) {
            is KeyAction.DoNothing -> Unit
            is KeyAction.LaunchApp -> launchApp(action.packageName)
            is KeyAction.ToggleFlashlight -> toggleFlashlight()
            is KeyAction.TakeScreenshot -> performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
                R.string.error_screenshot_failed
            )
            is KeyAction.LockScreen -> performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
                R.string.error_lock_screen_failed
            )
            is KeyAction.MediaPlayPause ->
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            is KeyAction.MediaNext -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            is KeyAction.MediaPrevious -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            is KeyAction.RingerCycle -> cycleRingerMode()
            is KeyAction.ToggleGlyphLight -> toggleGlyphLight()
            is KeyAction.ToggleNotifications ->
                toggleShade(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            is KeyAction.ToggleQuickSettings ->
                toggleShade(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            is KeyAction.LaunchAssistant -> launchAssistant()
            is KeyAction.LaunchCamera -> launchCamera(action.mode)
            is KeyAction.GoHome -> performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME,
                R.string.error_system_action_failed
            )
            is KeyAction.GoBack -> performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
                R.string.error_system_action_failed
            )
            is KeyAction.ShowRecents -> performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_RECENTS,
                R.string.error_system_action_failed
            )
            is KeyAction.ShowPowerMenu -> performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
                R.string.error_system_action_failed
            )
            is KeyAction.AdjustVolume -> adjustVolume(action.direction)
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Log.w(TAG, "No launch intent for package $packageName")
            toast(R.string.error_app_not_found)
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
        } catch (error: android.content.ActivityNotFoundException) {
            Log.w(TAG, "Activity not found launching $packageName", error)
            toast(R.string.error_app_not_found)
        }
    }

    private fun toggleFlashlight() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            toast(R.string.error_flashlight_unavailable)
            return
        }
        val cameraId = backCameraWithFlashOrNull(cameraManager)
        if (cameraId == null) {
            toast(R.string.error_flashlight_unavailable)
            return
        }
        try {
            val nextState = !torchEnabled
            cameraManager.setTorchMode(cameraId, nextState)
            torchEnabled = nextState
        } catch (error: CameraAccessException) {
            Log.w(TAG, "Torch toggle failed", error)
            toast(R.string.error_flashlight_unavailable)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Torch toggle rejected camera id", error)
            toast(R.string.error_flashlight_unavailable)
        }
    }

    /**
     * Binds to the Glyph service on first use, then flips [glyphLightOn]. A press that arrives
     * before binding completes is dropped rather than queued: the service binds in well under a
     * multi-tap window in practice, so a same-gesture repeat is the realistic case to handle, not a
     * slow bind.
     */
    private fun toggleGlyphLight() {
        val light = glyphLight
        if (light == null) {
            toast(R.string.error_glyph_light_unavailable)
            return
        }
        if (!glyphLightBound) {
            glyphLightBound = true
            light.bind(context) { setGlyphLight(light, on = true) }
            return
        }
        setGlyphLight(light, on = !glyphLightOn)
    }

    private fun setGlyphLight(light: GlyphLight, on: Boolean) {
        light.setAllOn(on)
        glyphLightOn = on
    }

    private fun backCameraWithFlashOrNull(cameraManager: CameraManager): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { cameraId ->
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val hasFlash =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (error: CameraAccessException) {
            Log.w(TAG, "Could not enumerate cameras", error)
            null
        }
    }

    private fun performGlobalAction(globalAction: Int, failureMessageRes: Int) {
        val service = accessibilityService
        if (service == null) {
            Log.w(TAG, "Global action $globalAction needs the accessibility service")
            toast(failureMessageRes)
            return
        }
        val dispatched = service.performGlobalAction(globalAction)
        if (!dispatched) {
            Log.w(TAG, "Global action $globalAction was not dispatched")
            toast(failureMessageRes)
        }
    }

    /**
     * Sends one media key to whichever session currently owns media buttons. The down/up pair is
     * load-bearing rather than ceremony: the framework treats play/pause as a voice key and holds
     * its down event until the up arrives, so a lone down would never be delivered. Nothing is
     * reported back — the platform offers no permission-free acknowledgement, so a press with no
     * player listening is a silent no-op, exactly like a headset button.
     */
    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            toast(R.string.error_media_unavailable)
            return
        }
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /**
     * Closes the shade if it is open in any state, otherwise opens it with [openAction]: both
     * toggles close a shade the other opened, or one pulled down by hand.
     */
    private fun toggleShade(openAction: Int) {
        val service = accessibilityService
        if (service == null) {
            toast(R.string.error_system_action_failed)
            return
        }
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val open = if (locked) shadeOpenedWhileLocked else isShadeOpen(service)
        if (open) {
            performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE,
                R.string.error_system_action_failed
            )
        } else {
            performGlobalAction(openAction, R.string.error_system_action_failed)
        }
        shadeOpenedWhileLocked = locked && !open
    }

    private fun isShadeOpen(service: AccessibilityService): Boolean {
        val screenHeight = service.getSystemService(WindowManager::class.java)
            ?.currentWindowMetrics?.bounds?.height() ?: return false
        val windows = service.windows.map { window ->
            val bounds = android.graphics.Rect().also(window::getBoundsInScreen)
            WindowSnapshot(type = window.type, heightPx = bounds.height())
        }
        return ShadeState.isShadeOpen(windows, screenHeight, AccessibilityWindowInfo.TYPE_SYSTEM)
    }

    /**
     * Sends the assist intent straight to the default assistant. Sent without a package, it opens a
     * chooser whenever several apps answer it, which is common.
     */
    private fun launchAssistant() {
        val assistantPackage = defaultAssistantPackage(context)
        if (assistantPackage == null) {
            toast(R.string.error_no_default_assistant)
            return
        }
        val intent = Intent(Intent.ACTION_ASSIST)
            .setPackage(assistantPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (error: android.content.ActivityNotFoundException) {
            Log.w(TAG, "Default assistant $assistantPackage does not answer ACTION_ASSIST", error)
            toast(R.string.error_no_default_assistant)
        }
    }

    /**
     * Opens the default camera. While locked, photo and selfie use the secure intent, which opens
     * over the lock screen; there is no secure video intent, so video asks to unlock first.
     * Selfie adds the front-camera extras common camera apps read; some apps ignore them.
     */
    private fun launchCamera(mode: CameraMode) {
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val intent = Intent(
            when {
                mode == CameraMode.VIDEO -> MediaStore.INTENT_ACTION_VIDEO_CAMERA
                locked -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                else -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            }
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (mode == CameraMode.SELFIE) {
            intent.putExtra(EXTRA_CAMERA_FACING, FRONT_CAMERA)
                .putExtra(EXTRA_LENS_FACING_FRONT, FRONT_CAMERA)
                .putExtra(EXTRA_USE_FRONT_CAMERA, true)
        }
        try {
            context.startActivity(intent)
        } catch (error: android.content.ActivityNotFoundException) {
            Log.w(TAG, "No camera app answers ${intent.action}", error)
            toast(R.string.error_camera_unavailable)
        }
    }

    /**
     * Moves the volume one step and shows the system panel, like the volume keys. Android picks the
     * stream, so it changes media volume while something plays and the ringer otherwise.
     */
    private fun adjustVolume(direction: VolumeDirection) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            toast(R.string.error_media_unavailable)
            return
        }
        audioManager.adjustSuggestedStreamVolume(
            when (direction) {
                VolumeDirection.UP -> AudioManager.ADJUST_RAISE
                VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
            },
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun cycleRingerMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            toast(R.string.error_ringer_unavailable)
            return
        }
        val policyAccessGranted = notificationPolicyAccessGranted()
        val nextMode = nextRingerMode(audioManager.ringerMode, policyAccessGranted)
        if (nextMode == null) {
            toast(R.string.hint_ringer_silent_exit_needs_policy)
            return
        }
        if (!applyRingerMode(audioManager, nextMode)) {
            toast(R.string.error_ringer_change_denied)
            return
        }
        if (!policyAccessGranted) {
            showSilentUnavailableHintOnce()
        }
    }

    /**
     * Writes [mode] and reports whether the system accepted it. OEM builds draw the
     * Do-Not-Disturb line in slightly different places, so the write stays guarded even for
     * transitions [nextRingerMode] believes are legal — a denied write must never escape, because
     * the action runs on the accessibility service's main thread.
     */
    private fun applyRingerMode(audioManager: AudioManager, mode: Int): Boolean = try {
        audioManager.ringerMode = mode
        true
    } catch (error: SecurityException) {
        Log.w(TAG, "Ringer mode change to $mode denied", error)
        false
    }

    private fun notificationPolicyAccessGranted(): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    /** Explains the shortened cycle once per process rather than on every key press. */
    private fun showSilentUnavailableHintOnce() {
        if (silentUnavailableHintShown) {
            return
        }
        silentUnavailableHintShown = true
        toast(R.string.hint_ringer_silent_needs_policy)
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }
}

// Unofficial "open the front camera" extras; Google Camera and most OEM camera apps read one.
private const val EXTRA_CAMERA_FACING = "android.intent.extras.CAMERA_FACING"
private const val EXTRA_LENS_FACING_FRONT = "android.intent.extras.LENS_FACING_FRONT"
private const val EXTRA_USE_FRONT_CAMERA = "android.intent.extra.USE_FRONT_CAMERA"
private const val FRONT_CAMERA = 1

// Settings keys the system writes when the user picks a digital assistant: the assist component,
// falling back to the voice interaction service. Either names the assistant's package.
private val ASSISTANT_SETTINGS = listOf("assistant", "voice_interaction_service")

/** The package of the default digital assistant, or null when none is set or readable. */
internal fun defaultAssistantPackage(context: Context): String? =
    ASSISTANT_SETTINGS.firstNotNullOfOrNull { key ->
        try {
            Settings.Secure.getString(context.contentResolver, key)
                ?.let(ComponentName::unflattenFromString)
                ?.packageName
        } catch (error: SecurityException) {
            null
        }
    }
