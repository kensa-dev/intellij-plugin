package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.kensa.plugin.intellij.execution.KensaEngagementNotifier
import dev.kensa.plugin.intellij.settings.KensaEngagementService
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy

class KensaTestRunListener(private val project: Project) : SMTRunnerEventsAdapter() {

    override fun onTestFinished(test: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(test.locationUrl ?: return) ?: return
        methodName ?: return
        project.service<KensaTestResultsService>().updateMethod(classFqn, methodName, test.toStatus() ?: return)
    }

    override fun onSuiteFinished(suite: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(suite.locationUrl ?: return) ?: return
        if (methodName != null) return // only handle class-level suites
        project.service<KensaTestResultsService>().updateClass(classFqn, suite.toStatus() ?: return)
    }

    override fun onTestingFinished(testsRoot: SMRootTestProxy) {
        if (!project.service<KensaTestResultsService>().hasAnyResults()) return

        val engagementService = ApplicationManager.getApplication().service<KensaEngagementService>()
        val state = engagementService.state

        if (state.dismissed) return

        state.runsSinceLastPrompt++
        if (state.runsSinceLastPrompt >= 5) {
            state.runsSinceLastPrompt = 0
            KensaEngagementNotifier.show(project)
        }
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
