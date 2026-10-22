package dev.reminderos.preview

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Alpha: stable per-reminder PendingIntent URI; no long-running background service.
 * Scheduler operations can be replayed after process death. Native system acceptance
 * must not be interpreted as visible delivery or user completion.
 */
internal class ReminderAlarmEngine(private val context: Context) {
    private val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val database = ReminderDatabase(context)

    fun notificationAllowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()

    fun capabilityText(): String = when {
        !notificationAllowed() -> "Notifications disabled — enable permission for visible reminders"
        !exactAllowed() -> "Approximate alarm mode — reminders may arrive late"
        else -> "Exact alarm scheduling available (delivery still depends on device settings)"
    }

    private fun alarmIntent(id: String, revision: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            data = Uri.parse("reminderos://alarm/$id")
            putExtra("id", id)
            putExtra("revision", revision)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancel(id: String): Boolean = try {
        alarm.cancel(alarmIntent(id, 0))
        true
    } catch (_: RuntimeException) { false }

    /**
     * Safe to call on app foreground and supported system restore broadcasts.
     * Cancel operations go before replacement registration and the DB tracks failures.
     */
    fun flush(): Int {
        val jobs = database.jobs()
        val blocked = mutableSetOf<String>()
        var accepted = 0
        for (job in jobs) {
            if (job.kind == "CANCEL") {
                val okay = cancel(job.reminderId)
                database.completeJob(job.id, okay, if (okay) null else "OS_CANCEL_ERROR")
                if (!okay) blocked.add(job.reminderId) else accepted++
                continue
            }
            if (!database.currentJob(job)) {
                database.retireJob(job.id)
                continue
            }
            if (job.reminderId in blocked) continue
            val due = job.fireAt ?: continue
            if (due <= System.currentTimeMillis()) {
                // Preserve the real-world outcome as UNKNOWN. An overdue reminder
                // requires user action; don't create a late, surprise alarm.
                database.retireJob(job.id)
                continue
            }
            if (!notificationAllowed()) {
                database.completeJob(job.id, false, "POST_NOTIFICATIONS_DENIED")
                continue
            }
            val okay = try {
                if (exactAllowed()) alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, due, alarmIntent(job.reminderId, job.revision)
                ) else alarm.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, due, alarmIntent(job.reminderId, job.revision)
                )
                true
            } catch (_: SecurityException) { false }
              catch (_: RuntimeException) { false }
            database.completeJob(job.id, okay, if (okay) null else "OS_REGISTER_FAILED")
            if (okay) accepted++
        }
        return accepted
    }

    fun recoverAfterRestart() {
        database.recoverFuture()
        flush()
    }

    companion object {
        const val CHANNEL = "reminder_alpha_channel"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Personal reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        private fun actionIntent(context: Context, id: String, rev: Int, kind: String): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = "dev.reminderos.alpha.$kind"
                data = Uri.parse("reminderos://action/$id/$rev/$kind")
                putExtra("id", id)
                putExtra("revision", rev)
                putExtra("kind", kind)
            }
            return PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun notifyDue(context: Context, row: ReminderRecord) {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val tap = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(row.title)
                .setContentText("Reminder due — choose Done, Snooze or Skip")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(
                    android.R.drawable.checkbox_on_background, "Done",
                    actionIntent(context, row.id, row.revision, "DONE")).build())
                .addAction(Notification.Action.Builder(
                    android.R.drawable.ic_lock_idle_alarm, "Snooze 10m",
                    actionIntent(context, row.id, row.revision, "SNOOZE")).build())
                .addAction(Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Skip",
                    actionIntent(context, row.id, row.revision, "SKIP")).build())
                .build()
            manager.notify(row.id.hashCode(), notification)
        }
        fun dismiss(context: Context, id: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(id.hashCode())
        }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getStringExtra("id") ?: return
        val revision = intent.getIntExtra("revision", -1)
        if (revision < 1) return
        val record = ReminderDatabase(context).receivedAlarm(id, revision) ?: return
        ReminderAlarmEngine.notifyDue(context, record)
    }
    companion object { const val ACTION_FIRE = "dev.reminderos.alpha.REMINDER_FIRE" }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val revision = intent.getIntExtra("revision", -1)
        val kind = intent.getStringExtra("kind") ?: return
        if (revision < 1 || kind !in setOf("DONE", "SKIP", "SNOOZE")) return
        if (ReminderDatabase(context).act(id, revision, kind)) {
            ReminderAlarmEngine.dismiss(context, id)
            ReminderAlarmEngine(context).flush()
        }
    }
}

class ReminderRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) return
        ReminderAlarmEngine(context).recoverAfterRestart()
    }
}
