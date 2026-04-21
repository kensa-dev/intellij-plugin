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
    fun resultsUpdated()
}

@Service(PROJECT)
class KensaTestResultsService(private val project: Project) {

    companion object {
        val KENSA_RESULTS_TOPIC: Topic<KensaResultsListener> =
            Topic.create("Kensa Results", KensaResultsListener::class.java)
    }

    private val methodResults = ConcurrentHashMap<String, TestStatus>()
    private val classResults = ConcurrentHashMap<String, TestStatus>()
    private val classIndexPaths = ConcurrentHashMap<String, String>()
    private val indexPathUpdatedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    var latestIndexPath: String? = null

    data class CountSnapshot(val passed: Int, val failed: Int, val ignored: Int) {
        val total: Int get() = passed + failed + ignored
        val isEmpty: Boolean get() = total == 0
    }

    fun getMethodStatus(classFqn: String, methodName: String): TestStatus? =
        methodResults["$classFqn#$methodName"]

    fun getClassStatus(classFqn: String): TestStatus? =
        classResults[classFqn]

    fun getIndexPath(classFqn: String): String? =
        classIndexPaths[classFqn]

    fun allIndexPaths(): Set<String> = classIndexPaths.values.toSet()

    fun indexPathsByRecency(): List<String> =
        indexPathUpdatedAt.entries
            .sortedByDescending { it.value }
            .map { it.key }

    fun snapshot(): CountSnapshot = snapshotFor(classResults.keys)

    fun snapshotForIndex(indexHtmlPath: String): CountSnapshot =
        snapshotFor(classesForIndex(indexHtmlPath))

    private fun snapshotFor(classFqns: Collection<String>): CountSnapshot {
        var passed = 0; var failed = 0; var ignored = 0
        for (classFqn in classFqns) {
            val methods = methodsForClass(classFqn)
            if (methods.isEmpty()) {
                when (classResults[classFqn]) {
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
        classIndexPaths.entries
            .filter { it.value == indexHtmlPath }
            .map { it.key }
            .sorted()

    fun methodsForClass(classFqn: String): Map<String, TestStatus> =
        methodResults.entries
            .filter { it.key.startsWith("$classFqn#") }
            .associate { it.key.removePrefix("$classFqn#") to it.value }

    fun clearForIndexHtml(indexHtmlPath: String) {
        val staleClasses = classIndexPaths.entries
            .filter { it.value == indexHtmlPath }
            .map { it.key }
        staleClasses.forEach { classFqn ->
            classResults.remove(classFqn)
            classIndexPaths.remove(classFqn)
            methodResults.keys.removeIf { it.startsWith("$classFqn#") }
        }
        if (classIndexPaths.values.none { it == indexHtmlPath }) {
            indexPathUpdatedAt.remove(indexHtmlPath)
        }
    }

    fun pruneMissingFiles() {
        val stale = classIndexPaths.values.toSet().filter { !java.io.File(it).exists() }
        if (stale.isEmpty()) return
        stale.forEach { clearForIndexHtml(it) }
        if (latestIndexPath?.let { !java.io.File(it).exists() } == true) {
            latestIndexPath = indexPathsByRecency().firstOrNull()
        }
        refreshMarkers()
    }

    fun updateFromIndex(
        classFqn: String,
        classStatus: TestStatus?,
        indexHtmlPath: String,
        methodStatuses: Map<String, TestStatus>
    ) {
        val effectiveClassStatus = classStatus
            ?: if (methodStatuses.values.any { it == TestStatus.FAILED }) TestStatus.FAILED
            else if (methodStatuses.values.any { it == TestStatus.PASSED }) TestStatus.PASSED
            else if (methodStatuses.values.isNotEmpty()) TestStatus.IGNORED
            else null
        if (effectiveClassStatus != null) classResults[classFqn] = effectiveClassStatus
        classIndexPaths[classFqn] = indexHtmlPath
        indexPathUpdatedAt[indexHtmlPath] = System.currentTimeMillis()
        latestIndexPath = indexHtmlPath
        methodStatuses.forEach { (method, status) ->
            methodResults["$classFqn#$method"] = status
        }
        refreshMarkers()
    }

    // Called by SMTRunnerEventsListener for real-time updates during a run
    fun updateMethod(classFqn: String, methodName: String, status: TestStatus) {
        methodResults["$classFqn#$methodName"] = status
        refreshMarkers()
    }

    fun updateClass(classFqn: String, status: TestStatus) {
        classResults[classFqn] = status
        refreshMarkers()
    }

    fun refreshMarkers() {
        invokeLater {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart("Kensa test results updated")
                project.messageBus.syncPublisher(KENSA_RESULTS_TOPIC).resultsUpdated()
            }
        }
    }
}
