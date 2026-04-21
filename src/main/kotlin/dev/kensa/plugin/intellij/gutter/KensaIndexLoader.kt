package dev.kensa.plugin.intellij.gutter

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object KensaIndexLoader {

    private val gson = Gson()
    private val log = thisLogger()

    fun loadFromFile(project: Project, indicesJson: VirtualFile) {
        val parentPath = indicesJson.parent?.path ?: return
        val json = indicesJson.inputStream.reader().use { it.readText() }
        loadJson(project, parentPath, json, indicesJson.path)
    }

    fun loadFromFile(project: Project, indicesJson: File) {
        val parentPath = indicesJson.parentFile?.absolutePath ?: return
        val json = indicesJson.readText()
        loadJson(project, parentPath, json, indicesJson.absolutePath)
    }

    fun scan(project: Project, root: File, outputDirName: String) {
        root.walkTopDown()
            .filter { it.name == "indices.json" && it.parentFile?.name == outputDirName }
            .forEach { loadFromFile(project, it) }
    }

    private fun loadJson(project: Project, parentPath: String, json: String, sourceForLog: String) {
        val indexHtmlPath = "$parentPath/index.html"
        try {
            val root = gson.fromJson(json, KensaIndicesRoot::class.java) ?: return

            val service = project.service<KensaTestResultsService>()
            service.clearForIndexHtml(indexHtmlPath)
            root.indices?.forEach { entry ->
                val classFqn = entry.testClass ?: return@forEach
                val classStatus = entry.state?.toTestStatus()
                val methodStatuses = entry.children
                    ?.mapNotNull { child ->
                        val method = child.testMethod ?: return@mapNotNull null
                        val status = child.state?.toTestStatus() ?: return@mapNotNull null
                        method to status
                    }
                    ?.toMap()
                    ?: emptyMap()

                service.updateFromIndex(classFqn, classStatus, indexHtmlPath, methodStatuses)
            }
        } catch (e: Exception) {
            log.warn("Failed to parse Kensa indices.json at $sourceForLog", e)
        }
    }

    private fun String.toTestStatus(): TestStatus? = when (this) {
        "Passed" -> TestStatus.PASSED
        "Failed" -> TestStatus.FAILED
        "Ignored", "Disabled", "Skipped" -> TestStatus.IGNORED
        else -> null
    }
}

private data class KensaIndicesRoot(
    @SerializedName("indices") val indices: List<KensaIndexEntry>?
)

private data class KensaIndexEntry(
    @SerializedName("testClass") val testClass: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("children") val children: List<KensaMethodEntry>?
)

private data class KensaMethodEntry(
    @SerializedName("testMethod") val testMethod: String?,
    @SerializedName("state") val state: String?
)
