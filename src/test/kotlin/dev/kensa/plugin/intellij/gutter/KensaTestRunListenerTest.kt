package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class KensaTestRunListenerTest : BasePlatformTestCase() {

    fun testParametrisedInvocationsCollapseToSingleMethod() {
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

    fun testOnTestingFinishedScansFilesystemForIndices() {
        val basePath = project.basePath ?: error("BasePlatformTestCase project has no basePath")
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
}
