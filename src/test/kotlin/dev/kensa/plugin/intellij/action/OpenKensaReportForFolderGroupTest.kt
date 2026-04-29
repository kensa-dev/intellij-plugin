package dev.kensa.plugin.intellij.action

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.TestStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

@TestApplication
class OpenKensaReportForFolderGroupTest {

    private val projectFixture = projectFixture()

    @BeforeEach
    fun resetService() {
        val results = projectFixture.get().service<KensaTestResultsService>()
        results.allIndexPaths().toList().forEach(results::clearForIndexHtml)
        results.latestIndexPath = null
    }

    @Test
    fun `disabled when no folder selected`() {
        val project = projectFixture.get()
        val action = OpenKensaReportForFolderGroup()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `disabled when folder has no Kensa output below it`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-folder-empty").toFile()
        val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)
            ?: error("temp dir not visible to VFS")

        val action = OpenKensaReportForFolderGroup()
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, vDir)
            .build()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `enabled when folder contains a registered Kensa output`() {
        val project = projectFixture.get()
        val results = project.service<KensaTestResultsService>()
        val tempDir = Files.createTempDirectory("kensa-folder-with").toFile()
        val outputDir = File(tempDir, "module/kensa-output").apply { mkdirs() }
        val indexHtml = File(outputDir, "index.html").apply { writeText("<html/>") }

        results.updateFromIndex(
            "com.example.InFolder",
            null,
            indexHtml.absolutePath,
            mapOf("m" to TestStatus.PASSED),
        )

        val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)
            ?: error("temp dir not visible to VFS")

        val action = OpenKensaReportForFolderGroup()
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, vDir)
            .build()
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `getChildren returns one entry per discovered index html under folder`() {
        val project = projectFixture.get()
        val tempDir = Files.createTempDirectory("kensa-folder-children").toFile()
        File(tempDir, "moduleA/kensa-output").apply { mkdirs(); File(this, "index.html").writeText("<html/>") }
        File(tempDir, "moduleB/kensa-output").apply { mkdirs(); File(this, "index.html").writeText("<html/>") }
        // A nested subdir we should NOT pick up — wrong parent name.
        File(tempDir, "moduleC/other").apply { mkdirs(); File(this, "index.html").writeText("<html/>") }

        val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)
            ?: error("temp dir not visible to VFS")

        val action = OpenKensaReportForFolderGroup()
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, vDir)
            .build()
        val event = TestActionEvent.createTestEvent(action, context)
        val children = action.getChildren(event)

        assertEquals(2, children.size)
    }
}
