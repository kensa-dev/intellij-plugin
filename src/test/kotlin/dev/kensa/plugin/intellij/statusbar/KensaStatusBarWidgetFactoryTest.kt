package dev.kensa.plugin.intellij.statusbar

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class KensaStatusBarWidgetFactoryTest {

    private val projectFixture = projectFixture()

    @Test
    fun `factory metadata`() {
        val factory = KensaStatusBarWidgetFactory()
        assertEquals("kensa.status", factory.id)
        assertEquals("Kensa", factory.displayName)
        assertTrue(factory.isAvailable(projectFixture.get()))
    }

    @Test
    fun `creates a widget bound to the project`() {
        val factory = KensaStatusBarWidgetFactory()
        val widget = factory.createWidget(projectFixture.get())
        assertEquals(KensaStatusBarWidget.ID, widget.ID())
    }
}
