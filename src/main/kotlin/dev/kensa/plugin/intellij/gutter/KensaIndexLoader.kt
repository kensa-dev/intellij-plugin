package dev.kensa.plugin.intellij.gutter

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(PROJECT)
class KensaIndexLoader(private val project: Project) {

    private val log = thisLogger()

    // Per-project on purpose: this gate must die with the project. If it outlived the project
    // (as it did when the loader was a singleton), reopening a project in the same IDE session
    // would skip every unchanged indices.json and leave the fresh results service empty.
    private val lastLoadedMtimes = ConcurrentHashMap<String, Long>()

    fun loadFromFile(indicesJson: VirtualFile) {
        val classification = classify(File(indicesJson.path)) ?: return
        if (deferToUnfinishedRun(classification)) return
        val key = indicesJson.path
        val mtime = indicesJson.timeStamp
        if (!shouldLoad(key, mtime)) return
        val json = indicesJson.inputStream.reader().use { it.readText() }
        loadJson(classification, json, indicesJson.path)
    }

    fun loadFromFile(indicesJson: File) {
        val classification = classify(indicesJson) ?: return
        if (deferToUnfinishedRun(classification)) return
        val key = indicesJson.absolutePath
        val mtime = indicesJson.lastModified()
        if (!shouldLoad(key, mtime)) return
        val json = indicesJson.readText()
        loadJson(classification, json, indicesJson.absolutePath)
    }

    /**
     * kensa-core 0.9.2 writes `indices.json` before `index.html` and the rest of the report; the
     * bundle's own `run.json` says when everything is on disk. While it reports an unfinished run,
     * record the run state and defer the load — the marker's finish rewrite triggers it. Bundles
     * with no marker (pre-0.9.2) load exactly as before.
     */
    private fun deferToUnfinishedRun(classification: BundleClassification): Boolean {
        val bundleDir = File(classification.bundleDir)
        val marker = KensaRunMarker.read(bundleDir) ?: return false
        if (marker.isFinished) return false
        recordRunState(bundleDir, marker, ::defaultIsPidAlive)
        return true
    }

    fun probeRunMarker(
        bundleDir: File,
        isPidAlive: (Long) -> Boolean = ::defaultIsPidAlive,
    ) {
        val service = project.service<KensaTestResultsService>()
        val marker = KensaRunMarker.read(bundleDir)
        if (marker == null) {
            service.clearRunState(bundleDir.absolutePath)
            return
        }
        if (marker.isFinished) {
            service.clearRunState(bundleDir.absolutePath)
            File(bundleDir, "indices.json").let { if (it.isFile) loadFromFile(it) }
        } else {
            recordRunState(bundleDir, marker, isPidAlive)
        }
    }

    /** Re-probe every active run: picks up crashed processes and fresh class counts without VFS events. */
    fun refreshRunStates(
        isPidAlive: (Long) -> Boolean = ::defaultIsPidAlive,
    ) {
        project.service<KensaTestResultsService>().activeRuns().forEach { entry ->
            probeRunMarker(File(entry.bundleDir), isPidAlive)
        }
    }

