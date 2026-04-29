package dev.kensa.plugin.intellij.intention

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
class KensaIntentionActionTest {

    @Test
    fun `local report intention metadata is stable`() {
        val action = KensaOpenLocalReportIntentionAction()
        assertEquals("Open local Kensa report", action.text)
        assertEquals("Kensa", action.familyName)
        assertFalse(action.startInWriteAction())
    }

    @Test
    fun `ci report intention metadata is stable`() {
        val action = KensaOpenCiReportIntentionAction()
        assertEquals("Open CI Kensa report", action.text)
        assertEquals("Kensa", action.familyName)
        assertFalse(action.startInWriteAction())
    }

    @Test
    fun `intention group exposes Kensa family name`() {
        val group = KensaReportIntentionGroup()
        assertEquals("Kensa", group.familyName)
    }
}
