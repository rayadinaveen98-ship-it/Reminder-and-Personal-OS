package dev.reminderos.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.reminderos.core.AlertIntent
import dev.reminderos.core.PlatformScheduleResult

/**
 * Android callback means alarm execution was attempted, not notification presentation
 * or task completion. This preview does not record Done/Skipped outcomes.
 */
class PreviewAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val due = PreviewStore.due(context)
        if (due <= 0L) return  // cancelled or duplicate callback
        PreviewStore.clear(context)
        PreviewAlarmGateway.showPreviewNotification(context)
    }

    companion object { const val ACTION_FIRE = "dev.reminderos.preview.FIRE" }
}

/**
 * Restore a future single test alert after boot or package update. Past-due previews
 * are cleared, not silently marked Completed. Real app needs overdue history.
 */
class PreviewRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowed = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
        if (intent.action !in allowed) return
        val due = PreviewStore.due(context)
        if (due <= 0L) return
        if (due <= System.currentTimeMillis()) {
            PreviewStore.clear(context)
            return
        }
        when (PreviewAlarmGateway(context).schedule(
            AlertIntent(PreviewAlarmGateway.PREVIEW_ID, due, 1)
        )) {
            is PlatformScheduleResult.Accepted -> Unit
            is PlatformScheduleResult.Rejected -> Unit // Keep durable due time for next app launch.
        }
    }
}
