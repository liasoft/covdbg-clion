package com.covdbg.coverage

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/**
 * The plugin's notifications, in one place.
 *
 * Every caller wants the same three things - the right group, a disposal check, and `notify(project)`
 * - so they live here rather than as a private helper per class.
 */
object CovdbgNotifications {

    /** Ordinary balloons. Declared in plugin.xml. */
    const val GROUP = "covdbg"

    /** Sticky, for the sign-in prompt that must survive the trip to the browser. */
    const val SIGN_IN_GROUP = "covdbg Sign-In"

    const val SERVICE_URL = "https://app.covdbg.com"

    const val SETTINGS_ID = "covdbg.settings"

    fun info(project: Project, content: String, title: String? = null): Notification? =
        notify(project, NotificationType.INFORMATION, content, title)

    fun warn(project: Project, content: String, title: String? = null): Notification? =
        notify(project, NotificationType.WARNING, content, title)

    fun error(project: Project, content: String, title: String? = null): Notification? =
        notify(project, NotificationType.ERROR, content, title)

    /** Returns null when the project is gone, so callers can chain actions without a guard. */
    fun notify(
        project: Project,
        type: NotificationType,
        content: String,
        title: String? = null,
        group: String = GROUP,
        configure: Notification.() -> Unit = {}
    ): Notification? {
        if (project.isDisposed) return null
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(group)
        val notification = if (title == null) {
            notificationGroup.createNotification(content, type)
        } else {
            notificationGroup.createNotification(title, content, type)
        }
        notification.configure()
        notification.notify(project)
        return notification
    }

    /** Adds an action, keeping only [handler]'s own captures alive rather than the caller. */
    fun Notification.action(text: String, handler: (Project) -> Unit): Notification {
        addAction(object : NotificationAction(text) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                e.project?.let(handler)
            }
        })
        return this
    }

    fun Notification.openSettingsAction(): Notification = action("Open Settings", ::openSettings)

    /** Opens Settings at the covdbg page. */
    fun openSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SETTINGS_ID)
    }
}
