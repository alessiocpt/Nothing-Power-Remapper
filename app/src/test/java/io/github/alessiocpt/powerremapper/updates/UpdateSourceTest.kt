package io.github.alessiocpt.powerremapper.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSourceTest {

    @Test
    fun `preview package follows GitHub pre-releases`() {
        assertEquals(
            UpdateSource.GITHUB_PREVIEW,
            chooseUpdateSource(packageName = "io.github.alessiocpt.powerremapper.preview")
        )
    }

    @Test
    fun `production package follows stable GitHub releases`() {
        assertEquals(
            UpdateSource.GITHUB_STABLE,
            chooseUpdateSource(packageName = "io.github.alessiocpt.powerremapper")
        )
    }
}
