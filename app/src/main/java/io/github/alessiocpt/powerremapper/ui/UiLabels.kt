package io.github.alessiocpt.powerremapper.ui

import io.github.alessiocpt.powerremapper.R
import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.actions.VolumeDirection
import io.github.alessiocpt.powerremapper.core.KeyGesture

/**
 * Single source of truth mapping domain values to their string resources, so screens never
 * hard-code a `when` over gestures/actions more than once (DRY).
 */
object UiLabels {

    fun gestureLabelRes(gesture: KeyGesture): Int = when (gesture) {
        KeyGesture.SINGLE_PRESS -> R.string.gesture_single_press
        KeyGesture.DOUBLE_PRESS -> R.string.gesture_double_press
        KeyGesture.TRIPLE_PRESS -> R.string.gesture_triple_press
        KeyGesture.LONG_PRESS -> R.string.gesture_long_press
    }

    /**
     * The label resource for a built-in action. [KeyAction.LaunchApp] is deliberately omitted:
     * a launch action shows the resolved app name, which is not a static resource.
     */
    fun builtInActionLabelRes(action: KeyAction): Int = when (action) {
        is KeyAction.AdjustVolume -> when (action.direction) {
            VolumeDirection.UP -> R.string.action_volume_up
            VolumeDirection.DOWN -> R.string.action_volume_down
        }
        else -> labelResForId(action.id)
    }

    private fun labelResForId(actionId: String): Int = when (actionId) {
        KeyAction.DoNothing.ID -> R.string.action_none
        KeyAction.ToggleFlashlight.ID -> R.string.action_toggle_flashlight
        KeyAction.TakeScreenshot.ID -> R.string.action_take_screenshot
        KeyAction.LockScreen.ID -> R.string.action_lock_screen
        KeyAction.MediaPlayPause.ID -> R.string.action_media_play_pause
        KeyAction.MediaNext.ID -> R.string.action_media_next
        KeyAction.MediaPrevious.ID -> R.string.action_media_previous
        KeyAction.RingerCycle.ID -> R.string.action_ringer_cycle
        KeyAction.ToggleGlyphLight.ID -> R.string.action_toggle_glyph_light
        KeyAction.GoHome.ID -> R.string.action_go_home
        KeyAction.GoBack.ID -> R.string.action_go_back
        KeyAction.ShowRecents.ID -> R.string.action_show_recents
        KeyAction.ShowPowerMenu.ID -> R.string.action_show_power_menu
        KeyAction.ToggleSplitScreen.ID -> R.string.action_toggle_split_screen
        else -> R.string.action_none
    }

    /**
     * The built-in (non-app) actions offered in the picker, in display order. Device-specific
     * filtering (e.g. [KeyAction.ToggleGlyphLight] needing Glyph Matrix hardware) is the picker
     * screen's job, not this list's: this is a static mapping of domain values to labels, not a
     * capability check.
     */
    val builtInActions: List<KeyAction> = listOf(
        KeyAction.DoNothing,
        KeyAction.ToggleFlashlight,
        KeyAction.TakeScreenshot,
        KeyAction.LockScreen,
        KeyAction.MediaPlayPause,
        KeyAction.MediaNext,
        KeyAction.MediaPrevious,
        KeyAction.RingerCycle,
        KeyAction.AdjustVolume(VolumeDirection.UP),
        KeyAction.AdjustVolume(VolumeDirection.DOWN),
        KeyAction.GoHome,
        KeyAction.GoBack,
        KeyAction.ShowRecents,
        KeyAction.ShowPowerMenu,
        KeyAction.ToggleSplitScreen,
        KeyAction.ToggleGlyphLight
    )
}
