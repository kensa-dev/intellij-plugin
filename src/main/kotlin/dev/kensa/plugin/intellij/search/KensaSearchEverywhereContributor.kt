package dev.kensa.plugin.intellij.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import dev.kensa.plugin.intellij.gutter.TestStatus
import javax.swing.JList
import javax.swing.ListCellRenderer

data class KensaSearchItem(
    val classFqn: String,
    val methodName: String,
    val status: TestStatus,
)

class KensaSearchEverywhereContributor(event: AnActionEvent) : SearchEverywhereContributor<KensaSearchItem> {

    private val project = checkNotNull(event.project)

    private val iconPass = IconLoader.getIcon("/icons/kensa-gutter-pass.svg", KensaSearchEverywhereContributor::class.java)
    private val iconFail = IconLoader.getIcon("/icons/kensa-gutter-fail.svg", KensaSearchEverywhereContributor::class.java)

    override fun getSearchProviderId() = "KensaSearchEverywhereContributor"
    override fun getGroupName() = "Kensa Tests"
    override fun getSortWeight() = 100
    override fun showInFindResults() = false
    override fun isEmptyPatternSupported() = true

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in KensaSearchItem>,
    ) {
        val snapshot = project.service<KensaTestResultsService>().methodStatusSnapshot()
        for ((key, status) in snapshot) {
            if (progressIndicator.isCanceled) return
            val idx = key.indexOf('#')
            if (idx < 0) continue
            val classFqn = key.substring(0, idx)
            val methodName = key.substring(idx + 1)
            if (pattern.isNotBlank() && !matches(pattern, classFqn, methodName)) continue
            consumer.process(KensaSearchItem(classFqn, methodName, status))
        }
    }

    private fun matches(pattern: String, classFqn: String, methodName: String): Boolean {
        val lower = pattern.lowercase()
        return methodName.lowercase().contains(lower) ||
            classFqn.substringAfterLast('.').lowercase().contains(lower) ||
            classFqn.lowercase().contains(lower)
    }

    override fun processSelectedItem(selected: KensaSearchItem, modifiers: Int, searchText: String): Boolean {
        KensaReportOpener.openLocal(null, project, selected.classFqn, selected.methodName)
        return true
    }

    override fun getElementsRenderer(): ListCellRenderer<in KensaSearchItem> =
        object : ColoredListCellRenderer<KensaSearchItem>() {
            override fun customizeCellRenderer(
                list: JList<out KensaSearchItem>,
                value: KensaSearchItem?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                value ?: return
                icon = if (value.status == TestStatus.PASSED) iconPass else iconFail
                append(value.methodName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("  ${value.classFqn.substringAfterLast('.')}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }

    override fun getDataForItem(element: KensaSearchItem, dataId: String): Any? = null

    class Factory : SearchEverywhereContributorFactory<KensaSearchItem> {
        override fun createContributor(event: AnActionEvent): SearchEverywhereContributor<KensaSearchItem> =
            KensaSearchEverywhereContributor(event)
    }
}
