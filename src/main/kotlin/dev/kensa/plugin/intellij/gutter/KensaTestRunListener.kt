package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.kensa.plugin.intellij.execution.KensaEngagementNotifier
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import dev.kensa.plugin.intellij.settings.KensaEngagementService
import dev.kensa.plugin.intellij.settings.KensaSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class KensaTestRunListener(
    private val project: Project,
) : SMTRunnerEventsAdapter() {

    companion object {
        // Test seam: tests pre-set this on the root proxy to pin the descriptor. In
        // production it is left unset so the descriptor is resolved from the live
        // selectedContent when testing finishes (see resolveDescriptor).
        internal val DESCRIPTOR_KEY: Key<RunContentDescriptor> = Key.create("kensa.runContentDescriptor")
        internal val RECORDED_CLASSES_KEY: Key<MutableSet<String>> = Key.create("kensa.recordedClasses")
    }

    override fun onTestFinished(test: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(test.locationUrl ?: return) ?: return
        methodName ?: return
        val baseMethod = methodName.substringBefore('[').takeIf { it.isNotBlank() } ?: return
        project.service<KensaTestResultsService>().updateMethod(classFqn, baseMethod, test.toStatus() ?: return)
        rememberClass(test, classFqn)
    }

    override fun onSuiteFinished(suite: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(suite.locationUrl ?: return) ?: return
        if (methodName != null) return // only handle class-level suites
        project.service<KensaTestResultsService>().updateClass(classFqn, suite.toStatus() ?: return)
        rememberClass(suite, classFqn)
    }

    override fun onTestingFinished(testsRoot: SMRootTestProxy) {
        // Bind the run's classes to the descriptor LATE — once, here, when the test tab is
        // reliably the selected content. Capturing earlier (onTestingStarted) is unreliable
        // for Gradle-delegated runs, whose first phase lives in the Build window, so the
        // descriptor seen at start can be null or the previous run's tab. Filing classes
        // under that stale descriptor is what made the run-window icon vanish in some
        // projects: the toolbar's update() resolves the live (correct) selectedContent and
        // would never find them.
        val descriptor = resolveDescriptor(testsRoot) ?: return
        val registry = project.service<KensaRunTabRegistry>()

        val recorded = testsRoot.getUserData(RECORDED_CLASSES_KEY)?.toSet().orEmpty()
        recorded.forEach { registry.recordClass(descriptor, it) }

        // Cheap exit for non-Kensa run tabs: nothing recorded means no project-wide scan.
        if (registry.classesFor(descriptor).isEmpty()) return

        project.basePath?.let { base ->
            val outputDir = project.service<KensaSettings>().effectiveOutputDirName
            KensaIndexLoader.scan(project, File(base), outputDir)
        }

        val indexPath = registry.indexPathFor(descriptor)
        // Report not on disk yet (Kensa flushes on JVM shutdown); the 5s poll will load it
        // and refresh the toolbar. The class binding above already persists in the registry.
        if (indexPath == null) return

        val engagementService = ApplicationManager.getApplication().service<KensaEngagementService>()
        val state = engagementService.state

        if (state.dismissed) return

        state.runsSinceLastPrompt++
        if (state.runsSinceLastPrompt >= 5) {
            state.runsSinceLastPrompt = 0
            KensaEngagementNotifier.show(project)
        }
    }

    private fun rememberClass(proxy: SMTestProxy, classFqn: String) {
        val root = proxy.findRoot() ?: return
        val set = root.getUserData(RECORDED_CLASSES_KEY)
            ?: ConcurrentHashMap.newKeySet<String>().also { root.putUserData(RECORDED_CLASSES_KEY, it) }
        set.add(classFqn)
    }

    private fun resolveDescriptor(testsRoot: SMRootTestProxy): RunContentDescriptor? =
        testsRoot.getUserData(DESCRIPTOR_KEY)
            ?: RunContentManager.getInstance(project).selectedContent

    private fun SMTestProxy.findRoot(): SMRootTestProxy? {
        var node: SMTestProxy? = this
        while (node != null) {
            if (node is SMRootTestProxy) return node
            node = node.parent
        }
        return null
    }

    private fun SMTestProxy.toStatus(): TestStatus? = when {
        isPassed -> TestStatus.PASSED
        isDefect -> TestStatus.FAILED
        else -> null
    }

    private fun parseLocation(url: String): Pair<String, String?>? {
        val path = when {
            url.startsWith("java:test://") -> url.removePrefix("java:test://")
            else -> return null
        }
        val slashIdx = path.lastIndexOf('/')
        return if (slashIdx >= 0) {
            path.substring(0, slashIdx) to path.substring(slashIdx + 1).takeIf { it.isNotEmpty() }
        } else {
            path to null
        }
    }
}
