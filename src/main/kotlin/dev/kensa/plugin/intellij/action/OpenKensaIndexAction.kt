package dev.kensa.plugin.intellij.action

import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService

class OpenKensaIndexAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = RunContentManager.getInstance(project).selectedContent
        val indexPath = descriptor?.let { project.service<KensaRunTabRegistry>().indexPathFor(it) }
            ?: project.service<KensaTestResultsService>().latestIndexPath
            ?: return
        KensaReportOpener.openIndexHtml(project, indexPath)
    }

    override fun update(e: AnActionEvent) {
        val visible = e.project?.service<KensaTestResultsService>()?.latestIndexPath != null
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
    }
}
