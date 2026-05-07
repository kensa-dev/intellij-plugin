package dev.kensa.plugin.intellij.agentskills

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.kensa.plugin.intellij.KensaBundle
import javax.swing.JComponent

class InstallAgentSkillsDialog(project: Project) : DialogWrapper(project, true) {

    private val combo = ComboBox(SkillTarget.values())

    init {
        title = KensaBundle.message("agentSkills.dialog.title")
        combo.selectedItem = SkillTarget.COPILOT_SCOPED
        init()
    }

    val selectedTarget: SkillTarget
        get() = combo.item

    override fun createCenterPanel(): JComponent = panel {
        row(KensaBundle.message("agentSkills.dialog.format")) {
            cell(combo).align(AlignX.FILL).resizableColumn()
        }
        row {
            comment(KensaBundle.message("agentSkills.dialog.description"))
        }
    }
}
