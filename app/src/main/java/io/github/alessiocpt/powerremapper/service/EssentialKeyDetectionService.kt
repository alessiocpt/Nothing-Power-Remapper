package io.github.alessiocpt.powerremapper.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import io.github.alessiocpt.powerremapper.actions.ActionExecutor
import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.actions.KeyHaptics
import io.github.alessiocpt.powerremapper.core.KeyGesture
import io.github.alessiocpt.powerremapper.core.KeyGestureClassifier
import io.github.alessiocpt.powerremapper.settings.DEFAULT_ESSENTIAL_KEY_SCAN_CODE
import io.github.alessiocpt.powerremapper.settings.HapticPattern
import io.github.alessiocpt.powerremapper.settings.HapticStrength
import io.github.alessiocpt.powerremapper.settings.ProfileSet
import io.github.alessiocpt.powerremapper.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Observes hardware key events, isolates the Essential Key by its learned scanCode, classifies the
 * gesture and runs the action the active Profile maps it to. It also follows window-state changes,
 * reading only package and class names, to know the Foreground App and so the Profile that applies. Consuming the event does NOT stop Nothing OS's own single
 * press handling (that needs the separate unlock flow); this service only observes and reacts.
 *
 * While the UI's detection screen is active it instead publishes every key event to
 * [KeyEventStream] so the user can capture the Essential Key's scanCode, and suppresses actions.
 */
