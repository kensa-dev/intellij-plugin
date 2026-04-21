package dev.kensa.plugin.intellij.execution

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.KensaTestRunListener
import dev.kensa.plugin.intellij.gutter.TestStatus
import javax.swing.JLabel

class KensaRunTabRegistryTest : BasePlatformTestCase() {

    fun testDescriptorTaggedWhenIndicesAlreadyLoaded() {
        val results = project.service<KensaTestResultsService>()
        val registry = project.service<KensaRunTabRegistry>()
        val listener = KensaTestRunListener(project)

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

    fun testDescriptorTaggedRetroactivelyWhenIndicesArriveAfterRun() {
        val results = project.service<KensaTestResultsService>()
        val registry = project.service<KensaRunTabRegistry>()
        val listener = KensaTestRunListener(project)

        val descriptor = newDescriptor()
        val root = SMRootTestProxy()
        root.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)
        val leaf = SMTestProxy("method", false, "java:test://com.example.NewKensaTest/method")
        root.addChild(leaf)
        leaf.setStarted(); leaf.setFinished()

        // Run fires while indices.json does not yet exist for this class.
        listener.onTestFinished(leaf)
        assertNull(registry.indexPathFor(descriptor))

        // indices.json arrives post-run.
        results.updateFromIndex(
            "com.example.NewKensaTest",
            null,
            "/out/index.html",
            mapOf("method" to TestStatus.PASSED),
        )

        assertEquals("/out/index.html", registry.indexPathFor(descriptor))
    }

    private fun newDescriptor(): RunContentDescriptor {
        val descriptor = RunContentDescriptor(null, null, JLabel(), "test")
        Disposer.register(testRootDisposable, descriptor)
        return descriptor
    }
}
