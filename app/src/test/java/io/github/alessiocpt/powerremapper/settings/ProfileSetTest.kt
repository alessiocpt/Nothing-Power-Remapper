package io.github.alessiocpt.powerremapper.settings

import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CAMERA = "com.nothing.camera"
private const val TELEGRAM = "org.telegram.messenger"

class ProfileSetTest {

    private val default = GestureActionMap.EMPTY
        .with(KeyGesture.SINGLE_PRESS, KeyAction.TakeScreenshot)
        .with(KeyGesture.DOUBLE_PRESS, KeyAction.ToggleFlashlight)

    private fun cameraProfile(fallback: Fallback) = Profile.newFor(CAMERA)
        .withFallback(fallback)
        .withSlot(KeyGesture.SINGLE_PRESS, GestureSlot.Assigned(KeyAction.MediaPlayPause))

    @Test
    fun aNewProfileInheritsWithEveryGestureUnassigned() {
        val profile = Profile.newFor(CAMERA)

        assertEquals(Fallback.INHERIT, profile.fallback)
        for (gesture in KeyGesture.entries) {
            assertEquals(GestureSlot.Unassigned, profile.slotFor(gesture))
        }
    }

    @Test
    fun withNoForegroundAppTheDefaultProfileApplies() {
        val set = ProfileSet(default).withProfile(cameraProfile(Fallback.ISOLATED))

        assertEquals(KeyAction.TakeScreenshot, set.resolve(null, KeyGesture.SINGLE_PRESS))
        assertEquals(KeyAction.ToggleFlashlight, set.resolve(null, KeyGesture.DOUBLE_PRESS))
    }

    @Test
    fun anAppWithoutAProfileUsesTheDefaultProfile() {
        val set = ProfileSet(default).withProfile(cameraProfile(Fallback.ISOLATED))

        assertEquals(KeyAction.TakeScreenshot, set.resolve(TELEGRAM, KeyGesture.SINGLE_PRESS))
    }

    @Test
    fun anAssignedSlotOverridesTheDefault() {
        val set = ProfileSet(default).withProfile(cameraProfile(Fallback.INHERIT))

        assertEquals(KeyAction.MediaPlayPause, set.resolve(CAMERA, KeyGesture.SINGLE_PRESS))
    }

    @Test
    fun anInheritingProfileUsesTheDefaultForUnassignedSlots() {
        val set = ProfileSet(default).withProfile(cameraProfile(Fallback.INHERIT))

        assertEquals(KeyAction.ToggleFlashlight, set.resolve(CAMERA, KeyGesture.DOUBLE_PRESS))
    }

    @Test
    fun anIsolatedProfileDoesNothingForUnassignedSlots() {
        val set = ProfileSet(default).withProfile(cameraProfile(Fallback.ISOLATED))

        assertEquals(KeyAction.DoNothing, set.resolve(CAMERA, KeyGesture.DOUBLE_PRESS))
    }

    @Test
    fun anAssignedDoNothingBlocksTheDefaultEvenWhenInheriting() {
        val profile = cameraProfile(Fallback.INHERIT)
            .withSlot(KeyGesture.DOUBLE_PRESS, GestureSlot.Assigned(KeyAction.DoNothing))
        val set = ProfileSet(default).withProfile(profile)

        assertEquals(KeyAction.DoNothing, set.resolve(CAMERA, KeyGesture.DOUBLE_PRESS))
    }

    @Test
    fun clearingASlotMakesItUnassignedAgain() {
        val profile = cameraProfile(Fallback.INHERIT)
            .withSlot(KeyGesture.SINGLE_PRESS, GestureSlot.Unassigned)

        assertEquals(GestureSlot.Unassigned, profile.slotFor(KeyGesture.SINGLE_PRESS))
        assertEquals(
            KeyAction.TakeScreenshot,
            ProfileSet(default).withProfile(profile).resolve(CAMERA, KeyGesture.SINGLE_PRESS)
        )
    }

    @Test
    fun activeGesturesFollowTheResolvedActions() {
        val inheriting = ProfileSet(default).withProfile(cameraProfile(Fallback.INHERIT))
        val isolated = ProfileSet(default).withProfile(cameraProfile(Fallback.ISOLATED))

        assertEquals(
            setOf(KeyGesture.SINGLE_PRESS, KeyGesture.DOUBLE_PRESS),
            inheriting.activeGestures(CAMERA)
        )
        assertEquals(setOf(KeyGesture.SINGLE_PRESS), isolated.activeGestures(CAMERA))
        assertEquals(default.activeGestures(), isolated.activeGestures(TELEGRAM))
    }

    @Test
    fun aSecondProfileForTheSameAppReplacesTheFirst() {
        val set = ProfileSet(default)
            .withProfile(cameraProfile(Fallback.INHERIT))
            .withProfile(Profile.newFor(CAMERA).withFallback(Fallback.ISOLATED))

        assertEquals(1, set.appProfiles.size)
        assertEquals(KeyAction.DoNothing, set.resolve(CAMERA, KeyGesture.SINGLE_PRESS))
    }

    @Test
    fun removingAProfileFallsBackToTheDefault() {
        val set = ProfileSet(default)
            .withProfile(cameraProfile(Fallback.ISOLATED))
            .withoutProfile(CAMERA)

        assertNull(set.profileFor(CAMERA))
        assertTrue(set.appProfiles.isEmpty())
        assertEquals(KeyAction.ToggleFlashlight, set.resolve(CAMERA, KeyGesture.DOUBLE_PRESS))
    }

    @Test
    fun appProfilesAreListedInPackageOrder() {
        val set = ProfileSet(default)
            .withProfile(Profile.newFor(TELEGRAM))
            .withProfile(Profile.newFor(CAMERA))

        assertEquals(listOf(CAMERA, TELEGRAM), set.appProfiles.map { it.packageName })
    }
}
