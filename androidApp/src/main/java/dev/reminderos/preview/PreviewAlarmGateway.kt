package dev.reminderos.preview

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.reminderos.core.AlertIntent
import dev.reminderos.core.DeliveryCapability
import dev.reminderos.core.NativeScheduleGateway
import dev.reminderos.core.PlatformRegistration
import dev.reminderos.core.PlatformScheduleResult
import dev.reminderos.core.SchedulerCapabilities

/**
 * Preview-only single-slot native adapter. Production needs transactional Room storage,
 * collision-safe OS identities and multi-occurrence reconciliation.
 */
internal class PreviewAlarmGateway(private val context: Context) : NativeScheduleGateway {
    private val manager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun exactGranted(): Boolean =
        Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    override fun capabilities(): SchedulerCapabilities = when {
        !notificationsGranted() -> SchedulerCapabilities(
            DeliveryCapability.NOT_AUTHORIZED, "Grant notification permission to receive visible alerts"
        )
        exactGranted() -> SchedulerCapabilities(DeliveryCapability.PRECISE)
        else -> SchedulerCapabilities(
            DeliveryCapability.APPROXIMATE_ONLY,
            "Exact-alarm access is unavailable; the Android system may delay the alert"
        )
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, PreviewAlarmReceiver::class.java)
            .setAction(PreviewAlarmReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun schedule(intent: AlertIntent): PlatformScheduleResult {
        if (intent.occurrenceId != PREVIEW_ID) {
            return PlatformScheduleResult.Rejected("PREVIEW_SINGLE_SLOT_ONLY")
        }
        if (intent.fireAtEpochMillis <= System.currentTimeMillis()) {
            return PlatformScheduleResult.Rejected("PAST_DUE")
        }
        if (!notificationsGranted()) {
            return PlatformScheduleResult.Rejected("POST_NOTIFICATIONS_DENIED")
        }
        return try {
            // PendingIntent remains valid if the Activity process is killed normally.
            if (exactGranted()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, intent.fireAtEpochMillis, pendingIntent()
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, intent.fireAtEpochMillis, pendingIntent()
                )
            }
            PlatformScheduleResult.Accepted(PREVIEW_ID)
        } catch (e: SecurityException) {
            PlatformScheduleResult.Rejected("EXACT_ALARM_ACCESS_CHANGED")
        } catch (e: RuntimeException) {
            PlatformScheduleResult.Rejected("NATIVE_SCHEDULER_ERROR")
        }
    }

    override fun cancel(registration: PlatformRegistration): Boolean {
        if (registration.occurrenceId != PREVIEW_ID || registration.platformHandle != PREVIEW_ID) {
            return false
        }
        return try {
            manager.cancel(pendingIntent())
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    companion object {
        const val PREVIEW_ID = "preview-one"
        const val CHANNEL_ID = "preview-reminders"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminder preview", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        fun showPreviewNotification(context: Context) {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val tap = PendingIntent.getActivity(
                context, 102, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reminder preview")
                .setContentText("Your scheduled test reminder is due.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            manager.notify(101, notification)
        }
    }
}

internal object PreviewStore {
    private const val PREFS = "preview_reminder_v1"
    private const val DUE = "due_at_epoch_millis"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun due(context: Context): Long = prefs(context).getLong(DUE, 0L)
    fun save(context: Context, due: Long) {
        // commit is synchronous for this tiny test state, not a production DB substitute
        check(prefs(context).edit().putLong(DUE, due).commit()) { "Failed to persist preview" }
    }
    fun clear(context: Context) {
        prefs(context).edit().remove(DUE).commit()
    }
}
