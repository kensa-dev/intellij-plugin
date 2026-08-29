package dev.kensa.plugin.intellij.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Computable
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
import dev.kensa.plugin.intellij.gutter.RunPhase
import dev.kensa.plugin.intellij.gutter.RunStateEntry
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

        internal fun runningText(entries: List<RunStateEntry>): String {
            val running = entries.filter { it.phase == RunPhase.RUNNING }
            val classes = running.sumOf { it.classesWritten }
            val text = StringBuilder("running, $classes ${if (classes == 1) "class" else "classes"}")
            // Method counts only exist in markers from kensa-core 0.9.2 onwards.
            val counted = running.filter { it.passed != null }
            if (counted.isNotEmpty()) {
                text.append(", ${counted.sumOf { it.passed ?: 0 }} passed, ${counted.sumOf { it.failed ?: 0 }} failed")
                val disabled = counted.sumOf { it.disabled ?: 0 }
                if (disabled > 0) text.append(", $disabled disabled")
            }
            return text.toString()
        }

        internal fun abandonedTooltip(entries: List<RunStateEntry>): String {
            val entry = entries.first { it.phase == RunPhase.ABANDONED }
            val started = entry.startedAt?.let { raw ->
                runCatching {
                    java.time.Instant.parse(raw)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                }.getOrNull()
            }
            val startedText = started?.let { "started $it" } ?: "started"
            val pidText = entry.pid?.let { " (process $it gone)" } ?: ""
            return "Kensa run $startedText never completed$pidText; re-run the tests"
        }
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
    private val runningLabel = JBLabel(com.intellij.ui.AnimatedIcon.Default()).apply {
        horizontalTextPosition = SwingConstants.RIGHT
        iconTextGap = 4
    }
    private val abandonedLabel = JBLabel(com.intellij.icons.AllIcons.General.Warning).apply {
        border = JBUI.Borders.emptyLeft(6)
    }

    override fun ID(): String = ID

    override fun getComponent(): JComponent = panel

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect(this).subscribe(
            KensaTestResultsService.KENSA_RESULTS_TOPIC,
            KensaResultsListener { _ -> refresh() }
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
            val active = service.activeRuns()
            val running = active.filter { it.phase == RunPhase.RUNNING }
            val abandoned = active.filter { it.phase == RunPhase.ABANDONED }
            val snap = service.snapshot()
            panel.removeAll()
            when {
                running.isNotEmpty() -> {
                    runningLabel.text = runningText(active)
                    panel.add(runningLabel)
                    panel.toolTipText = "Kensa test run in progress"
                }
                else -> {
                    if (!snap.isEmpty) {
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
                    } else {
                        panel.toolTipText = null
                    }
                    if (abandoned.isNotEmpty()) {
                        abandonedLabel.toolTipText = abandonedTooltip(abandoned)
                        panel.add(abandonedLabel)
                        if (snap.isEmpty) panel.toolTipText = abandonedTooltip(abandoned)
                    }
                }
            }
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

    // Both callers reach this from the EDT holding no read action -- showPicker() from the click
    // listener, tooltip() from refresh()'s invokeLater -- so the ProjectFileIndex lookup must take
    // its own. Read actions are re-entrant, so this stays correct if a caller ever already holds one.
    internal fun labelFor(indexHtmlPath: String): String {
        val vFile = LocalFileSystem.getInstance().findFileByPath(indexHtmlPath)
        val moduleRoot = vFile?.parent?.parent
        if (moduleRoot != null) {
            val module = ApplicationManager.getApplication().runReadAction(
                Computable { ProjectFileIndex.getInstance(project).getModuleForFile(moduleRoot) }
            )
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
