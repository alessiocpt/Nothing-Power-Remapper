package io.github.alessiocpt.powerremapper.actions

/**
 * A thing the app can do in response to a classified key gesture. Each variant is a pure data
 * description of intent; running it is [ActionExecutor]'s job. Keeping the model free of Android
 * types lets it be serialised for DataStore and unit-tested on the JVM.
 */
sealed interface KeyAction {

    /** Stable identifier persisted in settings. Payload (if any) is stored separately. */
    val id: String

    /**
     * Deliberately does nothing: the default for every gesture, and how a Profile blocks one. Its
     * persisted id stays "none" so older settings still read back.
     */
    data object DoNothing : KeyAction {
        override val id: String = ID
        const val ID: String = "none"
    }

    /** Launches an installed app by its package name. */
    data class LaunchApp(val packageName: String) : KeyAction {
        override val id: String = ID

        companion object {
            const val ID: String = "launch_app"
        }
    }

    /** Toggles the device flashlight (back-camera torch). */
    data object ToggleFlashlight : KeyAction {
        override val id: String = ID
        const val ID: String = "toggle_flashlight"
    }

    /** Takes a screenshot via the accessibility global action. */
    data object TakeScreenshot : KeyAction {
        override val id: String = ID
        const val ID: String = "take_screenshot"
    }

    /** Locks the screen via the accessibility global action. */
    data object LockScreen : KeyAction {
        override val id: String = ID
        const val ID: String = "lock_screen"
    }

    /** Sends a play/pause media key to the active media session. */
    data object MediaPlayPause : KeyAction {
        override val id: String = ID
        const val ID: String = "media_play_pause"
    }

    /** Skips to the next track in whichever app currently owns media buttons. */
    data object MediaNext : KeyAction {
        override val id: String = ID
        const val ID: String = "media_next"
    }

    /**
     * Skips to the previous track. What "previous" means is the player's decision: many restart the
     * current track instead once it has been playing for a few seconds.
     */
    data object MediaPrevious : KeyAction {
        override val id: String = ID
        const val ID: String = "media_previous"
    }

    /** Cycles the ringer mode: normal -> vibrate -> silent -> normal. */
    data object RingerCycle : KeyAction {
        override val id: String = ID
        const val ID: String = "ringer_cycle"
    }

    /** Toggles the whole Glyph Matrix on steady, as a light. Only offered on Glyph hardware. */
    data object ToggleGlyphLight : KeyAction {
        override val id: String = ID
        const val ID: String = "toggle_glyph_light"
    }

    /** Goes to the home screen via the accessibility global action. */
    data object GoHome : KeyAction {
        override val id: String = ID
        const val ID: String = "go_home"
    }

    /** Goes back via the accessibility global action. */
    data object GoBack : KeyAction {
        override val id: String = ID
        const val ID: String = "go_back"
    }

    /** Opens the recent-apps overview via the accessibility global action. */
    data object ShowRecents : KeyAction {
        override val id: String = ID
        const val ID: String = "show_recents"
    }

    /** Opens the power menu via the accessibility global action. */
    data object ShowPowerMenu : KeyAction {
        override val id: String = ID
        const val ID: String = "show_power_menu"
    }

    /**
     * Opens the notification shade, or closes it if it is already open in any state, including
     * when Quick Settings is showing.
     */
    data object ToggleNotifications : KeyAction {
        override val id: String = ID
        const val ID: String = "toggle_notifications"
    }

    /** Opens the fully expanded Quick Settings, or closes the shade if it is open in any state. */
    data object ToggleQuickSettings : KeyAction {
        override val id: String = ID
        const val ID: String = "toggle_quick_settings"
    }

    /**
     * Opens the app set as the device's default digital assistant, the way the assistant gesture
     * does, so with Claude as the assistant it opens Claude ready to talk.
     */
    data object LaunchAssistant : KeyAction {
        override val id: String = ID
        const val ID: String = "launch_assistant"
    }

    /** Opens the default camera app in [mode]; photo mode also works over the lock screen. */
    data class LaunchCamera(val mode: CameraMode) : KeyAction {
        override val id: String = ID

        companion object {
            const val ID: String = "launch_camera"
        }
    }

    /**
     * Sends the camera shutter key to the Foreground App through Shizuku, so a camera app takes a
     * photo, or starts or stops recording in video mode. Needs Shizuku running and granted.
     */
    data object CameraShutter : KeyAction {
        override val id: String = ID
        const val ID: String = "camera_shutter"
    }

    /**
     * Raises or lowers the volume of whatever is playing, showing the system volume panel. Not the
     * same as sending a volume key to an app, which Camera Shutter does.
     */
    data class AdjustVolume(val direction: VolumeDirection) : KeyAction {
        override val id: String = ID

        companion object {
            const val ID: String = "adjust_volume"
        }
    }

    companion object {
        /**
         * Rebuilds a [KeyAction] from its persisted [id] and optional [payload]. Unknown ids fall
         * back to [DoNothing] so a settings schema change can never crash the reader.
         */
        fun fromPersisted(id: String, payload: String?): KeyAction = when (id) {
            DoNothing.ID -> DoNothing
            LaunchApp.ID -> if (payload.isNullOrEmpty()) DoNothing else LaunchApp(payload)
            ToggleFlashlight.ID -> ToggleFlashlight
            TakeScreenshot.ID -> TakeScreenshot
            LockScreen.ID -> LockScreen
            MediaPlayPause.ID -> MediaPlayPause
            MediaNext.ID -> MediaNext
            MediaPrevious.ID -> MediaPrevious
            RingerCycle.ID -> RingerCycle
            ToggleGlyphLight.ID -> ToggleGlyphLight
            LaunchAssistant.ID -> LaunchAssistant
            GoHome.ID -> GoHome
            ToggleNotifications.ID -> ToggleNotifications
            ToggleQuickSettings.ID -> ToggleQuickSettings
            GoBack.ID -> GoBack
            ShowRecents.ID -> ShowRecents
            ShowPowerMenu.ID -> ShowPowerMenu
            CameraShutter.ID -> CameraShutter
            LaunchCamera.ID -> CameraMode.fromPersisted(payload)?.let(::LaunchCamera) ?: DoNothing
            AdjustVolume.ID -> VolumeDirection.fromPersisted(payload)?.let(::AdjustVolume) ?: DoNothing
            else -> DoNothing
        }

        /** The optional payload to persist alongside a [KeyAction]'s id, or null if it has none. */
        fun payloadOf(action: KeyAction): String? = when (action) {
            is LaunchApp -> action.packageName
            is AdjustVolume -> action.direction.persisted
            is LaunchCamera -> action.mode.persisted
            else -> null
        }
    }
}

/** Which way [KeyAction.AdjustVolume] moves the volume. */
enum class VolumeDirection(val persisted: String) {
    UP("up"),
    DOWN("down");

    companion object {
        fun fromPersisted(value: String?): VolumeDirection? =
            entries.firstOrNull { direction -> direction.persisted == value }
    }
}

/** What [KeyAction.LaunchCamera] opens the camera ready to do. */
enum class CameraMode(val persisted: String) {
    PHOTO("photo"),
    VIDEO("video");

    companion object {
        fun fromPersisted(value: String?): CameraMode? =
            entries.firstOrNull { mode -> mode.persisted == value }
    }
}
