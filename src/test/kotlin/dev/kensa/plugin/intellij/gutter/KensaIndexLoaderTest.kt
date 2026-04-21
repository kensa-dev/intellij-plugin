package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class KensaIndexLoaderTest : BasePlatformTestCase() {

    fun testScanWalksFilesystemAndLoadsIndicesWithoutVfs() {
        val root = Files.createTempDirectory("kensa-scan-test").toFile()
        val moduleOutputDir = File(root, "some-module/build/kensa-output").apply { mkdirs() }
        File(moduleOutputDir, "indices.json").writeText(
            """
            {
              "indices": [
                {
                  "testClass": "com.example.ExternalRun",
                  "state": "Passed",
                  "children": [
                    { "testMethod": "sole", "state": "Passed" }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        KensaIndexLoader.scan(project, root, "kensa-output")

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.ExternalRun", "sole"))
    }

    fun testLoadsEvenWhenIndexHtmlNotYetPresent() {
        val tempDir = Files.createTempDirectory("kensa-loader-test").toFile()
        val indicesJson = File(tempDir, "indices.json").apply {
            writeText(
                """
                {
                  "indices": [
                    {
                      "testClass": "com.example.FirstRun",
                      "state": "Passed",
                      "children": [
                        { "testMethod": "sole", "state": "Passed" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        }
        // Deliberately do NOT create index.html — simulate VFS seeing indices.json first.

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indicesJson)
            ?: error("indices.json not visible to VFS")

        KensaIndexLoader.loadFromFile(project, vFile)

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.FirstRun", "sole"))
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.FirstRun"))
        val expectedIndexHtml = File(tempDir, "index.html").absolutePath
        assertEquals(expectedIndexHtml, results.getIndexPath("com.example.FirstRun"))
    }
}
