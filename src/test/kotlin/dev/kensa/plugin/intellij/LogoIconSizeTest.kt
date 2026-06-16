package dev.kensa.plugin.intellij

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogoIconSizeTest {

    @Test
    fun `logo icon used on toolbar and menus declares an action-sized intrinsic size`() {
        // /icons/logo.svg is the icon for the run-tab toolbar action, the test-tree menu,
        // the Tools menu action and the intention. IntelliJ sizes an ActionButton from the
        // icon's intrinsic width/height, so an oversized declaration (e.g. 512x512) swells
        // the button to fill the whole toolbar and turns its entire area into one click
        // target. The intrinsic size must stay in the normal action-icon range.
        val svg = LogoIconSizeTest::class.java.getResource("/icons/logo.svg")?.readText()
        assertNotNull(svg, "/icons/logo.svg not found on the classpath")

        val width = Regex("""\bwidth="(\d+)"""").find(svg!!)?.groupValues?.get(1)?.toInt()
        val height = Regex("""\bheight="(\d+)"""").find(svg)?.groupValues?.get(1)?.toInt()

        assertNotNull(width, "logo.svg must declare a width")
        assertNotNull(height, "logo.svg must declare a height")
        assertTrue(width!! in 1..40, "logo.svg width $width too large for a toolbar/menu icon")
        assertTrue(height!! in 1..40, "logo.svg height $height too large for a toolbar/menu icon")
    }
}
