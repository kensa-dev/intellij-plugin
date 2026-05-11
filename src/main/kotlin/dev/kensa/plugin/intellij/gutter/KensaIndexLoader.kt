package dev.kensa.plugin.intellij.gutter

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

const val KENSA_SITE_DIR_NAME = "kensa-site"

object KensaIndexLoader {

    private val gson = Gson()
    private val log = thisLogger()

    fun loadFromFile(project: Project, indicesJson: VirtualFile) {
        val classification = classify(File(indicesJson.path)) ?: return
        val json = indicesJson.inputStream.reader().use { it.readText() }
        loadJson(project, classification, json, indicesJson.path)
    }

    fun loadFromFile(project: Project, indicesJson: File) {
        val classification = classify(indicesJson) ?: return
        val json = indicesJson.readText()
        loadJson(project, classification, json, indicesJson.absolutePath)
    }

    fun scan(project: Project, root: File, outputDirName: String) {
        root.walkTopDown()
            .filter { it.name == "indices.json" && isKensaIndicesJson(it, outputDirName) }
            .forEach { loadFromFile(project, it) }
    }

    fun isKensaIndicesJson(indicesJson: File, outputDirName: String): Boolean =
        classify(indicesJson, outputDirName) != null

    private data class BundleClassification(
        val indexHtmlPath: String,
        val sourceId: String?,
        val bundleDir: String,
    )

    private fun classify(indicesJson: File, outputDirName: String? = null): BundleClassification? {
        val parent = indicesJson.parentFile ?: return null
        val grandparent = parent.parentFile
        val siteRoot = grandparent?.parentFile

        if (grandparent?.name == "sources" && siteRoot?.name == KENSA_SITE_DIR_NAME) {
            return BundleClassification(
                indexHtmlPath = File(siteRoot, "index.html").absolutePath,
                sourceId = parent.name,
                bundleDir = parent.absolutePath,
            )
        }

        if (outputDirName == null || parent.name == outputDirName) {
            return BundleClassification(
                indexHtmlPath = File(parent, "index.html").absolutePath,
                sourceId = null,
                bundleDir = parent.absolutePath,
            )
        }

        return null
    }

    private fun loadJson(project: Project, classification: BundleClassification, json: String, sourceForLog: String) {
        try {
            val root = gson.fromJson(json, KensaIndicesRoot::class.java) ?: return

            val service = project.service<KensaTestResultsService>()
            service.clearForBundle(classification.indexHtmlPath, classification.sourceId)
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

                service.updateFromIndex(
                    classFqn,
                    classification.sourceId,
                    classStatus,
                    classification.indexHtmlPath,
                    classification.bundleDir,
                    methodStatuses,
                )
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
