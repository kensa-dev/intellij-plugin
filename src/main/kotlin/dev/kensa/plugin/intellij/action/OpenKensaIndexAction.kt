package dev.kensa.plugin.intellij.action

import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService

class OpenKensaIndexAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = RunContentManager.getInstance(project).selectedContent
        val indexPath = descriptor?.let { project.service<KensaRunTabRegistry>().indexPathFor(it) }
            ?: project.service<KensaTestResultsService>().latestIndexPath
            ?: return
        openInBrowser(indexPath.asPsiFileIn(project))
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val descriptor = project?.let { RunContentManager.getInstance(it).selectedContent }
        val visible = descriptor != null &&
            project.service<KensaRunTabRegistry>().indexPathFor(descriptor) != null

        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
    }

    private fun openInBrowser(psiFile: PsiFile?) {
        if (psiFile != null) {
            val request = createOpenInBrowserRequest(psiFile)
            if (request != null) openInBrowser(request)
        }
    }

    private fun createOpenInBrowserRequest(element: PsiElement): OpenInBrowserRequest? {
        val psiFile = ApplicationManager.getApplication().runReadAction(Computable {
            if (element.isValid) {
                element.containingFile?.let { if (it.virtualFile == null) null else it }
            } else null
        }) ?: return null

        return object : OpenInBrowserRequest(psiFile, true) {
            override val element = element
        }
    }

    private fun openInBrowser(request: OpenInBrowserRequest, preferLocalUrl: Boolean = false, browser: WebBrowser? = null) {
        try {
            val urls = WebBrowserService.getInstance().getUrlsToOpen(request, preferLocalUrl)
            BrowserLauncher.instance.browse(urls.first().toExternalForm(), browser, request.project)
        } catch (e: WebBrowserUrlProvider.BrowserException) {
            Messages.showErrorDialog(e.message, IdeBundle.message("browser.error"))
        } catch (e: Exception) {
            thisLogger().error(e)
        }
    }

    private fun String.asPsiFileIn(project: Project): PsiFile? =
        LocalFileSystem.getInstance().findFileByPath(this)?.let { PsiManager.getInstance(project).findFile(it) }
}
