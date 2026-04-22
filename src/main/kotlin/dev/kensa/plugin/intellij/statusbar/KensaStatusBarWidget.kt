package dev.kensa.plugin.intellij.statusbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBUI
import dev.kensa.plugin.intellij.gutter.KensaReportOpener
import dev.kensa.plugin.intellij.gutter.KensaResultsListener
import dev.kensa.plugin.intellij.gutter.KensaTestResultsService
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class KensaStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    companion object {
        const val ID: String = "kensa.status"

        private fun loadIcon(path: String): Icon =
            com.intellij.openapi.util.IconLoader.getIcon(path, KensaStatusBarWidget::class.java)
    }

    private var statusBar: StatusBar? = null
    private val panel: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(0, 4)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = handleClick(e)
        })
    }
    private val passedLabel = countLabel(loadIcon("/icons/kensa-gutter-pass.svg")).apply {
        border = JBUI.Borders.empty()
    }
    private val failedLabel = countLabel(loadIcon("/icons/kensa-gutter-fail.svg"))
    private val ignoredLabel = countLabel(loadIcon("/icons/kensa-gutter-ignored.svg"))
    private val multiLabel = JBLabel().apply {
        foreground = JBColor.GRAY
        border = JBUI.Borders.emptyLeft(4)
    }

    override fun ID(): String = ID

    override fun getComponent(): JComponent = panel

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect(this).subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { refresh() }
        )
        refresh()
    }

    override fun dispose() {
        statusBar = null
    }

    private fun refresh() {
        com.intellij.openapi.application.invokeLater {
            if (project.isDisposed) return@invokeLater
            val service = project.service<KensaTestResultsService>()
            val snap = service.snapshot()
            panel.removeAll()
            if (snap.isEmpty) {
                panel.revalidate()
                panel.repaint()
                panel.isVisible = false
                return@invokeLater
            }
            panel.isVisible = true
            passedLabel.text = snap.passed.toString()
            panel.add(passedLabel)
            if (snap.failed > 0) {
                failedLabel.text = snap.failed.toString()
                panel.add(failedLabel)
            }
            if (snap.ignored > 0) {
                ignoredLabel.text = snap.ignored.toString()
                panel.add(ignoredLabel)
            }
            val indexCount = service.allIndexPaths().size
            if (indexCount > 1) {
                multiLabel.text = "($indexCount)"
                panel.add(multiLabel)
            }
            panel.toolTipText = tooltip(service)
            panel.revalidate()
            panel.repaint()
            statusBar?.updateWidget(ID)
        }
    }

    private fun tooltip(service: KensaTestResultsService): String? {
        val indexPaths = service.indexPathsByRecency()
        if (indexPaths.isEmpty()) return null
        if (indexPaths.size == 1) {
            val s = service.snapshotForIndex(indexPaths.first())
            return "Kensa: ${s.passed} passed, ${s.failed} failed, ${s.ignored} ignored"
        }
        return buildString {
            append("<html>Kensa reports:<br>")
            for (path in indexPaths) {
                val s = service.snapshotForIndex(path)
                append(labelFor(path))
                append(": ")
                append("${s.passed}✓ ${s.failed}✗")
                if (s.ignored > 0) append(" ${s.ignored}⊘")
                append("<br>")
            }
            append("</html>")
        }
    }

    private fun handleClick(event: MouseEvent) {
        val service = project.service<KensaTestResultsService>()
        val paths = service.indexPathsByRecency()
        when {
            paths.isEmpty() -> return
            paths.size == 1 -> KensaReportOpener.openIndexHtml(project, paths.first())
            else -> showPicker(event, paths)
        }
    }

    private fun showPicker(event: MouseEvent, paths: List<String>) {
        val service = project.service<KensaTestResultsService>()
        val items = paths.map { path ->
            val s = service.snapshotForIndex(path)
            val counts = buildString {
                append("${s.passed}✓ ${s.failed}✗")
                if (s.ignored > 0) append(" ${s.ignored}⊘")
            }
            PickerItem(path, "${labelFor(path)}  —  $counts")
        }
        val step = object : BaseListPopupStep<PickerItem>("Open Kensa Report", items) {
            override fun getTextFor(value: PickerItem): String = value.display
            override fun onChosen(selected: PickerItem, finalChoice: Boolean): PopupStep<*>? {
                KensaReportOpener.openIndexHtml(project, selected.path)
                return FINAL_CHOICE
            }
        }
        ListPopupImpl(project, step).show(RelativePoint(event))
    }

    private fun labelFor(indexHtmlPath: String): String {
        val vFile = LocalFileSystem.getInstance().findFileByPath(indexHtmlPath)
        val moduleRoot = vFile?.parent?.parent
        if (moduleRoot != null) {
            val module = ProjectFileIndex.getInstance(project).getModuleForFile(moduleRoot)
            if (module != null) return module.name
        }
        val basePath = project.basePath
        if (basePath != null) {
            runCatching {
                return Paths.get(basePath).relativize(Paths.get(indexHtmlPath)).toString()
            }
        }
        return indexHtmlPath
    }

    private fun countLabel(icon: Icon): JBLabel = JBLabel(icon).apply {
        horizontalTextPosition = SwingConstants.RIGHT
        iconTextGap = 2
        border = JBUI.Borders.emptyLeft(6)
    }

    private data class PickerItem(val path: String, val display: String)
}
