package io.github.alessiocpt.powerremapper.unlock

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandTest {

    @Test
    fun disabling_usesTheReversibleDisableUser() {
        val command = packageToggleCommand("com.nothing.ntessentialspace", enabled = false, userId = 0)

        assertEquals(
            listOf("pm", "disable-user", "--user", "0", "com.nothing.ntessentialspace"),
            command
        )
    }

    @Test
    fun enabling_targetsTheGivenUser() {
        val command = packageToggleCommand("com.nothing.ntessentialspace", enabled = true, userId = 10)

        assertEquals(
            listOf("pm", "enable", "--user", "10", "com.nothing.ntessentialspace"),
            command
        )
    }

    @Test
    fun theCameraKeyIsInjectedWithInput() {
        assertEquals(
            listOf("input", "keyevent", KeyEvent.KEYCODE_CAMERA.toString()),
            keyInjectionCommand(KeyEvent.KEYCODE_CAMERA)
        )
    }

    @Test
    fun keysOutsideTheAllowlistAreRefused() {
        // The service runs with shell privileges, so it never sends an arbitrary key.
        assertNull(keyInjectionCommand(KeyEvent.KEYCODE_POWER))
        assertNull(keyInjectionCommand(KeyEvent.KEYCODE_HOME))
        assertNull(keyInjectionCommand(KeyEvent.KEYCODE_VOLUME_DOWN))
    }
}
