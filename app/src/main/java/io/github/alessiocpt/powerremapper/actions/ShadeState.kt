package io.github.alessiocpt.powerremapper.actions

/** The two facts about an on-screen window that decide whether it is the notification shade. */
data class WindowSnapshot(val type: Int, val heightPx: Int)

/**
 * Pure check for an open notification shade, from the window list an accessibility service sees
 * without reading any window content. The shade, partly or fully pulled down, is a system window
 * taller than the status and navigation bars ever are; anything at least a third of the screen
 * qualifies.
 */
object ShadeState {

    private const val SHADE_MIN_SCREEN_FRACTION = 3

    fun isShadeOpen(windows: List<WindowSnapshot>, screenHeightPx: Int, systemWindowType: Int): Boolean =
        windows.any { window ->
            window.type == systemWindowType &&
                window.heightPx * SHADE_MIN_SCREEN_FRACTION >= screenHeightPx
        }
}
