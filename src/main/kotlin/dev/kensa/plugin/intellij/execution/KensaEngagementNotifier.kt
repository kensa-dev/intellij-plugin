package dev.kensa.plugin.intellij.execution

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import dev.kensa.plugin.intellij.settings.KensaEngagementService
import java.util.concurrent.TimeUnit

object KensaEngagementNotifier {

    fun show(project: Project) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Kensa.Engagement")
            .createNotification(
                "Enjoying Kensa? ⭐",
                "If you are enjoying Kensa ❤️, please take the time to star ⭐the repository.",
                NotificationType.INFORMATION,
            )

        notification.addAction(NotificationAction.createSimple("Sure, take me there!") {
            BrowserUtil.browse("https://github.com/kensa-dev/kensa")
            notification.expire()
        })

        notification.addAction(NotificationAction.createSimple("Don't ask again") {
            ApplicationManager.getApplication().service<KensaEngagementService>().state.dismissed = true
            notification.expire()
        })

        notification.notify(project)

        AppExecutorUtil.getAppScheduledExecutorService()
            .schedule({ if (!project.isDisposed) notification.expire() }, 60, TimeUnit.SECONDS)
    }
}
