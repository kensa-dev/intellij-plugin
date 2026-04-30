package dev.kensa.plugin.intellij.action

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import dev.kensa.plugin.intellij.gutter.KensaReportOpener

class OpenKensaIndexAction : AnAction() {

    companion object {
        // Test seam: tests can inject a descriptor directly into the data context.
        // In production, the action falls back to RunContentManager.selectedContent.
        val RUN_CONTENT_DESCRIPTOR: DataKey<RunContentDescriptor> =
            DataKey.create("kensa.openKensaIndex.runContentDescriptor")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = resolveDescriptor(e, project) ?: return
        val indexPath = project.service<KensaRunTabRegistry>().indexPathFor(descriptor) ?: return
        KensaReportOpener.openIndexHtml(project, indexPath)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val descriptor = project?.let { resolveDescriptor(e, it) }
        val visible = project != null && descriptor != null &&
            project.service<KensaRunTabRegistry>().indexPathFor(descriptor) != null
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
    }

    private fun resolveDescriptor(e: AnActionEvent, project: Project): RunContentDescriptor? =
        e.getData(RUN_CONTENT_DESCRIPTOR) ?: RunContentManager.getInstance(project).selectedContent
}
