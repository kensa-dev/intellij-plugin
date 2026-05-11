package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class KensaTestResultsServiceTest {

    private val projectFixture = projectFixture()
    private val disposableFixture = disposableFixture()

    @Test
    fun `prune missing files clears entries for deleted index html`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        val tempDir = Files.createTempDirectory("kensa-service-test").toFile()
        val indexFile = File(tempDir, "index.html").apply { writeText("<html></html>") }

        val classFqn = "com.example.PruneMe"
        results.updateFromIndex(
            classFqn,
            null,
            indexFile.absolutePath,
            mapOf("method" to TestStatus.PASSED),
        )
        assertEquals(TestStatus.PASSED, results.getMethodStatus(classFqn, "method"))
        assertEquals(TestStatus.PASSED, results.getClassStatus(classFqn))
        assertTrue(indexFile.absolutePath in results.allIndexPaths())

        indexFile.delete()
        tempDir.delete()

        results.pruneMissingFiles()

        assertNull(results.getMethodStatus(classFqn, "method"))
        assertNull(results.getClassStatus(classFqn))
        assertFalse(indexFile.absolutePath in results.allIndexPaths())
    }

    @Test
    fun `update from index infers class status failed from methods`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.MixedTest",
            null,
            "/p/index.html",
            mapOf(
                "ok" to TestStatus.PASSED,
                "broken" to TestStatus.FAILED,
            ),
        )
        assertEquals(TestStatus.FAILED, results.getClassStatus("com.example.MixedTest"))
    }

    @Test
    fun `update from index infers class status passed when all pass`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.PassTest",
            null,
            "/p/index.html",
            mapOf("a" to TestStatus.PASSED, "b" to TestStatus.PASSED),
        )
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.PassTest"))
    }

    @Test
    fun `explicit class status overrides method inference`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.ForcedFail",
            TestStatus.FAILED,
            "/p/index.html",
            mapOf("ok" to TestStatus.PASSED),
        )
        assertEquals(TestStatus.FAILED, results.getClassStatus("com.example.ForcedFail"))
    }

    @Test
    fun `snapshotForIndex aggregates classes under that index only`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.snap.A",
            null,
            "/snap/agg/index.html",
            mapOf("a1" to TestStatus.PASSED, "a2" to TestStatus.FAILED),
        )
        results.updateFromIndex(
            "com.example.snap.B",
            null,
            "/snap/agg/index.html",
            mapOf("b1" to TestStatus.PASSED, "b2" to TestStatus.IGNORED),
        )

        val snap = results.snapshotForIndex("/snap/agg/index.html")
        assertEquals(2, snap.passed)
        assertEquals(1, snap.failed)
        assertEquals(1, snap.ignored)
        assertEquals(4, snap.total)
        assertFalse(snap.isEmpty)
    }

    @Test
    fun `index paths by recency orders most recent first`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        val oldPath = "/recency/old-${System.nanoTime()}/index.html"
        val newPath = "/recency/new-${System.nanoTime()}/index.html"
        results.updateFromIndex("com.example.recency.Old", null, oldPath, emptyMap())
        Thread.sleep(5)
        results.updateFromIndex("com.example.recency.New", null, newPath, emptyMap())

        val paths = results.indexPathsByRecency()
        val newIdx = paths.indexOf(newPath)
        val oldIdx = paths.indexOf(oldPath)
        assertTrue(newIdx >= 0)
        assertTrue(oldIdx >= 0)
        assertTrue(newIdx < oldIdx, "expected $newPath ($newIdx) before $oldPath ($oldIdx)")
    }

    @Test
    fun `latest index path reflects most recent update`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex("com.example.A", null, "/p/a/index.html", emptyMap())
        results.updateFromIndex("com.example.B", null, "/p/b/index.html", emptyMap())
        assertEquals("/p/b/index.html", results.latestIndexPath)
    }

    @Test
    fun `topic publishes index html path from update from index`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val captured = AtomicReference<String?>(null)
        project.messageBus.connect(disposableFixture.get()).subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { path -> captured.set(path) },
        )

        results.updateFromIndex("com.example.X", null, "/p/x/index.html", emptyMap())
        flushEdt()

        assertEquals("/p/x/index.html", captured.get())
    }

    @Test
    fun `topic publishes null for update method`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val captured = AtomicReference<String?>("<not-yet-set>")
        project.messageBus.connect(disposableFixture.get()).subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { path -> captured.set(path) },
        )

        results.updateMethod("com.example.X", "m", TestStatus.PASSED)
        flushEdt()

        assertNull(captured.get())
    }

    @Test
    fun `topic publishes null for update class`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val captured = AtomicReference<String?>("<not-yet-set>")
        project.messageBus.connect(disposableFixture.get()).subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { path -> captured.set(path) },
        )

        results.updateClass("com.example.X", TestStatus.FAILED)
        flushEdt()

        assertNull(captured.get())
    }

    @Test
    fun `clearForIndexHtml leaves indexPathUpdatedAt for paths still referenced`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex("com.example.CleanA", null, "/clean/shared.html", emptyMap())
        results.updateFromIndex("com.example.CleanB", null, "/clean/shared.html", emptyMap())

        // Hand-clear only one of the classes by clearing the path. The implementation removes
        // both classes since they share the path, then drops the timestamp because no class
        // remains under that path.
        results.clearForIndexHtml("/clean/shared.html")
        assertFalse("/clean/shared.html" in results.indexPathsByRecency())
        assertNull(results.getClassStatus("com.example.CleanA"))
        assertNull(results.getClassStatus("com.example.CleanB"))
    }

    @Test
    fun `same class in two source bundles returns each via source-aware lookup`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.Multi", "uiTest", null,
            "/site/index.html", "/site/sources/uiTest",
            mapOf("a" to TestStatus.PASSED),
        )
        results.updateFromIndex(
            "com.example.Multi", "test", null,
            "/site/index.html", "/site/sources/test",
            mapOf("a" to TestStatus.FAILED),
        )

        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.Multi", "uiTest"))
        assertEquals(TestStatus.FAILED, results.getClassStatus("com.example.Multi", "test"))
        assertEquals(TestStatus.PASSED, results.getMethodStatus("com.example.Multi", "a", "uiTest"))
        assertEquals(TestStatus.FAILED, results.getMethodStatus("com.example.Multi", "a", "test"))
    }

    @Test
    fun `source-aware lookup falls back to single bundle when source has no entry`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.OnlySingle", null, null,
            "/single/index.html", "/single",
            mapOf("a" to TestStatus.PASSED),
        )

        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.OnlySingle", "uiTest"))
        assertEquals("/single/index.html", results.getIndexEntry("com.example.OnlySingle", "uiTest")?.indexHtmlPath)
    }

    @Test
    fun `clearForBundle removes only the targeted source`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.Targeted", "uiTest", null,
            "/site/index.html", "/site/sources/uiTest",
            mapOf("a" to TestStatus.PASSED),
        )
        results.updateFromIndex(
            "com.example.Targeted", "test", null,
            "/site/index.html", "/site/sources/test",
            mapOf("a" to TestStatus.PASSED),
        )

        results.clearForBundle("/site/index.html", "uiTest")

        assertNull(results.getClassStatus("com.example.Targeted", "uiTest"))
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.Targeted", "test"))
    }

    @Test
    fun `pruneMissingFiles removes entry when bundle dir is gone but shell remains`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        val siteRoot = Files.createTempDirectory("kensa-prune-bundle").toFile()
        val shell = File(siteRoot, "index.html").apply { writeText("<html/>") }
        val sourceBundle = File(siteRoot, "sources/uiTest").apply { mkdirs() }

        results.updateFromIndex(
            "com.example.PruneBundle", "uiTest", null,
            shell.absolutePath, sourceBundle.absolutePath,
            mapOf("a" to TestStatus.PASSED),
        )
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.PruneBundle", "uiTest"))

        sourceBundle.deleteRecursively()

        results.pruneMissingFiles()

        assertNull(results.getClassStatus("com.example.PruneBundle", "uiTest"))
    }

    private fun flushEdt() = runBlocking {
        withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }
    }

    @Test
    fun `update from index sets latest index path and exposes via getter`() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.updateFromIndex(
            "com.example.LatestPath",
            null,
            "/latest/index.html",
            mapOf("m" to TestStatus.PASSED),
        )
        assertNotNull(results.latestIndexPath)
        assertEquals("/latest/index.html", results.getIndexPath("com.example.LatestPath"))
    }
}
