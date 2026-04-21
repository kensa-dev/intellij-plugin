package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class KensaTestResultsServiceTest : BasePlatformTestCase() {

    fun testPruneMissingFilesClearsEntriesForDeletedIndexHtml() {
        val results = project.service<KensaTestResultsService>()
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
}
