package dev.kensa.plugin.intellij.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.util.concurrent.ConcurrentHashMap

enum class TestStatus { PASSED, FAILED, IGNORED }

fun interface KensaResultsListener {
    fun resultsUpdated(indexHtmlPath: String?)
}

data class IndexEntry(
    val indexHtmlPath: String,
    val sourceId: String?,
    val bundleDir: String,
)

data class ResultKey(val classFqn: String, val sourceId: String?)

@Service(PROJECT)
class KensaTestResultsService(private val project: Project) {

    companion object {
        val KENSA_RESULTS_TOPIC: Topic<KensaResultsListener> =
            Topic.create("Kensa Results", KensaResultsListener::class.java)
    }

    private data class MethodKey(val classFqn: String, val methodName: String, val sourceId: String?)

    private val methodResults = ConcurrentHashMap<MethodKey, TestStatus>()
    private val classResults = ConcurrentHashMap<ResultKey, TestStatus>()
    private val indexEntries = ConcurrentHashMap<ResultKey, IndexEntry>()
    private val indexPathUpdatedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    var latestIndexPath: String? = null

    data class CountSnapshot(val passed: Int, val failed: Int, val ignored: Int) {
        val total: Int get() = passed + failed + ignored
        val isEmpty: Boolean get() = total == 0
    }

    fun getMethodStatus(classFqn: String, methodName: String): TestStatus? =
        latestMethodEntry(classFqn, methodName)

    fun getMethodStatus(classFqn: String, methodName: String, fileSourceId: String?): TestStatus? {
        if (fileSourceId == null) return getMethodStatus(classFqn, methodName)
        methodResults[MethodKey(classFqn, methodName, fileSourceId)]?.let { return it }
        return methodResults[MethodKey(classFqn, methodName, null)]
    }

    fun getClassStatus(classFqn: String): TestStatus? = latestClassEntry(classFqn)

    fun getClassStatus(classFqn: String, fileSourceId: String?): TestStatus? {
        if (fileSourceId == null) return getClassStatus(classFqn)
        classResults[ResultKey(classFqn, fileSourceId)]?.let { return it }
        return classResults[ResultKey(classFqn, null)]
    }

    fun getIndexPath(classFqn: String): String? = latestIndexEntry(classFqn)?.indexHtmlPath

    fun getIndexEntry(classFqn: String, fileSourceId: String?): IndexEntry? {
        if (fileSourceId == null) return latestIndexEntry(classFqn)
        indexEntries[ResultKey(classFqn, fileSourceId)]?.let { return it }
        return indexEntries[ResultKey(classFqn, null)]
    }

    private fun latestIndexEntry(classFqn: String): IndexEntry? =
        indexEntries.entries
            .filter { it.key.classFqn == classFqn }
            .maxByOrNull { indexPathUpdatedAt[it.value.indexHtmlPath] ?: 0L }
            ?.value

    private fun latestClassEntry(classFqn: String): TestStatus? =
        classResults.entries
            .filter { it.key.classFqn == classFqn }
            .maxByOrNull {
                val path = indexEntries[it.key]?.indexHtmlPath ?: return@maxByOrNull 0L
                indexPathUpdatedAt[path] ?: 0L
            }
            ?.value

    private fun latestMethodEntry(classFqn: String, methodName: String): TestStatus? =
        methodResults.entries
            .filter { it.key.classFqn == classFqn && it.key.methodName == methodName }
            .maxByOrNull {
                val path = indexEntries[ResultKey(it.key.classFqn, it.key.sourceId)]?.indexHtmlPath
                    ?: return@maxByOrNull 0L
                indexPathUpdatedAt[path] ?: 0L
            }
            ?.value

    fun allIndexPaths(): Set<String> = indexEntries.values.map { it.indexHtmlPath }.toSet()

    fun indexPathsByRecency(): List<String> =
        indexPathUpdatedAt.entries
            .sortedByDescending { it.value }
            .map { it.key }

    fun snapshot(): CountSnapshot = snapshotFor(classResults.keys.map { it.classFqn }.distinct())

    fun snapshotForIndex(indexHtmlPath: String): CountSnapshot =
        snapshotFor(classesForIndex(indexHtmlPath))

    private fun snapshotFor(classFqns: Collection<String>): CountSnapshot {
        var passed = 0; var failed = 0; var ignored = 0
        for (classFqn in classFqns) {
            val methods = methodsForClass(classFqn)
            if (methods.isEmpty()) {
                when (getClassStatus(classFqn)) {
                    TestStatus.PASSED -> passed++
                    TestStatus.FAILED -> failed++
                    TestStatus.IGNORED -> ignored++
                    null -> {}
                }
            } else {
                for (status in methods.values) when (status) {
                    TestStatus.PASSED -> passed++
                    TestStatus.FAILED -> failed++
                    TestStatus.IGNORED -> ignored++
                }
            }
        }
        return CountSnapshot(passed, failed, ignored)
    }

