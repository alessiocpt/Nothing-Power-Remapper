package io.github.alessiocpt.powerremapper.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSourceTest {

    @Test
    fun `preview package always follows GitHub pre-releases`() {
        assertEquals(
            UpdateSource.GITHUB_PREVIEW,
            chooseUpdateSource(
                packageName = "io.github.alessiocpt.powerremapper.preview",
                installingPackageName = "com.android.vending"
            )
        )
    }

    @Test
    fun `Play installed production package uses Google Play`() {
        assertEquals(
            UpdateSource.PLAY_STORE,
            chooseUpdateSource(
                packageName = "io.github.alessiocpt.powerremapper",
                installingPackageName = "com.android.vending"
            )
        )
    }

    @Test
    fun `sideloaded production package follows stable GitHub releases`() {
        assertEquals(
            UpdateSource.GITHUB_STABLE,
            chooseUpdateSource(
                packageName = "io.github.alessiocpt.powerremapper",
                installingPackageName = null
            )
        )
    }
}