    private fun defaultIsPidAlive(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private fun recordRunState(bundleDir: File, marker: KensaRunMarker, isPidAlive: (Long) -> Boolean) {
        project.service<KensaTestResultsService>().updateRunState(
            RunStateEntry(
                bundleDir = bundleDir.absolutePath,
                phase = marker.phase(isPidAlive),
                startedAt = marker.startedAt,
                pid = marker.pid,
                classesWritten = KensaRunMarker.classesWritten(bundleDir),
            )
        )
    }

    fun scan(root: File, outputDirName: String) {
        findIndices(root, outputDirName).forEach { loadFromFile(it) }
    }

    /**
     * Cheaply probe a single build/output dir (e.g. `build`, `target`) for Kensa reports without
     * walking its full tree — build dirs are huge, so a walk would reintroduce the CPU cost we are
     * avoiding. A report only ever appears at a fixed shape:
     *   - single: `<buildDir>/<reportDir>/indices.json`
     *   - site:   `<buildDir>/<reportDir>/sources/<sourceId>/indices.json`
     * so we look only at those positions (a couple of directory listings), never inside `classes/`,
     * `tmp/`, etc. Loads are mtime-gated, so repeated probes of unchanged reports are no-ops.
     */
    fun probeBuildDir(buildDir: File, outputDirName: String) {
        val children = buildDir.listFiles { f -> f.isDirectory } ?: return
        children.forEach { reportDir ->
            File(reportDir, "indices.json").let { single ->
                if (single.isFile && isKensaIndicesJson(single, outputDirName)) loadFromFile(single)
            }
            if (File(reportDir, "run.json").isFile && isKensaBundleDir(reportDir, outputDirName)) {
                probeRunMarker(reportDir)
            }
            File(reportDir, "sources").listFiles { f -> f.isDirectory }?.forEach { source ->
                File(source, "indices.json").let { siteIndices ->
                    if (siteIndices.isFile && isKensaIndicesJson(siteIndices, outputDirName)) {
                        loadFromFile(siteIndices)
                    }
                }
                if (File(source, "run.json").isFile && isKensaBundleDir(source, outputDirName)) {
                    probeRunMarker(source)
                }
            }
        }
    }

    private fun shouldLoad(key: String, mtime: Long): Boolean {
        val previous = lastLoadedMtimes[key]
        if (previous != null && mtime <= previous) return false
        lastLoadedMtimes[key] = mtime
        return true
    }

    fun pruneLoadedMtimes(stillExists: (String) -> Boolean) {
        lastLoadedMtimes.keys.removeIf { !stillExists(it) }
    }

    private fun loadJson(classification: BundleClassification, json: String, sourceForLog: String) {
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
            service.notifyIndexLoaded(classification.indexHtmlPath)
            service.clearRunState(classification.bundleDir)
        } catch (e: Exception) {
            log.warn("Failed to parse Kensa indices.json at $sourceForLog", e)
        }
    }

    companion object {

        private val gson = Gson()

        // Directories that can never contain a Kensa report and are expensive to descend. `build` is
        // deliberately NOT excluded — kensa-output commonly lives under it.
        private val EXCLUDED_DIRS = setOf(".git", ".gradle", ".idea", "node_modules")

        /**
         * Recursively locate Kensa `indices.json` files under [root], skipping directories that can
         * never hold a report (`.git`, `node_modules`, …) so the walk stays cheap on large projects.
         */
        fun findIndices(root: File, outputDirName: String): List<File> =
            root.walkTopDown()
                .onEnter { it.name !in EXCLUDED_DIRS }
                .filter { it.name == "indices.json" && isKensaIndicesJson(it, outputDirName) }
                .toList()

        fun isKensaIndicesJson(indicesJson: File, outputDirName: String): Boolean =
            classify(indicesJson, outputDirName) != null

        /** Same bundle-shape check as [isKensaIndicesJson], for a directory rather than the file. */
        fun isKensaBundleDir(dir: File, outputDirName: String): Boolean =
            classify(File(dir, "indices.json"), outputDirName) != null

        private fun classify(indicesJson: File, outputDirName: String? = null): BundleClassification? {
            val parent = indicesJson.parentFile ?: return null
            val grandparent = parent.parentFile
            val siteRoot = grandparent?.parentFile

            if (grandparent?.name == "sources" && siteRoot != null) {
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
    }
}

private data class BundleClassification(
    val indexHtmlPath: String,
    val sourceId: String?,
    val bundleDir: String,
)

private fun String.toTestStatus(): TestStatus? = when (this) {
    "Passed" -> TestStatus.PASSED
    "Failed" -> TestStatus.FAILED
    "Ignored", "Disabled", "Skipped" -> TestStatus.IGNORED
    else -> null
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
