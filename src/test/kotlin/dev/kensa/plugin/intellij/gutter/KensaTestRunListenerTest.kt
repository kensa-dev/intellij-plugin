package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class KensaTestRunListenerTest {

    private val projectFixture = projectFixture()

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
    fun `onTestingFinished scans filesystem for indices`() {
        val project = projectFixture.get()
        val basePath = project.basePath ?: error("project has no basePath")
        val outputDir = File(basePath, "module-x/build/kensa-output").apply { mkdirs() }
        File(outputDir, "indices.json").writeText(
            """
            {
              "indices": [
                {
                  "testClass": "com.example.FreshlyRun",
                  "state": "Passed",
                  "children": [
                    { "testMethod": "solo", "state": "Passed" }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val listener = KensaTestRunListener(project)
        listener.onTestingFinished(SMRootTestProxy())

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.FreshlyRun", "solo"))
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
}
