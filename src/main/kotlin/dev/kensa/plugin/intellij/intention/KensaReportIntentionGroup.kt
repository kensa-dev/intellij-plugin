package dev.kensa.plugin.intellij.intention

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.IntentionActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.Icon

class KensaReportIntentionGroup : IntentionActionGroup<IntentionAction>(
    listOf(KensaOpenLocalReportIntentionAction(), KensaOpenCiReportIntentionAction())
), HighPriorityAction, Iconable {

    override fun getFamilyName() = "Kensa"
    override fun getGroupText(actions: List<IntentionAction>) = "Open Kensa report"

    override fun chooseAction(
        project: Project,
        editor: Editor,
        file: PsiFile,
        actions: List<IntentionAction>,
        invokeAction: (IntentionAction) -> Unit
    ) {
        if (actions.size == 1) {
            invokeAction(actions.first())
            return
        }
        val logo = IconLoader.getIcon("/icons/logo.svg", KensaReportIntentionGroup::class.java)
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(actions)
            .setTitle("Kensa Report")
            .setRenderer(listCellRenderer {
                icon(logo)
                text(value.text)
            })
            .setItemChosenCallback { chosen -> invokeAction(chosen) }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    override fun getIcon(flags: Int): Icon =
        IconLoader.getIcon("/icons/logo.svg", KensaReportIntentionGroup::class.java)
}
