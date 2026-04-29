package dev.kensa.plugin.intellij.statusbar

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
class KensaStatusBarWidgetTest {

    private val projectFixture = projectFixture()

    @Test
    fun `widget ID matches factory ID`() {
        val widget = KensaStatusBarWidget(projectFixture.get())
        assertEquals(KensaStatusBarWidget.ID, widget.ID())
    }

    @Test
    fun `getComponent returns a JPanel`() {
        val widget = KensaStatusBarWidget(projectFixture.get())
        val component = widget.component
        assertNotNull(component)
        assertEquals(JPanel::class.java, component::class.java)
    }
}
