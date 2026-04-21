package dev.kensa.plugin.intellij.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

@Service(Service.Level.PROJECT)
@State(
    name = "KensaSettings",
    storages = [Storage("kensa.xml")]
)
class KensaSettings : SimplePersistentStateComponent<KensaSettingsState>(KensaSettingsState()) {

    val effectiveOutputDirName: String
        get() = state.outputDirName?.takeIf { it.isNotBlank() } ?: "kensa-output"

    fun resolveUrl(project: Project, classFqn: String, methodName: String?): String? {
        val template = state.ciReportUrlTemplate?.takeIf { it.isNotBlank() } ?: return null
        return substitute(project.name, template, classFqn, methodName)
    }

    fun previewUrl(project: Project, template: String, classFqn: String, methodName: String?): String? {
        val trimmed = template.takeIf { it.isNotBlank() } ?: return null
        return substitute(project.name, trimmed, classFqn, methodName)
    }

    private fun substitute(projectName: String, template: String, classFqn: String, methodName: String?): String {
        val simpleClassName = classFqn.substringAfterLast('.')
        val packageName = classFqn.substringBeforeLast('.', missingDelimiterValue = "")

        return template
            .replace("{projectName}", projectName)
            .replace("{testClass}", classFqn)
            .replace("{simpleClassName}", simpleClassName)
            .replace("{packageName}", packageName)
            .let { url ->
                if (methodName.isNullOrBlank()) {
                    url.replace(Regex("[?&]method=\\{testMethod}"), "")
                        .replace("{testMethod}", "")
                } else {
                    url.replace("{testMethod}", URLEncoder.encode(methodName, UTF_8))
                }
            }
    }
}

class KensaSettingsState : BaseState() {
    var ciReportUrlTemplate by string()
    var showGutterIcons by property(false)
    var outputDirName by string()
}
