package dev.kensa.plugin.intellij.statusbar

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
}
