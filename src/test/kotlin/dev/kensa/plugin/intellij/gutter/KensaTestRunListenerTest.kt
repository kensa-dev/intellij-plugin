package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import javax.swing.JLabel

@TestApplication
class KensaTestRunListenerTest {

    private val projectFixture = projectFixture()
    private val disposableFixture = disposableFixture()

    private fun newDescriptor(): RunContentDescriptor {
        val d = RunContentDescriptor(null, null, JLabel(), "test")
        Disposer.register(disposableFixture.get(), d)
        return d
    }

    @Test
    fun `parametrised invocations collapse to single method`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val listener = KensaTestRunListener(project)

        val inv1 = SMTestProxy("theTest[1]", false, "java:test://com.example.MyTest/theTest[1]")
        inv1.setStarted(); inv1.setFinished()
        listener.onTestFinished(inv1)

        val inv2 = SMTestProxy("theTest[2]", false, "java:test://com.example.MyTest/theTest[2]")
        inv2.setStarted(); inv2.setFinished()
        listener.onTestFinished(inv2)

        val classSuite = SMTestProxy("com.example.MyTest", true, "java:test://com.example.MyTest")
        classSuite.setStarted(); classSuite.setFinished()
        listener.onSuiteFinished(classSuite)

        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.MyTest", "theTest"))
        assertNull(results.getMethodStatus("com.example.MyTest", "theTest[1]"))
        assertNull(results.getMethodStatus("com.example.MyTest", "theTest[2]"))
    }

    @Test
    fun `non-Kensa class is not recorded in the registry`() {
        val project = projectFixture.get()
        val registry = project.service<KensaRunTabRegistry>()
        val results = project.service<KensaTestResultsService>()
        // Pre-load the service with an entry that would otherwise make indexPathFor return non-null.
        results.updateFromIndex("com.example.Plain", null, "/p/index.html", emptyMap())

        val listener = KensaTestRunListener(project, isKensaTestClass = { false })

        val descriptor = newDescriptor()
        val root = SMRootTestProxy()
        root.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)
        val leaf = SMTestProxy("m", false, "java:test://com.example.Plain/m")
        root.addChild(leaf)
        leaf.setStarted(); leaf.setFinished()

        listener.onTestFinished(leaf)

        assertNull(registry.indexPathFor(descriptor))
        assertTrue(registry.classesFor(descriptor).isEmpty())
    }

    @Test
    fun `Kensa class IS recorded in the registry`() {
        val project = projectFixture.get()
        val registry = project.service<KensaRunTabRegistry>()
        val results = project.service<KensaTestResultsService>()
        results.updateFromIndex("com.example.MyKensaTest", null, "/p/index.html", emptyMap())

        val listener = KensaTestRunListener(project, isKensaTestClass = { it == "com.example.MyKensaTest" })

        val descriptor = newDescriptor()
        val root = SMRootTestProxy()
        root.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)
        val leaf = SMTestProxy("m", false, "java:test://com.example.MyKensaTest/m")
        root.addChild(leaf)
        leaf.setStarted(); leaf.setFinished()

        listener.onTestFinished(leaf)

        assertEquals("/p/index.html", registry.indexPathFor(descriptor))
        assertTrue("com.example.MyKensaTest" in registry.classesFor(descriptor))
    }

    @Test
    fun `onTestingFinished does NOT scan when no Kensa classes were recorded for the descriptor`() {
        val project = projectFixture.get()
        val basePath = project.basePath ?: error("project has no basePath")
        val outputDir = File(basePath, "module-no-kensa/build/kensa-output").apply { mkdirs() }
        File(outputDir, "indices.json").writeText(
            """
            {"indices":[{"testClass":"com.example.NoKensaRun","state":"Passed",
            "children":[{"testMethod":"x","state":"Passed"}]}]}
            """.trimIndent()
        )

        // No prior recordClass calls — descriptor was never tagged as Kensa.
        val listener = KensaTestRunListener(project, isKensaTestClass = { false })
        val testsRoot = SMRootTestProxy()
        val descriptor = newDescriptor()
        testsRoot.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)

        listener.onTestingFinished(testsRoot)

        // Service must NOT have been mutated by a stray scan.
        val results = project.service<KensaTestResultsService>()
        assertNull(results.getMethodStatus("com.example.NoKensaRun", "x"))
    }

    @Test
    fun `onTestingFinished DOES scan when Kensa classes were recorded for the descriptor`() {
        val project = projectFixture.get()
        val basePath = project.basePath ?: error("project has no basePath")
        val outputDir = File(basePath, "module-yes-kensa/build/kensa-output").apply { mkdirs() }
        File(outputDir, "indices.json").writeText(
            """
            {"indices":[{"testClass":"com.example.YesKensaRun","state":"Passed",
            "children":[{"testMethod":"y","state":"Passed"}]}]}
            """.trimIndent()
        )

        val listener = KensaTestRunListener(project, isKensaTestClass = { true })

        val descriptor = newDescriptor()
        val root = SMRootTestProxy()
        root.putUserData(KensaTestRunListener.DESCRIPTOR_KEY, descriptor)
        val leaf = SMTestProxy("y", false, "java:test://com.example.YesKensaRun/y")
        root.addChild(leaf)
        leaf.setStarted(); leaf.setFinished()
        listener.onTestFinished(leaf)

        listener.onTestingFinished(root)

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.YesKensaRun", "y"))
    }

    @Test
    fun `gradle test url prefix is currently rejected`() {
        // Pin current parseLocation behavior: only java:test:// URLs are recognised.
        // If/when the listener is broadened to accept gradle:test:// or java:testng://,
        // this test will fail and should be updated to assert recording.
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val listener = KensaTestRunListener(project)

        val gradleProxy = SMTestProxy("doStuff", false, "gradle:test://com.example.GradleRun/doStuff")
        gradleProxy.setStarted(); gradleProxy.setFinished()
        listener.onTestFinished(gradleProxy)

        val testNgProxy = SMTestProxy("ngThing", false, "java:testng://com.example.NgRun/ngThing")
        testNgProxy.setStarted(); testNgProxy.setFinished()
        listener.onTestFinished(testNgProxy)

        assertNull(results.getMethodStatus("com.example.GradleRun", "doStuff"))
        assertNull(results.getMethodStatus("com.example.NgRun", "ngThing"))
    }

    @Test
    fun `exposes a public single-arg Project constructor for platform listener instantiation`() {
        // The IntelliJ platform's ProjectImpl.findConstructorAndInstantiateClass requires
        // one of: (), (Project), (CoroutineScope), (Project, CoroutineScope).
        // Without @JvmOverloads on the primary constructor, Kotlin's default-value param
        // does not emit a (Project) bytecode-level constructor and the platform fails to
        // instantiate this lazy listener on build 261+ (IntelliJ 2026.1).
        val ctor = KensaTestRunListener::class.java.getDeclaredConstructor(Project::class.java)
        assertNotNull(ctor)
        assertTrue(Modifier.isPublic(ctor.modifiers), "constructor must be public for platform reflection")
    }
}
