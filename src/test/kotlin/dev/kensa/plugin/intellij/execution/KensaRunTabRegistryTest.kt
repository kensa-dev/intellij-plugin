package dev.kensa.plugin.intellij.execution

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.KensaTestRunListener
import dev.kensa.plugin.intellij.gutter.TestStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.swing.JLabel

@TestApplication
class KensaRunTabRegistryTest {

    private val projectFixture = projectFixture()
    private val disposableFixture = disposableFixture()

    @Test
    fun `descriptor tagged when indices already loaded`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val registry = project.service<KensaRunTabRegistry>()
        val listener = KensaTestRunListener(project, isKensaTestClass = { true })

        results.updateFromIndex(
            "com.example.MyTest",
            null,
            "/out/index.html",
            mapOf("method" to TestStatus.PASSED),
        )

        val descriptor = newDescriptor()
        val root = SMRootTestProxy()
        root.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)
        val leaf = SMTestProxy("method", false, "java:test://com.example.MyTest/method")
        root.addChild(leaf)
        leaf.setStarted(); leaf.setFinished()

        listener.onTestFinished(leaf)

        assertEquals("/out/index.html", registry.indexPathFor(descriptor))
    }

    @Test
    fun `descriptor tagged retroactively when indices arrive after run`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val registry = project.service<KensaRunTabRegistry>()
        val listener = KensaTestRunListener(project, isKensaTestClass = { true })

        val descriptor = newDescriptor()
        val root = SMRootTestProxy()
        root.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)
        val leaf = SMTestProxy("method", false, "java:test://com.example.NewKensaTest/method")
        root.addChild(leaf)
        leaf.setStarted(); leaf.setFinished()

        listener.onTestFinished(leaf)
        assertNull(registry.indexPathFor(descriptor))

        results.updateFromIndex(
            "com.example.NewKensaTest",
            null,
            "/out/index.html",
            mapOf("method" to TestStatus.PASSED),
        )

        assertEquals("/out/index.html", registry.indexPathFor(descriptor))
    }

    @Test
    fun `recordClass is no-op for already disposed descriptor`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val registry = project.service<KensaRunTabRegistry>()

        results.updateFromIndex(
            "com.example.Disposed",
            null,
            "/out/index.html",
            mapOf("m" to TestStatus.PASSED),
        )

        val descriptor = RunContentDescriptor(null, null, JLabel(), "disposed-before-record")
        Disposer.dispose(descriptor)

        registry.recordClass(descriptor, "com.example.Disposed")

        assertNull(registry.indexPathFor(descriptor))
    }

    private fun newDescriptor(): RunContentDescriptor {
        val descriptor = RunContentDescriptor(null, null, JLabel(), "test")
        Disposer.register(disposableFixture.get(), descriptor)
        return descriptor
    }
}
