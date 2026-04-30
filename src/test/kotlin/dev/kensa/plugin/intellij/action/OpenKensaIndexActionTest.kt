package dev.kensa.plugin.intellij.action

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.TestStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JLabel

@TestApplication
class OpenKensaIndexActionTest {

    private val projectFixture = projectFixture()
    private val disposableFixture = disposableFixture()

    @BeforeEach
    fun resetService() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.allIndexPaths().toList().forEach(results::clearForIndexHtml)
        results.latestIndexPath = null
    }

    @Test
    fun `hidden when no descriptor is selected even if a Kensa report exists`() {
        val project = projectFixture.get()
        // A Kensa report has been loaded before.
        project.service<KensaTestResultsService>().updateFromIndex(
            "com.example.X",
            null,
            "/p/index.html",
            mapOf("m" to TestStatus.PASSED),
        )

        val action = OpenKensaIndexAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)

        // Without a descriptor in the data context the action must stay hidden.
        assertFalse(event.presentation.isVisible)
    }

    @Test
    fun `hidden for non-Kensa run tab even when latestIndexPath is set`() {
        val project = projectFixture.get()
        project.service<KensaTestResultsService>().updateFromIndex(
            "com.example.X",
            null,
            "/p/index.html",
            mapOf("m" to TestStatus.PASSED),
        )

        // The descriptor was never tagged in the registry: this tab is NOT a Kensa run.
        val descriptor = newDescriptor()

        val action = OpenKensaIndexAction()
        val event = eventFor(action, descriptor)
        action.update(event)

        assertFalse(event.presentation.isVisible)
    }

    @Test
    fun `visible when descriptor is tagged in registry with an index path`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.YesKensa",
            null,
            "/p/index.html",
            mapOf("m" to TestStatus.PASSED),
        )

        val descriptor = newDescriptor()
        project.service<KensaRunTabRegistry>().recordClass(descriptor, "com.example.YesKensa")

        val action = OpenKensaIndexAction()
        val event = eventFor(action, descriptor)
        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    @Test
    fun `hidden when descriptor is tagged but service has no entry`() {
        val project = projectFixture.get()
        val descriptor = newDescriptor()
        project.service<KensaRunTabRegistry>().recordClass(descriptor, "com.example.OrphanKensa")

        val action = OpenKensaIndexAction()
        val event = eventFor(action, descriptor)
        action.update(event)

        assertFalse(event.presentation.isVisible)
    }

    private fun newDescriptor(): RunContentDescriptor {
        val d = RunContentDescriptor(null, null, JLabel(), "test")
        Disposer.register(disposableFixture.get(), d)
        return d
    }

    private fun eventFor(action: OpenKensaIndexAction, descriptor: RunContentDescriptor) =
        TestActionEvent.createTestEvent(
            action,
            SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, projectFixture.get())
                .add(OpenKensaIndexAction.RUN_CONTENT_DESCRIPTOR, descriptor)
                .build(),
        )
}
