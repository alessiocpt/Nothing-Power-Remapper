package io.github.alessiocpt.powerremapper.settings

import io.github.alessiocpt.powerremapper.actions.KeyAction
import io.github.alessiocpt.powerremapper.core.KeyGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CAMERA = "com.nothing.camera"
private const val TELEGRAM = "org.telegram.messenger"

class ProfileSerializationTest {

    private fun roundTrip(profiles: List<Profile>): List<Profile> =
        ProfileSerialization.decodeProfiles(ProfileSerialization.encodeProfiles(profiles))

    @Test
    fun roundTripKeepsEverySlotStatePayloadAndFallback() {
        val camera = Profile.newFor(CAMERA)
            .withFallback(Fallback.ISOLATED)
            .withSlot(KeyGesture.SINGLE_PRESS, GestureSlot.Assigned(KeyAction.MediaPlayPause))
            .withSlot(KeyGesture.DOUBLE_PRESS, GestureSlot.Assigned(KeyAction.DoNothing))
            .withSlot(KeyGesture.LONG_PRESS, GestureSlot.Assigned(KeyAction.LaunchApp(TELEGRAM)))
        val telegram = Profile.newFor(TELEGRAM)

        assertEquals(listOf(camera, telegram), roundTrip(listOf(camera, telegram)))
        assertEquals(GestureSlot.Unassigned, roundTrip(listOf(camera))[0].slotFor(KeyGesture.TRIPLE_PRESS))
    }

    @Test
    fun noProfilesRoundTripsToNoProfiles() {
        assertTrue(roundTrip(emptyList()).isEmpty())
    }

    @Test
    fun missingOrCorruptJsonDecodesToNoProfiles() {
        assertTrue(ProfileSerialization.decodeProfiles(null).isEmpty())
        assertTrue(ProfileSerialization.decodeProfiles("").isEmpty())
        assertTrue(ProfileSerialization.decodeProfiles("{not json").isEmpty())
        assertTrue(ProfileSerialization.decodeProfiles("[]").isEmpty())
    }

    @Test
    fun anUnknownActionIdDecodesToDoNothing() {
        val json = """
            {"profiles":[{"package":"$CAMERA","fallback":"inherit",
              "slots":{"single_press":{"id":"from_a_future_version"}}}]}
        """.trimIndent()

        val profile = ProfileSerialization.decodeProfiles(json).single()

        assertEquals(GestureSlot.Assigned(KeyAction.DoNothing), profile.slotFor(KeyGesture.SINGLE_PRESS))
    }

    @Test
    fun unknownGesturesAndFallbacksAreTolerated() {
        val json = """
            {"profiles":[{"package":"$CAMERA","fallback":"sideways",
              "slots":{"quadruple_press":{"id":"lock_screen"},"long_press":{"id":"lock_screen"}}}]}
        """.trimIndent()

        val profile = ProfileSerialization.decodeProfiles(json).single()

        assertEquals(Fallback.INHERIT, profile.fallback)
        assertEquals(GestureSlot.Assigned(KeyAction.LockScreen), profile.slotFor(KeyGesture.LONG_PRESS))
    }

    @Test
    fun aMalformedProfileIsSkippedWithoutLosingTheOthers() {
        val json = """
            {"profiles":[{"fallback":"inherit"},{"package":"$TELEGRAM","fallback":"isolated"}]}
        """.trimIndent()

        val profiles = ProfileSerialization.decodeProfiles(json)

        assertEquals(listOf(Profile.newFor(TELEGRAM).withFallback(Fallback.ISOLATED)), profiles)
    }

    @Test
    fun profilesAreEncodedAsAJsonArray() {
        val json = ProfileSerialization.encodeProfiles(listOf(Profile.newFor(CAMERA)))

        assertTrue(json, json.startsWith("{\"profiles\":[{"))
    }
}
