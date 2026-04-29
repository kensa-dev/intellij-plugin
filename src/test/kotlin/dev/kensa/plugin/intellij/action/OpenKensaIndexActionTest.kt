package dev.kensa.plugin.intellij.action

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.TestStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class OpenKensaIndexActionTest {

    private val projectFixture = projectFixture()

    @BeforeEach
    fun resetService() {
        // Light-fixture project services persist across tests; reset before each.
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.allIndexPaths().toList().forEach(results::clearForIndexHtml)
        results.latestIndexPath = null
    }

    @Test
    fun `hidden when no report ever loaded`() {
        val action = OpenKensaIndexAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(projectFixture.get()))
        action.update(event)
        assertFalse(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
    }

    @Test
    fun `visible after any report loaded`() {
        projectFixture.get().service<KensaTestResultsService>().updateFromIndex(
            "com.example.X",
            null,
            "/p/index.html",
            mapOf("m" to TestStatus.PASSED),
        )

        val action = OpenKensaIndexAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(projectFixture.get()))
        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    @Test
    fun `hidden again after report pruned`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.X",
            null,
            "/no-such-path/index.html",
            mapOf("m" to TestStatus.PASSED),
        )
        results.pruneMissingFiles()

        val action = OpenKensaIndexAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(projectFixture.get()))
        action.update(event)

        assertFalse(event.presentation.isVisible)
    }
}
