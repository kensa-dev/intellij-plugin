package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import dev.kensa.plugin.intellij.KensaBundle
import dev.kensa.plugin.intellij.agentskills.InstallKensaAgentSkillsAction
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.net.URI

class KensaSettingsConfigurable(private val project: Project) : BoundConfigurable("Kensa") {

    companion object {
        private const val SAMPLE_CLASS = "com.example.MyTest"
        private const val SAMPLE_METHOD = "shouldDoSomething"
    }

    override fun createPanel() = panel {
        group("Output") {
            row("Directory name:") {
                textField()
                    .bindText(
                        getter = { project.service<KensaSettings>().state.outputDirName ?: "" },
                        setter = { project.service<KensaSettings>().state.outputDirName = it.ifBlank { null } }
                    )
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Directory written by Kensa (default: <b>kensa-output</b>). Leave blank to use the default.")
            }
        }
        group("Editor") {
            row {
                checkBox("Show gutter icons on Kensa test classes and methods")
                    .bindSelected(
                        getter = { project.service<KensaSettings>().state.showGutterIcons },
                        setter = { project.service<KensaSettings>().state.showGutterIcons = it }
                    )
                    .comment("Adds a clickable icon in the editor gutter to open Kensa reports. Requires reopening the file.")
            }
        }
        group("AI Agent Skills") {
            row {
                button(KensaBundle.message("agentSkills.settings.button")) {
                    InstallKensaAgentSkillsAction.runInstall(project)
                }
                    .comment(KensaBundle.message("agentSkills.settings.description"))
            }
        }
        group("CI Report Integration") {
            val previewLabel = JBLabel(" ")
            val defaultForeground = previewLabel.foreground
            row("Report URL template:") {
                textArea()
                    .bindText(
                        getter = { project.service<KensaSettings>().state.ciReportUrlTemplate ?: "" },
                        setter = { project.service<KensaSettings>().state.ciReportUrlTemplate = it.ifBlank { null } }
                    )
                    .rows(5)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Available tokens: <b>{projectName}</b>, <b>{testClass}</b>, <b>{testMethod}</b>, <b>{simpleClassName}</b>, <b>{packageName}</b>")
                    .applyToComponent {
                        document.addDocumentListener(object : DocumentListener {
                            override fun insertUpdate(e: DocumentEvent) = refresh()
                            override fun removeUpdate(e: DocumentEvent) = refresh()
                            override fun changedUpdate(e: DocumentEvent) = refresh()
                            private fun refresh() = updatePreview(previewLabel, defaultForeground, text)
                        })
                        updatePreview(previewLabel, defaultForeground, text)
                    }
            }
            row("Preview:") {
                cell(previewLabel)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("Sample values: class=<b>$SAMPLE_CLASS</b>, method=<b>$SAMPLE_METHOD</b>")
            }
        }
    }

    private fun updatePreview(label: JBLabel, defaultForeground: java.awt.Color, template: String) {
        val url = project.service<KensaSettings>().previewUrl(project, template, SAMPLE_CLASS, SAMPLE_METHOD)
        if (url.isNullOrBlank()) {
            label.text = " "
            label.foreground = defaultForeground
            label.toolTipText = null
            return
        }
        label.text = url
        val valid = runCatching { URI(url).toURL() }.isSuccess
        if (valid) {
            label.foreground = defaultForeground
            label.toolTipText = null
        } else {
            label.foreground = JBColor.RED
            label.toolTipText = "Template does not produce a valid URL."
        }
    }
}
