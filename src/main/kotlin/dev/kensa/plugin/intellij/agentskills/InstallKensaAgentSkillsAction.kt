package dev.kensa.plugin.intellij.agentskills

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import dev.kensa.plugin.intellij.KensaBundle
import java.nio.file.Path
import kotlin.io.path.Path

class InstallKensaAgentSkillsAction : AnAction() {

    companion object {
        fun runInstall(project: Project) {
            installInProject(project)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        installInProject(project)
    }
}

private fun installInProject(project: Project) {
    val basePath = project.basePath ?: return

    val dialog = InstallAgentSkillsDialog(project)
    if (!dialog.showAndGet()) return
    val target = dialog.selectedTarget

    val files = try {
        AgentSkillBundleBuilder.filesFor(target)
    } catch (ex: Exception) {
        Messages.showErrorDialog(
            project,
            ex.message ?: KensaBundle.message("agentSkills.error.bundle"),
            KensaBundle.message("agentSkills.dialog.title"),
        )
        return
    }

    val baseDir = Path(basePath)
    val existing = files.keys.filter { baseDir.resolve(it).toFile().exists() }
    if (existing.isNotEmpty()) {
        val choice = Messages.showYesNoDialog(
            project,
            KensaBundle.message("agentSkills.dialog.overwriteConfirm", existing.joinToString("\n")),
            KensaBundle.message("agentSkills.dialog.title"),
            null,
        )
        if (choice != Messages.YES) return
    }

    val written = mutableListOf<VirtualFile>()
    try {
        WriteAction.runAndWait<RuntimeException> {
            files.forEach { (relativePath, content) ->
                written += writeProjectFile(baseDir, relativePath, content)
            }
        }
    } catch (ex: Exception) {
        Messages.showErrorDialog(
            project,
            KensaBundle.message("agentSkills.error.write", ex.message ?: ex.javaClass.simpleName),
            KensaBundle.message("agentSkills.dialog.title"),
        )
        return
    }

    notifySuccess(project, files.keys)
    openFirst(project, written)
}

private fun writeProjectFile(baseDir: Path, relativePath: String, content: String): VirtualFile {
    val absolute = baseDir.resolve(relativePath)
    val parent = absolute.parent
    val parentVf = VfsUtil.createDirectoryIfMissing(parent.toString())
        ?: error("Could not create directory: $parent")
    val fileName = absolute.fileName.toString()
    val existing = parentVf.findChild(fileName)
    val target = existing ?: parentVf.createChildData(InstallKensaAgentSkillsAction::class.java, fileName)
    target.setBinaryContent(content.toByteArray(Charsets.UTF_8))
    LocalFileSystem.getInstance().refreshAndFindFileByPath(absolute.toString())
    return target
}

private fun notifySuccess(project: Project, paths: Collection<String>) {
    Notifications.Bus.notify(
        Notification(
            "Kensa",
            KensaBundle.message("agentSkills.notify.title"),
            KensaBundle.message("agentSkills.notify.success", paths.joinToString("\n")),
            NotificationType.INFORMATION,
        ),
        project,
    )
}

private fun openFirst(project: Project, written: List<VirtualFile>) {
    val firstFile = written.firstOrNull { it.name.endsWith(".md") || it.name.endsWith(".mdc") }
        ?: written.firstOrNull()
        ?: return
    FileEditorManager.getInstance(project).openFile(firstFile, true)
}
