
package dev.kensa.plugin.intellij.execution

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.ActivityTracker
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.AppExecutorUtil
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaResultsListener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import java.util.concurrent.TimeUnit
import com.intellij.openapi.components.service
import dev.kensa.plugin.intellij.gutter.KensaIndexLoader
import dev.kensa.plugin.intellij.settings.KensaSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class KensaOutputFileWatcherStartupActivity : ProjectActivity {

    private val log = thisLogger()

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return

        val startupComplete = AtomicBoolean(false)

        fun walkForIndices() {
            if (project.isDisposed) return
            val outputDir = project.service<KensaSettings>().effectiveOutputDirName
            project.service<KensaIndexLoader>().scan(File(basePath), outputDir)
        }

        // The build/output dirs (Gradle `build`, Maven `target`, …) are exactly the dirs IntelliJ
        // marks excluded — which is *why* VFS ignores reports written under them. We read that set
        // from the module model so the fast probe can target only those, not the whole project tree.
        fun computeBuildRoots(): List<String> = ApplicationManager.getApplication().runReadAction(
            Computable {
                if (project.isDisposed) return@Computable emptyList<String>()
                val fromModel = ModuleManager.getInstance(project).modules.flatMap { module ->
                    ModuleRootManager.getInstance(module).excludeRoots.map { it.path }
                }
                // Fallbacks in case the model isn't ready or a root-level build dir isn't excluded yet.
                (fromModel + listOf("$basePath/build", "$basePath/target")).distinct()
            }
        )

        val buildRoots = java.util.concurrent.atomic.AtomicReference(computeBuildRoots())

        project.messageBus.connect().subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { indexHtmlPath ->
                ActivityTracker.getInstance().inc()

                if (indexHtmlPath == null) return@KensaResultsListener
                if (!startupComplete.get()) return@KensaResultsListener

                log.debug("Kensa output detected: $indexHtmlPath")

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Kensa")
                    .createNotification(
                        "Kensa Report Ready",
                        "Test report updated",
                        NotificationType.INFORMATION,
                    )
                    .addAction(OpenKensaReportNotificationAction(project, indexHtmlPath))
                    .notify(project)
            }
        )

        // Reports written by terminal/external test runs land under an excluded `build/` dir, so VFS
        // would normally never see them. We register a native file-watch on each *discovered* report
        // dir, which makes VFS_CHANGES fire for it despite the exclusion — instant, push-based updates
        // with no polling. The bundle dir holds `indices.json` directly, so a flat (non-recursive)
        // watch is enough and keeps the native watcher's load minimal.
        val watchedRoots = ConcurrentHashMap<String, LocalFileSystem.WatchRequest>()

        fun syncWatchedRoots() {
            if (project.isDisposed) return
            val desired = project.service<KensaTestResultsService>().bundleDirs()
            val lfs = LocalFileSystem.getInstance()
            (desired - watchedRoots.keys).forEach { dir ->
                lfs.addRootToWatch(dir, false)?.let { watchedRoots[dir] = it }
            }
            (watchedRoots.keys - desired).forEach { dir ->
                watchedRoots.remove(dir)?.let { lfs.removeWatchedRoot(it) }
            }
        }

        walkForIndices()
        syncWatchedRoots()
        ApplicationManager.getApplication().invokeLater {
            DaemonCodeAnalyzer.getInstance(project).restart("Kensa startup scan complete")
            startupComplete.set(true)
        }

        val scheduler = AppExecutorUtil.getAppScheduledExecutorService()

        // Fast path (3s): probe only the known build/output dirs at the fixed report shape. This is
        // what makes a terminal test run show up quickly — native watches cover already-known reports
        // instantly, but a brand-new report dir can't be watched until it exists, and this catches it.
        val probeTask = scheduler.scheduleWithFixedDelay(
            {
                if (project.isDisposed) return@scheduleWithFixedDelay
                val outputDir = project.service<KensaSettings>().effectiveOutputDirName
                buildRoots.get().forEach { root ->
                    project.service<KensaIndexLoader>().probeBuildDir(File(root), outputDir)
                }
                project.service<KensaIndexLoader>().refreshRunStates()
                syncWatchedRoots()
            },
            3, 3, TimeUnit.SECONDS,
        )

        // Slow path (60s): refresh the build-root set from the (possibly still-loading) module model,
        // run one pruned full walk to catch reports in non-excluded/unconventional locations, and
        // prune stale entries. Bounded and infrequent.
        val maintenanceTask = scheduler.scheduleWithFixedDelay(
            {
                if (project.isDisposed) return@scheduleWithFixedDelay
                buildRoots.set(computeBuildRoots())
                walkForIndices()
                project.service<KensaTestResultsService>().pruneMissingFiles()
                project.service<KensaIndexLoader>().pruneLoadedMtimes { File(it).exists() }
                syncWatchedRoots()
            },
            60, 60, TimeUnit.SECONDS,
        )

        com.intellij.openapi.util.Disposer.register(project) {
            probeTask.cancel(false)
            maintenanceTask.cancel(false)
            val lfs = LocalFileSystem.getInstance()
            watchedRoots.values.forEach { lfs.removeWatchedRoot(it) }
            watchedRoots.clear()
        }

        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, KensaVfsListener(project, basePath))
    }

    private class OpenKensaReportNotificationAction(
        private val project: Project,
        private val indexPath: String,
    ) : com.intellij.notification.NotificationAction("Open Report") {

        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
            notification.expire()
            KensaReportOpener.openIndexHtml(project, indexPath)
        }
    }
}
