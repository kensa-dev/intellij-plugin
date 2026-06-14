package dev.kensa.plugin.intellij.console

import com.intellij.CommonBundle
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.ide.browsers.WebBrowserUrlProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class KensaConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(KensaOutputFilter(project))
}

class KensaOutputFilter(private val project: Project) : Filter {
    private var foundKensaMarker = false
    private val logger = thisLogger()

    override fun applyFilter(line: String, entireLength: Int): Result? {
        if (line.contains(KENSA_MARKER)) {
            foundKensaMarker = true
            logger.debug("Found Kensa Output marker: $line")
            return null
        }

        if (foundKensaMarker) {
            val output = line.trim()
            if (output.isNotEmpty()) {
                logger.debug("Captured Kensa output: $output")
                foundKensaMarker = false

                val startOffset = entireLength - line.length
                val endOffset = entireLength

                return Result(
                    startOffset,
                    endOffset,
                    KensaIndexHyperlinkInfo(project, output)
                )
            }
        }

        return null
    }

    private class KensaIndexHyperlinkInfo(
        private val project: Project,
        private val indexPath: String,
    ) : HyperlinkInfo {

        private val log = thisLogger()

        override fun navigate(project: Project) {
            val psiFile = indexPath.asPsiFileIn(this.project) ?: return

            val request = createOpenInBrowserRequest(psiFile) ?: return

            try {
                val urls = WebBrowserService.getInstance().getUrlsToOpen(request, false)
                BrowserLauncher.instance.browse(urls.first().toExternalForm(), null, request.project)
            } catch (ex: WebBrowserUrlProvider.BrowserException) {
                Messages.showErrorDialog(ex.message, CommonBundle.getErrorTitle())
            } catch (ex: Exception) {
                log.error(ex)
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

        private fun String.asPsiFileIn(project: Project): PsiFile? =
            LocalFileSystem.getInstance().findFileByPath(this)
                ?.let { PsiManager.getInstance(project).findFile(it) }
    }

    companion object {
        const val KENSA_MARKER = "Kensa Output :"
    }
}