class EssentialKeyDetectionService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var actionExecutor: ActionExecutor
    private lateinit var keyHaptics: KeyHaptics
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var inputMethodManager: InputMethodManager
    private lateinit var foregroundAppTracker: ForegroundAppTracker

    // Whether "package/class" names an activity. Window-state events repeat the same few classes,
    // so each lookup is done once per process.
    private val activityLookups = HashMap<String, Boolean>()

    // Rebuilt whenever settings change, and between gestures when the active Profile's gestures
    // differ, so the classifier only waits for gestures that would do something.
    private var classifier: KeyGestureClassifier? = null
    private var classifierGestures: Set<KeyGesture> = emptySet()
    // The down time of the last key-down seen, so a release whose key-down never arrived (screen
    // off) is still recognised as the start of a new press.
    private var lastKeyDownTimeMs: Long? = null
    private var learnedScanCode = DEFAULT_ESSENTIAL_KEY_SCAN_CODE
    private var profileSet = ProfileSet()
    private var hapticStrength = HapticStrength.OFF
    private var customHapticPattern = HapticPattern.DEFAULT
    private var hapticsOnActionOnly = false
    private var actionsOnlyWhenUnlocked = false
    private var suppressNextClassifiedAction = false
    // Tracks which suppression state the current classifier was built for, so a new press can pick
    // up a screen transition without disturbing a multi-tap sequence already in flight.
    private var classifierBuiltWhileSuppressed = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        actionExecutor = ActionExecutor(context = this, accessibilityService = this)
        keyHaptics = KeyHaptics(this)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        inputMethodManager = getSystemService(InputMethodManager::class.java)
        foregroundAppTracker = ForegroundAppTracker(ownPackage = packageName)
        collectSettings()
    }

    private fun collectSettings() {
        val repository = SettingsRepository.getInstance(this)
        serviceScope.launch {
            combine(
                repository.essentialKeyScanCode,
                repository.profileSet
            ) { scanCode, profiles -> scanCode to profiles }
                .collect { (scanCode, profiles) ->
                    learnedScanCode = scanCode
                    profileSet = profiles
                    rebuildClassifier()
                }
        }
        serviceScope.launch {
            repository.hapticStrength.collect { strength -> hapticStrength = strength }
        }
        serviceScope.launch {
            repository.customHapticPattern.collect { pattern -> customHapticPattern = pattern }
        }
        serviceScope.launch {
            repository.hapticsOnActionOnly.collect { enabled -> hapticsOnActionOnly = enabled }
        }
        serviceScope.launch {
            repository.actionsOnlyWhenUnlocked.collect { enabled -> actionsOnlyWhenUnlocked = enabled }
        }
    }

    private fun rebuildClassifier() {
        classifier?.reset()
        suppressNextClassifiedAction = false
        classifierBuiltWhileSuppressed = KeyEventStream.actionExecutionSuppressed
        classifierGestures = enabledGestures()
        classifier = KeyGestureClassifier(
            enabledGestures = classifierGestures,
            scheduler = HandlerGestureScheduler(mainHandler),
            onGesture = ::onGestureClassified,
            onPress = ::onPress
        )
    }

    /**
     * Only the gestures with a real mapping need recognising; this lets the classifier resolve
     * simpler gestures without waiting for the multi-tap window. The key-test screen suppresses
     * action execution precisely so it can show the raw gesture the user pressed, so it needs every
     * gesture recognised regardless of what is currently mapped.
     */
    private fun enabledGestures(): Set<KeyGesture> =
        if (KeyEventStream.actionExecutionSuppressed) {
            KeyGesture.entries.toSet()
        } else {
            profileSet.activeGestures(activeApp())
        }

    /**
     * The app whose Profile applies: the Foreground App, or none (the Default Profile) while the
     * lock screen shows, since the app underneath is not what the user is looking at.
     */
    private fun activeApp(): String? =
        if (keyguardManager.isKeyguardLocked) null else foregroundAppTracker.foregroundApp

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (KeyEventStream.detectionModeActive) {
            publishForLearning(event)
            // Do not consume: let the system keep behaving normally while learning.
            return false
        }
        return handleOperationalEvent(event)
    }

    private fun publishForLearning(event: KeyEvent) {
        if (!KeyEventFilter.isLearnableCandidate(event.keyCode)) {
            return
        }
        KeyEventStream.publishRawEvent(
            ObservedKeyEvent(
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                action = event.action,
                timestampMs = event.eventTime
            )
        )
    }

    private fun handleOperationalEvent(event: KeyEvent): Boolean {
        if (!KeyEventFilter.matchesLearnedKey(event.scanCode, learnedScanCode)) {
            return false
        }
        // Switching app, locking the phone, or entering or leaving the key-test screen can change
        // which gestures the classifier should recognise. Only apply that when a new press starts
        // while no gesture is in flight, so a multi-tap sequence is never cut short.
        if (startsNewPress(event) && classifier?.isIdle != false &&
            (classifierBuiltWhileSuppressed != KeyEventStream.actionExecutionSuppressed ||
                classifierGestures != enabledGestures())
        ) {
            rebuildClassifier()
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastKeyDownTimeMs = event.downTime
        }
        // Remember that this gesture began on the test screen. Some gestures resolve after a short
        // timeout, when the user may already have navigated away and cleared the screen-wide flag.
        if (KeyEventStream.actionExecutionSuppressed) {
            suppressNextClassifiedAction = true
        }
        // Mirror matching events to the stream so the key-test screen can log them live.
        KeyEventStream.publishRawEvent(
            ObservedKeyEvent(
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                action = event.action,
                timestampMs = event.eventTime
            )
        )
        feedClassifier(event)
        return true
    }

    /**
     * A key-down with a new down time starts a press; auto-repeats share it. A key-up starts one
     * only when its key-down never arrived, as when Nothing OS spends it waking the screen.
     */
    private fun startsNewPress(event: KeyEvent): Boolean =
        (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) &&
            event.downTime != lastKeyDownTimeMs

    private fun feedClassifier(event: KeyEvent) {
        val activeClassifier = classifier ?: return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> activeClassifier.onKeyDown(event.downTime)
            KeyEvent.ACTION_UP -> activeClassifier.onKeyUp(event.downTime, event.eventTime)
        }
    }

    // By default, like a physical button, feedback confirms the press itself rather than waiting
    // for the gesture to resolve. A press that does nothing, because nothing is mapped or it is
    // ignored while locked, gets no feedback.
    private fun onPress() {
        if (!hapticsOnActionOnly &&
            profileSet.activeGestures(activeApp()).isNotEmpty() &&
            !ignoredWhileLocked()
        ) {
            keyHaptics.perform(hapticStrength, customHapticPattern)
        }
    }

    private fun onGestureClassified(gesture: KeyGesture) {
        KeyEventStream.publishGesture(
            ObservedGesture(gesture = gesture, timestampMs = System.currentTimeMillis())
        )
        val actionSuppressed = KeyEventStream.actionExecutionSuppressed || suppressNextClassifiedAction
        suppressNextClassifiedAction = false
        if (!actionSuppressed && !ignoredWhileLocked()) {
            val action = profileSet.resolve(activeApp(), gesture)
            if (hapticsOnActionOnly && action != KeyAction.DoNothing) {
                keyHaptics.perform(hapticStrength, customHapticPattern)
            }
            actionExecutor.execute(action)
        }
    }

    /**
     * Whether the key does nothing right now because actions are limited to an unlocked device.
     * The test is the lock screen showing, not the screen being off: with the screen off the press
     * itself wakes the device, so by the time a gesture resolves it is awake and showing the lock
     * screen. Checked when the action is about to run rather than when the press began, so a press
     * that straddles the device locking cannot slip through.
     */
    private fun ignoredWhileLocked(): Boolean =
        actionsOnlyWhenUnlocked && keyguardManager.isKeyguardLocked

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val windowPackage = event.packageName?.toString() ?: return
        val changed = foregroundAppTracker.onWindowStateChanged(
            packageName = windowPackage,
            isActivity = isActivity(windowPackage, event.className?.toString()),
            currentImePackage = inputMethodManager.currentInputMethodInfo?.packageName
        )
        val foregroundApp = foregroundAppTracker.foregroundApp
        if (changed && foregroundApp != null) {
            foregroundAppState.value = foregroundApp
            recentForegroundAppsState.value =
                (listOf(foregroundApp) + recentForegroundAppsState.value).take(RECENT_FOREGROUND_APPS)
        }
    }

    /**
     * Whether a window belongs to an activity rather than a dialog, popup or toast. A package this
     * app cannot see under package-visibility rules counts as not an activity, which keeps the
     * previous Foreground App; every app a Profile can be made for is visible.
     */
    private fun isActivity(windowPackage: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        return activityLookups.getOrPut("$windowPackage/$className") {
            try {
                packageManager.getActivityInfo(
                    ComponentName(windowPackage, className),
                    PackageManager.ComponentInfoFlags.of(0)
                )
                true
            } catch (error: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    override fun onInterrupt() {
        classifier?.reset()
        suppressNextClassifiedAction = false
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        isRunning = false
        foregroundAppState.value = null
        recentForegroundAppsState.value = emptyList()
        classifier?.reset()
        suppressNextClassifiedAction = false
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
    }

    companion object {
        /**
         * Whether the service is connected and running. The home screen reads this to show the
         * live status without an IPC round-trip; it is set on connect and cleared on teardown.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        private val foregroundAppState = MutableStateFlow<String?>(null)

        /** The Foreground App's package, or null until the service has seen one or while it is off. */
        val foregroundApp: StateFlow<String?> = foregroundAppState.asStateFlow()

        private const val RECENT_FOREGROUND_APPS = 5
        private val recentForegroundAppsState = MutableStateFlow<List<String>>(emptyList())

        /**
         * The last few Foreground Apps, newest first, for Key Test. Reaching Key Test always passes
         * through the launcher, so the current Foreground App alone cannot show what came before.
         */
        val recentForegroundApps: StateFlow<List<String>> = recentForegroundAppsState.asStateFlow()
    }
}
