package dev.kensa.plugin.intellij.gutter

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import dev.kensa.plugin.intellij.execution.KensaEngagementNotifier
import dev.kensa.plugin.intellij.execution.KensaRunTabRegistry
import dev.kensa.plugin.intellij.settings.KensaEngagementService
import dev.kensa.plugin.intellij.settings.KensaSettings
import java.io.File

class KensaTestRunListener @JvmOverloads constructor(
    private val project: Project,
    private val isKensaTestClass: (String) -> Boolean = { fqn -> defaultIsKensaTestClass(project, fqn) },
) : SMTRunnerEventsAdapter() {

    companion object {
        internal val DESCRIPTOR_KEY: Key<RunContentDescriptor> = Key.create("kensa.runContentDescriptor")
    }

    override fun onTestingStarted(testsRoot: SMRootTestProxy) {
        RunContentManager.getInstance(project).selectedContent?.let { descriptor ->
            testsRoot.putUserData(DESCRIPTOR_KEY, descriptor)
        }
    }

    override fun onTestFinished(test: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(test.locationUrl ?: return) ?: return
        methodName ?: return
        val baseMethod = methodName.substringBefore('[').takeIf { it.isNotBlank() } ?: return
        project.service<KensaTestResultsService>().updateMethod(classFqn, baseMethod, test.toStatus() ?: return)
        maybeTagDescriptor(test, classFqn)
    }

    override fun onSuiteFinished(suite: SMTestProxy) {
        val (classFqn, methodName) = parseLocation(suite.locationUrl ?: return) ?: return
        if (methodName != null) return // only handle class-level suites
        project.service<KensaTestResultsService>().updateClass(classFqn, suite.toStatus() ?: return)
        maybeTagDescriptor(suite, classFqn)
    }

    override fun onTestingFinished(testsRoot: SMRootTestProxy) {
        val descriptor = testsRoot.getUserData(DESCRIPTOR_KEY)
            ?: RunContentManager.getInstance(project).selectedContent
            ?: return
        val registry = project.service<KensaRunTabRegistry>()
        // Only refresh + notify if this run actually exercised a Kensa-derived test class.
        if (registry.classesFor(descriptor).isEmpty()) return

        project.basePath?.let { base ->
            val outputDir = project.service<KensaSettings>().effectiveOutputDirName
            KensaIndexLoader.scan(project, File(base), outputDir)
        }

        if (registry.indexPathFor(descriptor) == null) return

        val engagementService = ApplicationManager.getApplication().service<KensaEngagementService>()
        val state = engagementService.state

        if (state.dismissed) return

        state.runsSinceLastPrompt++
        if (state.runsSinceLastPrompt >= 5) {
            state.runsSinceLastPrompt = 0
            KensaEngagementNotifier.show(project)
        }
    }

    private fun maybeTagDescriptor(proxy: SMTestProxy, classFqn: String) {
        val descriptor = proxy.rootDescriptor() ?: return
        if (!isKensaTestClass(classFqn)) return
        project.service<KensaRunTabRegistry>().recordClass(descriptor, classFqn)
    }

    private fun SMTestProxy.rootDescriptor(): RunContentDescriptor? {
        var node: SMTestProxy? = this
        while (node != null) {
            if (node is SMRootTestProxy) {
                node.getUserData(DESCRIPTOR_KEY)?.let { return it }
                // onTestingStarted may fire before the test run tab becomes the selected
                // content (e.g. Gradle-delegated runs spend their first phase in the Build
                // window). Lazy-capture here so later events can still tag the descriptor.
                val current = RunContentManager.getInstance(project).selectedContent ?: return null
                node.putUserData(DESCRIPTOR_KEY, current)
                return current
            }
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

private fun defaultIsKensaTestClass(project: Project, classFqn: String): Boolean =
    ReadAction.compute<Boolean, RuntimeException> {
        if (project.isDisposed) return@compute false
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        val testClass = facade.findClass(classFqn, scope) ?: return@compute false
        val kensa = facade.findClass(KENSA_TEST_FQN, scope) ?: return@compute false
        InheritanceUtil.isInheritorOrSelf(testClass, kensa, true)
    }
