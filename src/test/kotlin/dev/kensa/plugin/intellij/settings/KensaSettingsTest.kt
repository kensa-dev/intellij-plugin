package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class KensaSettingsTest {

    private val projectFixture = projectFixture()

    @Test
    fun `effectiveOutputDirName defaults to kensa-output`() {
        val settings = projectFixture.get().service<KensaSettings>()
        settings.state.outputDirName = null
        assertEquals("kensa-output", settings.effectiveOutputDirName)
    }

    @Test
    fun `effectiveOutputDirName uses configured value when set`() {
        val settings = projectFixture.get().service<KensaSettings>()
        settings.state.outputDirName = "build/kensa"
        assertEquals("build/kensa", settings.effectiveOutputDirName)
    }

    @Test
    fun `effectiveOutputDirName falls back when blank`() {
        val settings = projectFixture.get().service<KensaSettings>()
        settings.state.outputDirName = "   "
        assertEquals("kensa-output", settings.effectiveOutputDirName)
    }

    @Test
    fun `resolveUrl returns null when template unset`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate = null
        assertNull(settings.resolveUrl(project, "com.example.MyTest", "method"))
    }

    @Test
    fun `resolveUrl substitutes all placeholders`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate =
            "https://ci.example.com/{projectName}/{packageName}/{simpleClassName}/{testClass}?method={testMethod}"

        val url = settings.resolveUrl(project, "com.example.MyTest", "myMethod")
        assertEquals(
            "https://ci.example.com/${project.name}/com.example/MyTest/com.example.MyTest?method=myMethod",
            url,
        )
    }

    @Test
    fun `resolveUrl url-encodes method name`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate = "https://ci/{testMethod}"
        val url = settings.resolveUrl(project, "com.example.X", "foo bar[1]")
        assertEquals("https://ci/foo+bar%5B1%5D", url)
    }

    @Test
    fun `resolveUrl strips method query param when method is null`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate =
            "https://ci.example.com/{testClass}?method={testMethod}"
        val url = settings.resolveUrl(project, "com.example.MyTest", null)
        assertEquals("https://ci.example.com/com.example.MyTest", url)
    }

    @Test
    fun `resolveUrl preserves other query params when method is null`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate =
            "https://ci/{testClass}?run=latest&method={testMethod}"
        val url = settings.resolveUrl(project, "com.example.X", null)
        assertEquals("https://ci/com.example.X?run=latest", url)
    }

    @Test
    fun `previewUrl uses provided template instead of state`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate = "https://stale/{testClass}"

        val url = settings.previewUrl(project, "https://preview/{testClass}", "com.example.X", null)
        assertEquals("https://preview/com.example.X", url)
    }

    @Test
    fun `package name is empty for root class`() {
        val project = projectFixture.get()
        val settings = project.service<KensaSettings>()
        settings.state.ciReportUrlTemplate = "{packageName}/{simpleClassName}"
        val url = settings.resolveUrl(project, "RootTest", null)
        assertEquals("/RootTest", url)
    }
}
