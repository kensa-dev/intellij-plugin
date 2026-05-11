package dev.kensa.plugin.intellij.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import java.io.File
import java.nio.file.Paths

class OpenKensaReportForFolderGroup : ActionGroup("Open Kensa Report", true) {

    override fun getActionUpdateThread() = BGT

    override fun update(e: AnActionEvent) {
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val hasOutput = folder != null && folder.isDirectory && project != null &&
            indexPathsUnder(project, folder.path).isNotEmpty()
        e.presentation.isEnabledAndVisible = hasOutput
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        e ?: return emptyArray()
        val folder = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyArray()
        val project = e.project ?: return emptyArray()
        val folderPath = Paths.get(folder.path)

        return indexPathsUnder(project, folder.path)
            .filter { File(it).exists() }
            .sorted()
            .map { indexHtml ->
                val label = folderPath.relativize(File(indexHtml).parentFile.toPath()).toString()
                OpenIndexAction(project, indexHtml, label)
            }
            .toTypedArray()
    }

    private fun indexPathsUnder(project: Project, folderPath: String): List<String> {
        val prefix = if (folderPath.endsWith('/')) folderPath else "$folderPath/"
        return project.service<KensaTestResultsService>().allIndexPaths()
            .filter { it.startsWith(prefix) }
            .toList()
    }
}

private class OpenIndexAction(
    private val project: Project,
    private val indexHtmlPath: String,
    val label: String,
) : AnAction(label) {
    override fun getActionUpdateThread() = BGT
    override fun actionPerformed(e: AnActionEvent) = KensaReportOpener.openIndexHtml(project, indexHtmlPath)
}
