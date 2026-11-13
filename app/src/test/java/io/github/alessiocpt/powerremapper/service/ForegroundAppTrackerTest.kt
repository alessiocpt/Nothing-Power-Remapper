package io.github.alessiocpt.powerremapper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWN_PACKAGE = "io.github.alessiocpt.powerremapper"
private const val CAMERA = "com.nothing.camera"
private const val TELEGRAM = "org.telegram.messenger"
private const val KEYBOARD = "com.google.android.inputmethod.latin"

class ForegroundAppTrackerTest {

    private val tracker = ForegroundAppTracker(ownPackage = OWN_PACKAGE)

    private fun activity(packageName: String?, imePackage: String? = KEYBOARD) =
        tracker.onWindowStateChanged(packageName, isActivity = true, currentImePackage = imePackage)

    @Test
    fun startsWithNoForegroundApp() {
        assertNull(tracker.foregroundApp)
    }

    @Test
    fun anActivityOfAnotherAppBecomesTheForegroundApp() {
        assertTrue(activity(CAMERA))
        assertEquals(CAMERA, tracker.foregroundApp)
    }

    @Test
    fun switchingAppsUpdatesTheForegroundApp() {
        activity(CAMERA)
        activity(TELEGRAM)
        assertEquals(TELEGRAM, tracker.foregroundApp)
    }

    @Test
    fun theSameAppAgainReportsNoChange() {
        activity(CAMERA)
        assertFalse(activity(CAMERA))
    }

    @Test
    fun theNotificationShadeKeepsTheLastRealApp() {
        activity(CAMERA)
        assertFalse(activity(SYSTEM_UI_PACKAGE))
        assertEquals(CAMERA, tracker.foregroundApp)
    }

    @Test
    fun theActiveKeyboardKeepsTheLastRealApp() {
        activity(TELEGRAM)
        assertFalse(activity(KEYBOARD))
        assertEquals(TELEGRAM, tracker.foregroundApp)
    }

    @Test
    fun thisAppKeepsTheLastRealApp() {
        activity(CAMERA)
        assertFalse(activity(OWN_PACKAGE))
        assertEquals(CAMERA, tracker.foregroundApp)
    }

    @Test
    fun aWindowThatIsNotAnActivityKeepsTheLastRealApp() {
        activity(CAMERA)
        // A dialog, popup or toast another app shows over the camera.
        assertFalse(tracker.onWindowStateChanged(TELEGRAM, isActivity = false, currentImePackage = KEYBOARD))
        assertEquals(CAMERA, tracker.foregroundApp)
    }

    @Test
    fun aMissingPackageKeepsTheLastRealApp() {
        activity(CAMERA)
        assertFalse(activity(null))
        assertFalse(activity(""))
        assertEquals(CAMERA, tracker.foregroundApp)
    }

    @Test
    fun withNoKeyboardKnownAnyOtherAppStillCounts() {
        assertTrue(activity(CAMERA, imePackage = null))
        assertEquals(CAMERA, tracker.foregroundApp)
    }
}
