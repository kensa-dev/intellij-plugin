package dev.kensa.plugin.intellij.action

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
class OpenKensaTestActionTest {

    private val projectFixture = projectFixture()

    @Test
    fun `update thread is BGT`() {
        assertEquals(ActionUpdateThread.BGT, OpenKensaTestAction().actionUpdateThread)
    }

    @Test
    fun `disabled when no test proxy is selected`() {
        val project = projectFixture.get()
        val action = OpenKensaTestAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `disabled when proxy locationUrl is unsupported scheme`() {
        val project = projectFixture.get()
        val action = OpenKensaTestAction()

        val proxy = SMTestProxy("foo", false, "gradle:test://com.example.Foo/foo")
        proxy.setStarted(); proxy.setFinished()

        val testProxyKey = DataKey.create<SMTestProxy>("testProxy")
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(testProxyKey, proxy)
            .build()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)

        // gradle:test:// is rejected at the URL-prefix check, no PSI lookup happens.
        assertFalse(event.presentation.isEnabledAndVisible)
    }
}
