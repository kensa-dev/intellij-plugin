package dev.kensa.plugin.intellij.execution

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.kensa.plugin.intellij.gutter.KensaIndexLoader
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.TestStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

@TestApplication
class KensaVfsListenerTest {

    private val projectFixture = projectFixture()

    // Collects work instead of running it, so tests control exactly when background work happens.
    private class ManualExecutor : Executor {
        val queued = mutableListOf<Runnable>()
        override fun execute(command: Runnable) {
            queued.add(command)
        }

        fun drain() {
            queued.forEach { it.run() }
            queued.clear()
        }
    }

    @Test
    fun `defers indices parsing to the executor instead of the event thread`() {
        val project = projectFixture.get()
        val tmp = Files.createTempDirectory("kensa-vfs-listener").toFile()
        val bundle = File(tmp, "build/kensa-output").apply { mkdirs() }
        val indicesJson = File(bundle, "indices.json").apply {
            writeText(
                """{"indices":[{"testClass":"com.example.Vfs","state":"Passed",
                "children":[{"testMethod":"m","state":"Passed"}]}]}"""
            )
        }
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indicesJson)
            ?: error("indices.json not visible to VFS")

        val executor = ManualExecutor()
        val listener = KensaVfsListener(project, tmp.absolutePath, executor)

        listener.after(listOf(VFileContentChangeEvent(null, vFile, 0, 1)))

        val results = project.service<KensaTestResultsService>()
        // VFS events arrive on the EDT inside a write action: the listener must only queue
        // work there, never read or parse the file inline.
        assertNull(results.getClassStatus("com.example.Vfs"))

        executor.drain()
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.Vfs"))
    }

    @Test
    fun `defers pruning of deleted reports to the executor`() {
        val project = projectFixture.get()
        val tmp = Files.createTempDirectory("kensa-vfs-prune").toFile()
        val bundle = File(tmp, "build/kensa-output").apply { mkdirs() }
        val indicesJson = File(bundle, "indices.json").apply {
            writeText(
                """{"indices":[{"testClass":"com.example.Pruned","state":"Passed",
                "children":[{"testMethod":"m","state":"Passed"}]}]}"""
            )
        }
        project.service<KensaIndexLoader>().loadFromFile(indicesJson)
        val results = project.service<KensaTestResultsService>()
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.Pruned"))

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indicesJson)
            ?: error("indices.json not visible to VFS")
        bundle.deleteRecursively()

        val executor = ManualExecutor()
        val listener = KensaVfsListener(project, tmp.absolutePath, executor)
        listener.after(listOf(VFileDeleteEvent(null, vFile)))

        // Pruning stats the filesystem, so it must not run inline on the event thread.
        assertEquals(TestStatus.PASSED, results.getClassStatus("com.example.Pruned"))

        executor.drain()
        assertNull(results.getClassStatus("com.example.Pruned"))
    }

    @Test
    fun `ignores unrelated events without queueing work`() {
        val project = projectFixture.get()
        val tmp = Files.createTempDirectory("kensa-vfs-unrelated").toFile()
        val other = File(tmp, "notes.txt").apply { writeText("hello") }
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(other)
            ?: error("notes.txt not visible to VFS")

        val executor = ManualExecutor()
        val listener = KensaVfsListener(project, tmp.absolutePath, executor)
        listener.after(listOf(VFileContentChangeEvent(null, vFile, 0, 1)))

        assertTrue(executor.queued.isEmpty())
    }
}
