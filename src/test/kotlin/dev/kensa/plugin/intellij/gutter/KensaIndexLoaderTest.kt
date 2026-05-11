package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `loads site-mode source bundle and points at site root index html`() {
        val project = projectFixture.get()
        val tempParent = Files.createTempDirectory("kensa-site-mode").toFile()
        val siteRoot = File(tempParent, "build/kensa-site").apply { mkdirs() }
        val sourceBundle = File(siteRoot, "sources/uiTest").apply { mkdirs() }
        File(sourceBundle, "indices.json").writeText(
            """{"indices":[{"testClass":"com.example.SiteOnly","state":"Passed",
            "children":[{"testMethod":"runs","state":"Passed"}]}]}"""
        )

        KensaIndexLoader.scan(project, tempParent, "kensa-output")

        val results = project.service<KensaTestResultsService>()
        val entry = results.getIndexEntry("com.example.SiteOnly", "uiTest")
            ?: error("expected entry for site source uiTest")
        assertEquals("uiTest", entry.sourceId)
        assertEquals(File(siteRoot, "index.html").absolutePath, entry.indexHtmlPath)
        assertEquals(sourceBundle.absolutePath, entry.bundleDir)
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.SiteOnly", "runs", "uiTest"))
    }

    @Test
    fun `same class in two source bundles stores both entries`() {
        val project = projectFixture.get()
        val tempParent = Files.createTempDirectory("kensa-multi").toFile()
        val siteRoot = File(tempParent, "build/kensa-site").apply { mkdirs() }
        val ui = File(siteRoot, "sources/uiTest").apply { mkdirs() }
        val acceptance = File(siteRoot, "sources/acceptanceTest").apply { mkdirs() }
        File(ui, "indices.json").writeText(
            """{"indices":[{"testClass":"com.example.Shared","state":"Passed",
            "children":[{"testMethod":"x","state":"Passed"}]}]}"""
        )
        File(acceptance, "indices.json").writeText(
            """{"indices":[{"testClass":"com.example.Shared","state":"Failed",
            "children":[{"testMethod":"x","state":"Failed"}]}]}"""
        )

        KensaIndexLoader.loadFromFile(project, File(ui, "indices.json"))
        KensaIndexLoader.loadFromFile(project, File(acceptance, "indices.json"))

        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.Shared", "uiTest"))
        assertEquals(TestStatus.FAILED, results.getClassStatus("com.example.Shared", "acceptanceTest"))
        assertEquals("uiTest", results.getIndexEntry("com.example.Shared", "uiTest")?.sourceId)
        assertEquals("acceptanceTest", results.getIndexEntry("com.example.Shared", "acceptanceTest")?.sourceId)
    }

    @Test
    fun `recognises site layout without manifest on disk`() {
        val project = projectFixture.get()
        val tempParent = Files.createTempDirectory("kensa-no-manifest").toFile()
        val siteRoot = File(tempParent, "build/kensa-site").apply { mkdirs() }
        val sourceBundle = File(siteRoot, "sources/uiTest").apply { mkdirs() }
        File(sourceBundle, "indices.json").writeText(
            """{"indices":[{"testClass":"com.example.NoManifest","state":"Passed",
            "children":[]}]}"""
        )

        KensaIndexLoader.loadFromFile(project, File(sourceBundle, "indices.json"))

        val results = project.service<KensaTestResultsService>()
        val entry = results.getIndexEntry("com.example.NoManifest", "uiTest")
            ?: error("expected site-source entry even without manifest.json on disk")
        assertEquals("uiTest", entry.sourceId)
        assertEquals(File(siteRoot, "index.html").absolutePath, entry.indexHtmlPath)
    }

    @Test
    fun `isKensaIndicesJson recognises both layouts`() {
        val singleRoot = Files.createTempDirectory("kensa-iso-single").toFile()
        val singleBundle = File(singleRoot, "build/kensa-output").apply { mkdirs() }
        val singleIndices = File(singleBundle, "indices.json").apply { writeText("{}") }

        val siteParent = Files.createTempDirectory("kensa-iso-site").toFile()
        val siteRoot = File(siteParent, "build/kensa-site").apply { mkdirs() }
        val sourceBundle = File(siteRoot, "sources/uiTest").apply { mkdirs() }
        val siteIndices = File(sourceBundle, "indices.json").apply { writeText("{}") }

        val unrelated = File(Files.createTempDirectory("kensa-iso-other").toFile(), "indices.json").apply { writeText("{}") }

        assertTrue(KensaIndexLoader.isKensaIndicesJson(singleIndices, "kensa-output"))
        assertTrue(KensaIndexLoader.isKensaIndicesJson(siteIndices, "kensa-output"))
        assertFalse(KensaIndexLoader.isKensaIndicesJson(unrelated, "kensa-output"))
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
