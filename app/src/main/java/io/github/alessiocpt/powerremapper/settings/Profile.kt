package io.github.alessiocpt.powerremapper.settings

import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture

/** What a Profile says about one Gesture: left open, or assigned an Action (Do Nothing included). */
sealed interface GestureSlot {
    /** Left open; resolved through the Profile's [Fallback]. */
    data object Unassigned : GestureSlot

    data class Assigned(val action: KeyAction) : GestureSlot
}

/** How a Profile resolves its Unassigned Gestures. */
enum class Fallback {
    /** Unassigned Gestures run the Default Profile's Action. */
    INHERIT,

    /** Unassigned Gestures Do Nothing, so presses the Profile did not plan for cannot misfire. */
    ISOLATED
}

/**
 * The Gesture-to-Action assignments that apply while one app is the Foreground App. A plain
 * immutable value with no Android dependency, like [GestureActionMap].
 */
data class Profile(
    val packageName: String,
    val fallback: Fallback,
    private val assignedActions: Map<KeyGesture, KeyAction>
) {
    fun slotFor(gesture: KeyGesture): GestureSlot =
        assignedActions[gesture]?.let(GestureSlot::Assigned) ?: GestureSlot.Unassigned

    fun withSlot(gesture: KeyGesture, slot: GestureSlot): Profile = copy(
        assignedActions = when (slot) {
            GestureSlot.Unassigned -> assignedActions - gesture
            is GestureSlot.Assigned -> assignedActions + (gesture to slot.action)
        }
    )

    fun withFallback(fallback: Fallback): Profile = copy(fallback = fallback)

    companion object {
        /** A new Profile inherits from the Default Profile and leaves every Gesture Unassigned. */
        fun newFor(packageName: String): Profile =
            Profile(packageName, Fallback.INHERIT, emptyMap())
    }
}

/**
 * Every Profile: the Default Profile plus at most one Profile per app. Resolving a Gesture against
 * the Foreground App is the single place that decides which Action runs.
 */
data class ProfileSet(
    val defaultProfile: GestureActionMap = GestureActionMap.EMPTY,
    private val profilesByPackage: Map<String, Profile> = emptyMap()
) {
    /** The app Profiles, ordered by package name so lists stay stable. */
    val appProfiles: List<Profile>
        get() = profilesByPackage.values.sortedBy(Profile::packageName)

    fun profileFor(packageName: String?): Profile? =
        packageName?.let(profilesByPackage::get)

    /** The Action to run for [gesture] while [foregroundApp] is in front (null: none known). */
    fun resolve(foregroundApp: String?, gesture: KeyGesture): KeyAction {
        val profile = profileFor(foregroundApp) ?: return defaultProfile.actionFor(gesture)
        return when (val slot = profile.slotFor(gesture)) {
            is GestureSlot.Assigned -> slot.action
            GestureSlot.Unassigned -> when (profile.fallback) {
                Fallback.INHERIT -> defaultProfile.actionFor(gesture)
                Fallback.ISOLATED -> KeyAction.DoNothing
            }
        }
    }

    /** Gestures that would run something other than Do Nothing while [foregroundApp] is in front. */
    fun activeGestures(foregroundApp: String?): Set<KeyGesture> =
        KeyGesture.entries
            .filter { gesture -> resolve(foregroundApp, gesture) != KeyAction.DoNothing }
            .toSet()

    fun withDefaultProfile(defaultProfile: GestureActionMap): ProfileSet =
        copy(defaultProfile = defaultProfile)

    fun withProfile(profile: Profile): ProfileSet =
        copy(profilesByPackage = profilesByPackage + (profile.packageName to profile))

    fun withoutProfile(packageName: String): ProfileSet =
        copy(profilesByPackage = profilesByPackage - packageName)
}
