package dev.kensa.plugin.intellij.statusbar

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.gutter.RunPhase
import dev.kensa.plugin.intellij.gutter.RunStateEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.JPanel

@TestApplication
class KensaStatusBarWidgetTest {

    private val projectFixture = projectFixture()

    @Test
    fun `widget ID matches factory ID`() {
        val widget = KensaStatusBarWidget(projectFixture.get())
        assertEquals(KensaStatusBarWidget.ID, widget.ID())
    }

    @Test
    fun `getComponent returns a JPanel`() {
        val widget = KensaStatusBarWidget(projectFixture.get())
        val component = widget.component
        assertNotNull(component)
        assertEquals(JPanel::class.java, component::class.java)
    }

    // Regression: labelFor consults ProjectFileIndex/VFS, which are model reads. It is reached from
    // the EDT with no ambient read action -- via showPicker() on a status-bar click, and via
    // tooltip() on every refresh once more than one report exists -- so it must take its own read
    // action. Without one the platform throws "Read access is allowed from inside read-action only".
    // Asserted from a pooled thread holding no read action, which trips the same assertion the EDT does.
    @Test
    fun `labelFor takes its own read action`() {
        val project = projectFixture.get()
        val reportDir = Files.createTempDirectory("kensa-statusbar").toFile()
            .resolve("some-module/kensa-output")
            .apply { mkdirs() }
        val indexHtml = File(reportDir, "index.html").apply { writeText("<html/>") }
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexHtml)

        val widget = KensaStatusBarWidget(project)

        val executor = Executors.newSingleThreadExecutor()
        try {
            val label = executor.submit<String> { widget.labelFor(indexHtml.absolutePath) }.get()
            assertNotNull(label)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `running text sums classes across running bundles`() {
        val entries = listOf(
            RunStateEntry("/a/kensa-output", RunPhase.RUNNING, "2026-08-27T10:15:30.00Z", 1, 3),
            RunStateEntry("/b/kensa-output", RunPhase.RUNNING, "2026-08-27T10:15:31.00Z", 2, 2),
            RunStateEntry("/c/kensa-output", RunPhase.ABANDONED, null, 3, 1),
        )
        assertEquals("running, 5 classes", KensaStatusBarWidget.runningText(entries))
    }

    @Test
    fun `running text uses singular for one class`() {
        val entries = listOf(RunStateEntry("/a/kensa-output", RunPhase.RUNNING, null, 1, 1))
        assertEquals("running, 1 class", KensaStatusBarWidget.runningText(entries))
    }

    @Test
    fun `abandoned tooltip names the start time and pid`() {
        val entries = listOf(
            RunStateEntry("/a/kensa-output", RunPhase.ABANDONED, "2026-08-27T10:15:30.00Z", 12345, 2),
        )
        val tooltip = KensaStatusBarWidget.abandonedTooltip(entries)
        assertTrue(tooltip.contains("never completed"))
        assertTrue(tooltip.contains("12345"))
        assertTrue(tooltip.contains("re-run"))
    }

    @Test
    fun `abandoned tooltip survives an unparseable start time`() {
        val entries = listOf(RunStateEntry("/a/kensa-output", RunPhase.ABANDONED, "garbage", null, 0))
        assertTrue(KensaStatusBarWidget.abandonedTooltip(entries).contains("never completed"))
    }
}
