package io.github.alessiocpt.powerremapper.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyActionTest {

    private fun roundTrip(action: KeyAction): KeyAction =
        KeyAction.fromPersisted(action.id, KeyAction.payloadOf(action))

    @Test
    fun systemNavigationActionsRoundTrip() {
        for (action in listOf(
            KeyAction.LaunchAssistant,
            KeyAction.ToggleNotifications,
            KeyAction.ToggleQuickSettings,
            KeyAction.GoHome,
            KeyAction.GoBack,
            KeyAction.ShowRecents,
            KeyAction.ShowPowerMenu
        )) {
            assertEquals(action, roundTrip(action))
        }
    }

    @Test
    fun aRemovedActionReadsBackAsDoNothing() {
        // Split screen was offered briefly; Nothing OS does not support it as a system action.
        assertEquals(KeyAction.DoNothing, KeyAction.fromPersisted("toggle_split_screen", null))
    }

    @Test
    fun adjustVolumeKeepsItsDirection() {
        for (direction in VolumeDirection.entries) {
            assertEquals(KeyAction.AdjustVolume(direction), roundTrip(KeyAction.AdjustVolume(direction)))
        }
    }

    @Test
    fun adjustVolumeWithoutAKnownDirectionDoesNothing() {
        assertEquals(KeyAction.DoNothing, KeyAction.fromPersisted(KeyAction.AdjustVolume.ID, null))
        assertEquals(KeyAction.DoNothing, KeyAction.fromPersisted(KeyAction.AdjustVolume.ID, "sideways"))
    }
}
