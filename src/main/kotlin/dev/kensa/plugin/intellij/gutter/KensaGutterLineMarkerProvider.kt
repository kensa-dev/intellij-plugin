package dev.kensa.plugin.intellij.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.ActionGroup
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
        // Capture the project once, while `element` is valid. The click / popup callbacks below run
        // later (the popup one is invoked speculatively during highlighting by
        // GutterIntentionMenuContributor), by which time `element` may be invalidated — so they must
        // never dereference it, or `element.project` throws PsiInvalidElementAccessException.
        val project = element.project
        if (!project.service<KensaSettings>().state.showGutterIcons) return null

        val target = resolveKensaTarget(element) ?: return null
        val fileSourceId = KensaSourceSetResolver.resolve(element)
        if (localReportPath(project, target.classFqn, fileSourceId) == null) return null
        val icon = iconFor(project, target.classFqn, target.methodName, fileSourceId) ?: return null

        val hasCi = ciUrl(project, target.classFqn, target.methodName) != null
        val tooltip = if (hasCi) "Open Kensa report  (right-click for CI report)" else "Open Kensa report"

        return object : LineMarkerInfo<PsiElement>(
            element,
            element.textRange,
            icon,
            { tooltip },
            // Left-click always opens the local report — one click, no chooser.
            { mouseEvent, _ ->
                KensaReportOpener.openLocal(mouseEvent, project, target.classFqn, target.methodName, fileSourceId)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { tooltip }
        ) {
            // Right-click exposes the Local / CI chooser; CI is only present when a template is set.
            // Uses only the captured project/target/fileSourceId — never `element` — because the
            // menu contributor calls this during highlighting, when the element may be invalid.
            override fun createGutterRenderer(): GutterIconRenderer =
                object : LineMarkerGutterIconRenderer<PsiElement>(this) {
                    override fun getPopupMenuActions(): ActionGroup =
                        buildGutterReportActions(project, target, fileSourceId)
                }
        }
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
