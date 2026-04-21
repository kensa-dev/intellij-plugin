package dev.kensa.plugin.intellij.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class KensaStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = KensaStatusBarWidget.ID
    override fun getDisplayName(): String = "Kensa"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = KensaStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Unit
    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar): Boolean = true
}
