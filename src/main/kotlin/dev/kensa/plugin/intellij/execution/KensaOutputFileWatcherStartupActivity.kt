
package dev.kensa.plugin.intellij.execution

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.ActivityTracker
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import dev.kensa.plugin.intellij.gutter.KensaResultsListener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import java.util.concurrent.TimeUnit
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.openapi.components.service
import dev.kensa.plugin.intellij.gutter.KensaIndexLoader
import dev.kensa.plugin.intellij.settings.KensaSettings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class KensaOutputFileWatcherStartupActivity : ProjectActivity {

    private val log = thisLogger()

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return

        val startupComplete = AtomicBoolean(false)
        val lastNotifiedAt = AtomicLong(0)
        val debounceMs = 3000L

        project.messageBus.connect().subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { indexHtmlPath ->
                ActivityTracker.getInstance().inc()

                if (indexHtmlPath == null) return@KensaResultsListener
                if (!startupComplete.get()) return@KensaResultsListener

                val now = System.currentTimeMillis()
                val last = lastNotifiedAt.get()
                if (now - last < debounceMs) return@KensaResultsListener
                if (!lastNotifiedAt.compareAndSet(last, now)) return@KensaResultsListener

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

        scanExistingIndices(project, basePath)
        ApplicationManager.getApplication().invokeLater {
            DaemonCodeAnalyzer.getInstance(project).restart("Kensa startup scan complete")
            startupComplete.set(true)
        }

        val pollTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            {
                if (!project.isDisposed) project.service<KensaTestResultsService>().pruneMissingFiles()
            },
            5, 5, TimeUnit.SECONDS,
        )
        com.intellij.openapi.util.Disposer.register(project) { pollTask.cancel(false) }

        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val outputDir = project.service<KensaSettings>().effectiveOutputDirName

                val hasRelevantDelete = events.any { event ->
                    event is VFileDeleteEvent && event.path.startsWith(basePath)
                }
                if (hasRelevantDelete) {
                    project.service<KensaTestResultsService>().pruneMissingFiles()
                }

                val indexJsonEvents = events.filter { event ->
                    (event is VFileContentChangeEvent || event is VFileCreateEvent) &&
                        event.path.startsWith(basePath) &&
                        event.path.contains("/$outputDir/") &&
                        event.path.endsWith("/indices.json")
                }
                indexJsonEvents.forEach { event ->
                    val vFile = LocalFileSystem.getInstance().findFileByPath(event.path) ?: return@forEach
                    KensaIndexLoader.loadFromFile(project, vFile)
                }
            }
        })
    }

    private fun scanExistingIndices(project: Project, basePath: String) {
        val outputDir = project.service<KensaSettings>().effectiveOutputDirName
        File(basePath).walkTopDown()
            .filter { it.name == "indices.json" && it.parentFile?.name == outputDir }
            .forEach { file ->
                val vFile = LocalFileSystem.getInstance().findFileByPath(file.path) ?: return@forEach
                KensaIndexLoader.loadFromFile(project, vFile)
            }
    }

    private class OpenKensaReportNotificationAction(
        private val project: Project,
        private val indexPath: String,
    ) : com.intellij.notification.NotificationAction("Open Report") {

        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: com.intellij.notification.Notification) {
            notification.expire()

            val vFile = LocalFileSystem.getInstance().findFileByPath(indexPath) ?: return
            val psiFile = ApplicationManager.getApplication().runReadAction(Computable { PsiManager.getInstance(project).findFile(vFile) }) ?: return

            val request = object : OpenInBrowserRequest(psiFile, true) {
                override val element: PsiElement = psiFile
            }

            try {
                val urls = WebBrowserService.getInstance().getUrlsToOpen(request, false)
                BrowserLauncher.instance.browse(urls.first().toExternalForm(), null, project)
            } catch (ex: Exception) {
                thisLogger().error(ex)
            }
        }
    }
}
