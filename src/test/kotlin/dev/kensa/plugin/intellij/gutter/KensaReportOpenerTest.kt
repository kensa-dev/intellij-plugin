package dev.kensa.plugin.intellij.gutter

import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.settings.KensaSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

@TestApplication
class KensaReportOpenerTest {

    private val projectFixture = projectFixture()

    @Test
    fun `localReportPath returns existing path stored in service`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-opener").toFile()
        val indexHtml = File(tempDir, "index.html").apply { writeText("<html/>") }

        project.service<KensaTestResultsService>().updateFromIndex(
            "com.example.Opener",
            null,
            indexHtml.absolutePath,
            mapOf("m" to TestStatus.PASSED),
        )

        assertEquals(indexHtml.absolutePath, localReportPath(project, "com.example.Opener"))
    }

    @Test
    fun `localReportPath returns null when service has no entry`() {
        assertNull(localReportPath(projectFixture.get(), "com.example.Unknown"))
    }

    @Test
    fun `localReportPath returns null when index html no longer exists on disk`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-opener-stale").toFile()
        val indexHtml = File(tempDir, "index.html").apply { writeText("<html/>") }

        project.service<KensaTestResultsService>().updateFromIndex(
            "com.example.Stale",
            null,
            indexHtml.absolutePath,
            mapOf("m" to TestStatus.PASSED),
        )

        indexHtml.delete()

        assertNull(localReportPath(project, "com.example.Stale"))
    }

    @Test
    fun `ciUrl delegates to settings`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate = "https://ci/{testClass}?method={testMethod}"

        val url = ciUrl(project, "com.example.Cls", "doStuff")
        assertEquals("https://ci/com.example.Cls?method=doStuff", url)
    }

    @Test
    fun `ciUrl is null when template unset`() {
        val project = projectFixture.get()
        project.service<KensaSettings>().state.ciReportUrlTemplate = null
        assertNull(ciUrl(project, "com.example.Anything", null))
    }

    @Test
    fun `KensaTarget data class equality and copy`() {
        val a = KensaTarget("com.example.X", "method")
        val b = KensaTarget("com.example.X", "method")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(KensaTarget("com.example.X", null), a.copy(methodName = null))
    }
}
