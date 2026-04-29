package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

@TestApplication
class KensaIndexLoaderTest {

    private val projectFixture = projectFixture()

    @Test
    fun `scan walks filesystem and loads indices without VFS`() {
        val project = projectFixture.get()
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

    @Test
    fun `loads even when index html not yet present`() {
        val project = projectFixture.get()
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

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indicesJson)
            ?: error("indices.json not visible to VFS")

        KensaIndexLoader.loadFromFile(project, vFile)

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.FirstRun", "sole"))
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.FirstRun"))
        val expectedIndexHtml = File(tempDir, "index.html").absolutePath
        assertEquals(expectedIndexHtml, results.getIndexPath("com.example.FirstRun"))
    }

    @Test
    fun `file overload loads via plain IO`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-loader-file").toFile()
        val indicesJson = File(tempDir, "indices.json").apply {
            writeText(
                """
                {"indices":[{"testClass":"com.example.IO","state":"Passed",
                "children":[{"testMethod":"x","state":"Passed"}]}]}
                """.trimIndent()
            )
        }

        KensaIndexLoader.loadFromFile(project, indicesJson)

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.IO", "x"))
    }

    @Test
    fun `failed and ignored states map correctly`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-states").toFile()
        File(tempDir, "indices.json").also { f ->
            f.writeText(
                """
                {
                  "indices": [
                    {
                      "testClass": "com.example.States",
                      "state": "Failed",
                      "children": [
                        { "testMethod": "ok", "state": "Passed" },
                        { "testMethod": "bad", "state": "Failed" },
                        { "testMethod": "skip", "state": "Skipped" },
                        { "testMethod": "off", "state": "Disabled" },
                        { "testMethod": "ign", "state": "Ignored" }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
            KensaIndexLoader.loadFromFile(project, f)
        }

        val r = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.FAILED, r.getClassStatus("com.example.States"))
        assertEquals(TestStatus.PASSED, r.getMethodStatus("com.example.States", "ok"))
        assertEquals(TestStatus.FAILED, r.getMethodStatus("com.example.States", "bad"))
        assertEquals(TestStatus.IGNORED, r.getMethodStatus("com.example.States", "skip"))
        assertEquals(TestStatus.IGNORED, r.getMethodStatus("com.example.States", "off"))
        assertEquals(TestStatus.IGNORED, r.getMethodStatus("com.example.States", "ign"))
    }

    @Test
    fun `unknown state is dropped not errored`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-unknown").toFile()
        File(tempDir, "indices.json").also { f ->
            f.writeText(
                """
                {"indices":[{"testClass":"com.example.U","state":"Quantum",
                "children":[{"testMethod":"m","state":"Maybe"}]}]}
                """.trimIndent()
            )
            KensaIndexLoader.loadFromFile(project, f)
        }

        val r = project.service<KensaTestResultsService>()
        assertNull(r.getClassStatus("com.example.U"))
        assertNull(r.getMethodStatus("com.example.U", "m"))
    }

    @Test
    fun `malformed JSON is logged and swallowed`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-malformed").toFile()
        File(tempDir, "indices.json").also { f ->
            f.writeText("{not valid json")
            KensaIndexLoader.loadFromFile(project, f)
        }

        val expectedIndexHtml = File(tempDir, "index.html").absolutePath
        val r = project.service<KensaTestResultsService>()
        assertFalse(expectedIndexHtml in r.allIndexPaths())
    }

    @Test
    fun `reloading same index clears stale classes no longer in JSON`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-stale").toFile()
        val indicesJson = File(tempDir, "indices.json")

        indicesJson.writeText(
            """
            {"indices":[
              {"testClass":"com.example.Stays","state":"Passed",
               "children":[{"testMethod":"a","state":"Passed"}]},
              {"testClass":"com.example.Removed","state":"Passed",
               "children":[{"testMethod":"b","state":"Passed"}]}
            ]}
            """.trimIndent()
        )
        KensaIndexLoader.loadFromFile(project, indicesJson)

        val r = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, r.getClassStatus("com.example.Stays"))
        assertEquals(TestStatus.PASSED, r.getClassStatus("com.example.Removed"))

        indicesJson.writeText(
            """
            {"indices":[
              {"testClass":"com.example.Stays","state":"Passed",
               "children":[{"testMethod":"a","state":"Passed"}]}
            ]}
            """.trimIndent()
        )
        KensaIndexLoader.loadFromFile(project, indicesJson)

        assertEquals(TestStatus.PASSED, r.getClassStatus("com.example.Stays"))
        assertNull(r.getClassStatus("com.example.Removed"))
        assertNull(r.getMethodStatus("com.example.Removed", "b"))
    }
}