    fun classesForIndex(indexHtmlPath: String): List<String> =
        indexEntries.entries
            .filter { it.value.indexHtmlPath == indexHtmlPath }
            .map { it.key.classFqn }
            .distinct()
            .sorted()

    fun methodsForClass(classFqn: String): Map<String, TestStatus> =
        methodResults.entries
            .filter { it.key.classFqn == classFqn }
            .associate { it.key.methodName to it.value }

    fun clearForIndexHtml(indexHtmlPath: String) {
        clearForBundle(indexHtmlPath, sourceId = null, sourceMatchesAny = true)
    }

    fun clearForBundle(indexHtmlPath: String, sourceId: String?) {
        clearForBundle(indexHtmlPath, sourceId, sourceMatchesAny = false)
    }

    private fun clearForBundle(indexHtmlPath: String, sourceId: String?, sourceMatchesAny: Boolean) {
        val staleKeys = indexEntries.entries
            .filter {
                it.value.indexHtmlPath == indexHtmlPath &&
                    (sourceMatchesAny || it.key.sourceId == sourceId)
            }
            .map { it.key }
        staleKeys.forEach { key ->
            classResults.remove(key)
            indexEntries.remove(key)
            methodResults.keys.removeIf { it.classFqn == key.classFqn && it.sourceId == key.sourceId }
        }
        if (indexEntries.values.none { it.indexHtmlPath == indexHtmlPath }) {
            indexPathUpdatedAt.remove(indexHtmlPath)
        }
    }

    fun pruneMissingFiles() {
        val staleByPath = indexEntries.values
            .filter { entry ->
                !java.io.File(entry.indexHtmlPath).exists() || !java.io.File(entry.bundleDir).exists()
            }
            .toSet()
        if (staleByPath.isEmpty()) return
        staleByPath.forEach { entry -> clearForBundle(entry.indexHtmlPath, entry.sourceId) }
        if (latestIndexPath?.let { !java.io.File(it).exists() } == true) {
            latestIndexPath = indexPathsByRecency().firstOrNull()
        }
        refreshMarkers()
    }

    fun updateFromIndex(
        classFqn: String,
        classStatus: TestStatus?,
        indexHtmlPath: String,
        methodStatuses: Map<String, TestStatus>,
    ) = updateFromIndex(classFqn, sourceId = null, classStatus, indexHtmlPath, indexHtmlPath.parentDirOrSelf(), methodStatuses)

    fun updateFromIndex(
        classFqn: String,
        sourceId: String?,
        classStatus: TestStatus?,
        indexHtmlPath: String,
        bundleDir: String,
        methodStatuses: Map<String, TestStatus>,
    ) {
        val key = ResultKey(classFqn, sourceId)
        val effectiveClassStatus = classStatus
            ?: if (methodStatuses.values.any { it == TestStatus.FAILED }) TestStatus.FAILED
            else if (methodStatuses.values.any { it == TestStatus.PASSED }) TestStatus.PASSED
            else if (methodStatuses.values.isNotEmpty()) TestStatus.IGNORED
            else null
        if (effectiveClassStatus != null) classResults[key] = effectiveClassStatus
        indexEntries[key] = IndexEntry(indexHtmlPath, sourceId, bundleDir)
        indexPathUpdatedAt[indexHtmlPath] = System.currentTimeMillis()
        latestIndexPath = indexHtmlPath
        methodStatuses.forEach { (method, status) ->
            methodResults[MethodKey(classFqn, method, sourceId)] = status
        }
        refreshMarkers(indexHtmlPath)
    }

    fun updateMethod(classFqn: String, methodName: String, status: TestStatus) {
        methodResults[MethodKey(classFqn, methodName, null)] = status
        refreshMarkers()
    }

    fun updateClass(classFqn: String, status: TestStatus) {
        classResults[ResultKey(classFqn, null)] = status
        refreshMarkers()
    }

    fun refreshMarkers(indexHtmlPath: String? = null) {
        invokeLater {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart("Kensa test results updated")
                project.messageBus.syncPublisher(KENSA_RESULTS_TOPIC).resultsUpdated(indexHtmlPath)
            }
        }
    }
}

private fun String.parentDirOrSelf(): String = java.io.File(this).parentFile?.absolutePath ?: this
