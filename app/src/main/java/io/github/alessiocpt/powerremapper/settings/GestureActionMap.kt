package io.github.alessiocpt.powerremapper.settings

import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture

/**
 * The full gesture -> action configuration: one [KeyAction] per [KeyGesture]. Missing entries
 * resolve to [KeyAction.DoNothing] so callers never deal with nullability.
 *
 * This is a plain immutable value with no Android or DataStore dependency, so the persistence
 * mapping in [SettingsSerialization] can be unit-tested in isolation.
 */
data class GestureActionMap(
    private val actionsByGesture: Map<KeyGesture, KeyAction> = emptyMap()
) {
    fun actionFor(gesture: KeyGesture): KeyAction =
        actionsByGesture[gesture] ?: KeyAction.DoNothing

    fun with(gesture: KeyGesture, action: KeyAction): GestureActionMap =
        GestureActionMap(actionsByGesture + (gesture to action))

    /** Whether [action] is bound to any gesture, so callers can gate action-specific UI on it. */
    fun isMapped(action: KeyAction): Boolean =
        KeyGesture.entries.any { gesture -> actionFor(gesture) == action }

    /** Gestures whose mapped action is anything other than [KeyAction.DoNothing]. */
    fun activeGestures(): Set<KeyGesture> =
        KeyGesture.entries
            .filter { gesture -> actionFor(gesture) != KeyAction.DoNothing }
            .toSet()

    companion object {
        val EMPTY = GestureActionMap()
    }
}
