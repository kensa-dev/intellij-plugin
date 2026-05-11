package dev.kensa.plugin.intellij.gutter

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import dev.kensa.plugin.intellij.settings.KensaSettings
import org.jetbrains.uast.*
import java.awt.event.MouseEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

internal const val KENSA_TEST_FQN = "dev.kensa.junit.KensaTest"
internal val TEST_ANNOTATIONS = setOf(
    "org.junit.jupiter.api.Test",
    "org.junit.jupiter.params.ParameterizedTest",
)

data class KensaTarget(val classFqn: String, val methodName: String?)

fun resolveKensaTarget(element: PsiElement): KensaTarget? {
    if (element.firstChild != null) return null
    val parent = element.parent ?: return null
    val uParent = parent.toUElementOfExpectedTypes(UClass::class.java, UMethod::class.java) ?: return null

    val nameIdentifier = when (uParent) {
        is UClass -> uParent.uastAnchor?.sourcePsi
        is UMethod -> uParent.uastAnchor?.sourcePsi
        else -> null
    }
    if (nameIdentifier != element && nameIdentifier?.textRange != element.textRange) return null

    val project = element.project

    val (classFqn, methodName) = when (uParent) {
        is UMethod -> {
            val containingClass = uParent.getContainingUClass() ?: return null
            val fqn = containingClass.qualifiedName ?: return null
            fqn to uParent.name
        }
        is UClass -> {
            val fqn = uParent.qualifiedName ?: return null
            fqn to null
        }
        else -> return null
    }

    if (!isKensaTest(uParent, project)) return null
    return KensaTarget(classFqn, methodName)
}

fun isKensaTest(uElement: UElement, project: Project): Boolean {
    val psiClass = when (uElement) {
        is UClass -> uElement.javaPsi
        is UMethod -> uElement.getContainingUClass()?.javaPsi
        else -> null
    } ?: return false

    val kensaBase = JavaPsiFacade.getInstance(project)
        .findClass(KENSA_TEST_FQN, GlobalSearchScope.allScope(project))
        ?: return false

    if (!InheritanceUtil.isInheritorOrSelf(psiClass, kensaBase, true)) return false

    if (uElement is UMethod) {
        return uElement.uAnnotations.any { annotation ->
            annotation.qualifiedName in TEST_ANNOTATIONS
        }
    }

    return true
}

fun localReportPath(project: Project, classFqn: String): String? =
    localReportPath(project, classFqn, null)

fun localReportPath(project: Project, classFqn: String, fileSourceId: String?): String? =
    project.service<KensaTestResultsService>().getIndexEntry(classFqn, fileSourceId)
        ?.indexHtmlPath
        ?.takeIf { java.io.File(it).exists() }

fun ciUrl(project: Project, classFqn: String, methodName: String?): String? =
    project.service<KensaSettings>().resolveUrl(project, classFqn, methodName)

fun buildKensaRoute(classFqn: String, methodName: String?, sourceId: String?): String = buildString {
    append("#/test/")
    if (!sourceId.isNullOrBlank()) {
        append(URLEncoder.encode(sourceId, UTF_8))
        append("::")
    }
    append(URLEncoder.encode(classFqn, UTF_8))
    if (!methodName.isNullOrBlank()) {
        append("?method=")
        append(URLEncoder.encode(methodName, UTF_8))
    }
}

object KensaReportOpener {

    fun openLocal(mouseEvent: MouseEvent?, project: Project, classFqn: String, methodName: String?) {
        openLocal(mouseEvent, project, classFqn, methodName, fileSourceId = null)
    }

    fun openLocal(
        mouseEvent: MouseEvent?,
        project: Project,
        classFqn: String,
        methodName: String?,
        fileSourceId: String?,
    ) {
        val entry = project.service<KensaTestResultsService>().getIndexEntry(classFqn, fileSourceId)
        if (entry == null) {
            Messages.showInfoMessage(
                project,
                "No local Kensa report found.\nRun the tests first.",
                "Kensa"
            )
            return
        }
        if (!java.io.File(entry.indexHtmlPath).exists()) {
            val message = if (entry.sourceId != null)
                "Kensa site shell not found at ${entry.indexHtmlPath}.\nRun ./gradlew assembleKensaSite to assemble the report shell."
            else
                "Kensa report shell not found at ${entry.indexHtmlPath}."
            Messages.showWarningDialog(project, message, "Kensa")
            return
        }
        openLocalBrowser(project, entry.indexHtmlPath, classFqn, methodName, entry.sourceId)
    }

    fun openCi(project: Project, classFqn: String, methodName: String?) {
        val url = ciUrl(project, classFqn, methodName) ?: return
        try {
            BrowserLauncher.instance.browse(url, null, project)
        } catch (ex: Exception) {
            thisLogger().error(ex)
        }
    }

    fun openIndexHtml(project: Project, indexHtmlPath: String) {
        val vFile = LocalFileSystem.getInstance().findFileByPath(indexHtmlPath) ?: return
        val psiFile = ApplicationManager.getApplication()
            .runReadAction(Computable { PsiManager.getInstance(project).findFile(vFile) }) ?: return
        val request = object : OpenInBrowserRequest(psiFile, true) {
            override val element: PsiElement = psiFile
        }
        try {
            val url = WebBrowserService.getInstance().getUrlsToOpen(request, false)
                .firstOrNull()?.toExternalForm() ?: return
            BrowserLauncher.instance.browse(url, null, project)
        } catch (ex: Exception) {
            thisLogger().error(ex)
        }
    }

    private fun openLocalBrowser(
        project: Project,
        indexPath: String,
        classFqn: String,
        methodName: String?,
        sourceId: String?,
    ) {
        val vFile = LocalFileSystem.getInstance().findFileByPath(indexPath) ?: return
        val psiFile = ApplicationManager.getApplication()
            .runReadAction(Computable { PsiManager.getInstance(project).findFile(vFile) }) ?: return

        val request = object : OpenInBrowserRequest(psiFile, true) {
            override val element: PsiElement = psiFile
        }

        try {
            val baseUrl = WebBrowserService.getInstance()
                .getUrlsToOpen(request, false)
                .firstOrNull()?.toExternalForm() ?: return

            val finalUrl = baseUrl.substringBefore('#') + buildKensaRoute(classFqn, methodName, sourceId)
            BrowserLauncher.instance.browse(finalUrl, null, project)
        } catch (ex: Exception) {
            thisLogger().error(ex)
        }
    }
}
