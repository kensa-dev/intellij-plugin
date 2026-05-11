package dev.kensa.plugin.intellij.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import dev.kensa.plugin.intellij.settings.KensaSettings
import javax.swing.Icon

class KensaGutterLineMarkerProvider : LineMarkerProvider {

    private val iconPass: Icon    = IconLoader.getIcon("/icons/kensa-gutter-pass.svg",    KensaGutterLineMarkerProvider::class.java)
    private val iconFail: Icon    = IconLoader.getIcon("/icons/kensa-gutter-fail.svg",    KensaGutterLineMarkerProvider::class.java)
    private val iconIgnored: Icon = IconLoader.getIcon("/icons/kensa-gutter-ignored.svg", KensaGutterLineMarkerProvider::class.java)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!element.project.service<KensaSettings>().state.showGutterIcons) return null

        val target = resolveKensaTarget(element) ?: return null
        val fileSourceId = KensaSourceSetResolver.resolve(element)
        if (localReportPath(element.project, target.classFqn, fileSourceId) == null) return null
        val icon = iconFor(element.project, target.classFqn, target.methodName, fileSourceId) ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { "Open Kensa report" },
            { mouseEvent, psiElement ->
                KensaReportOpener.openLocal(mouseEvent, psiElement.project, target.classFqn, target.methodName, fileSourceId)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Open Kensa report" }
        )
    }

    private fun iconFor(project: Project, classFqn: String, methodName: String?, fileSourceId: String?): Icon? {
        val results = project.service<KensaTestResultsService>()
        val status = if (methodName != null) results.getMethodStatus(classFqn, methodName, fileSourceId)
                     else results.getClassStatus(classFqn, fileSourceId)
        return when (status) {
            TestStatus.PASSED  -> iconPass
            TestStatus.FAILED  -> iconFail
            TestStatus.IGNORED -> iconIgnored
            null               -> null
        }
    }
}
