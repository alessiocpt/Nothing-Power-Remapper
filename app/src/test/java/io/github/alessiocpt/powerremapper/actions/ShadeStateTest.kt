package io.github.alessiocpt.powerremapper.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN_HEIGHT = 2800
private const val APP = 1
private const val SYSTEM = 3

class ShadeStateTest {

    private fun window(type: Int, height: Int) = WindowSnapshot(type = type, heightPx = height)

    @Test
    fun anAppWithStatusAndNavigationBarsIsNotAnOpenShade() {
        val windows = listOf(window(APP, SCREEN_HEIGHT), window(SYSTEM, 120), window(SYSTEM, 140))

        assertFalse(ShadeState.isShadeOpen(windows, SCREEN_HEIGHT, systemWindowType = SYSTEM))
    }

    @Test
    fun aSystemWindowCoveringMostOfTheScreenIsAnOpenShade() {
        val windows = listOf(window(APP, SCREEN_HEIGHT), window(SYSTEM, SCREEN_HEIGHT))

        assertTrue(ShadeState.isShadeOpen(windows, SCREEN_HEIGHT, systemWindowType = SYSTEM))
    }

    @Test
    fun aPartlyPulledShadeCountsAsOpen() {
        val windows = listOf(window(SYSTEM, SCREEN_HEIGHT / 2))

        assertTrue(ShadeState.isShadeOpen(windows, SCREEN_HEIGHT, systemWindowType = SYSTEM))
    }

    @Test
    fun aLargeAppWindowIsNotTheShade() {
        assertFalse(
            ShadeState.isShadeOpen(listOf(window(APP, SCREEN_HEIGHT)), SCREEN_HEIGHT, systemWindowType = SYSTEM)
        )
    }

    @Test
    fun noWindowsMeansNoShade() {
        assertFalse(ShadeState.isShadeOpen(emptyList(), SCREEN_HEIGHT, systemWindowType = SYSTEM))
    }
}
