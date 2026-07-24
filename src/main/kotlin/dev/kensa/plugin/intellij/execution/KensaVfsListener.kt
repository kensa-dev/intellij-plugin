package dev.kensa.plugin.intellij.execution

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import dev.kensa.plugin.intellij.gutter.KensaIndexLoader
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.settings.KensaSettings
import java.io.File
import java.util.concurrent.Executor

/**
 * Reacts to VFS changes on Kensa report files. VFS events are delivered on the EDT inside a write
 * action, so this listener only does cheap path filtering inline and hands the file IO + JSON
 * parsing to [executor]. The executor is single-threaded so batches apply in event order.
 */
internal class KensaVfsListener(
    private val project: Project,
    private val basePath: String,
    private val executor: Executor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("Kensa VFS Listener", 1),
) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val hasRelevantDelete = events.any { event ->
            event is VFileDeleteEvent && event.path.startsWith(basePath)
        }
        val candidatePaths = events
            .filter { event ->
                (event is VFileContentChangeEvent || event is VFileCreateEvent) &&
                    event.path.startsWith(basePath) &&
                    event.path.endsWith("/indices.json")
            }
            .map { it.path }

        if (!hasRelevantDelete && candidatePaths.isEmpty()) return

        executor.execute {
            if (project.isDisposed) return@execute
            if (hasRelevantDelete) {
                project.service<KensaTestResultsService>().pruneMissingFiles()
            }
            val outputDir = project.service<KensaSettings>().effectiveOutputDirName
            val loader = project.service<KensaIndexLoader>()
            candidatePaths.forEach { path ->
                val file = File(path)
                if (KensaIndexLoader.isKensaIndicesJson(file, outputDir)) {
                    loader.loadFromFile(file)
                }
            }
        }
    }
}
