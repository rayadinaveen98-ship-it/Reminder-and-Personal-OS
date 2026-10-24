package dev.reminderos.preview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * R3c: Quiet Precision management surface over the existing SQLite store.
 *
 * Pausing/archiving a recurrence uses its existing transactional routines and
 * scheduler outbox. This screen never copies/rebuilds user records, and RPOSB2
 * portable import is explicitly NOT available.
 */
class QuietPrecisionManagementActivity : Activity() {
    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_SETTINGS = "settings"
        const val PAGE_SCHEDULES = "schedules"
        private const val REQUEST_NOTIFICATIONS = 4103
    }
    private val store by lazy { ReminderDatabase(this) }
    private val engine by lazy { ReminderAlarmEngine(this) }
    private var settings = false
    private val dark get() = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val canvas get() = Color.parseColor(if (dark) "#121B27" else "#F6F8FC")
    private val surface get() = Color.parseColor(if (dark) "#1D2938" else "#FFFFFF")
    private val ink get() = Color.parseColor(if (dark) "#F3F6FA" else "#1D2937")
    private val secondary get() = Color.parseColor(if (dark) "#A0AFC1" else "#5F6E80")
    private val border get() = Color.parseColor(if (dark) "#354457" else "#DFE6F0")
    private val primary get() = Color.parseColor(if (dark) "#87A7FC" else "#3D63CF")
    private val onPrimary get() = Color.parseColor(if (dark) "#101821" else "#FFFFFF")

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun shape(bg: Int) = GradientDrawable().apply {
        setColor(bg)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), border)
    }
    private fun text(msg: String, size: Float = 14f, bold: Boolean = false,
                     color: Int = ink): TextView = TextView(this).apply {
        this.text = msg
        textSize = size
        setTextColor(color)
        setPadding(0, dp(4), 0, dp(4))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun button(msg: String, filled: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = msg
            isAllCaps = false
            textSize = 13f
            minHeight = dp(48)
            setTextColor(if (filled) onPrimary else primary)
            background = shape(if (filled) primary else surface)
            setOnClickListener { action() }
        }
    private fun gap(target: LinearLayout, amount: Int = 12) =
        target.addView(View(this), LinearLayout.LayoutParams(1, dp(amount)))
    private fun block(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(surface)
        setPadding(dp(16), dp(15), dp(16), dp(15))
    }
    private fun message(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = intent.getStringExtra(EXTRA_PAGE) == PAGE_SETTINGS
        render()
    }
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        root.addView(button("← Back") { finish() },
            LinearLayout.LayoutParams(dp(108), dp(48)))
        gap(root, 13)
        root.addView(text(if (settings) "Settings" else "Manage reminders", 26f, true))
        gap(root, 12)

        val switcher = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for ((page, label) in listOf(false to "Reminders", true to "Settings")) {
            val child = button(label, page == settings) {
                settings = page
                render()
            }
            val p = LinearLayout.LayoutParams(0, dp(48), 1f)
            if (switcher.childCount > 0) p.marginStart = dp(10)
            switcher.addView(child, p)
        }
        root.addView(switcher)
        gap(root, 16)

        val scroller = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(22))
        }
        try {
            if (settings) settingsPage(body) else remindersPage(body)
        } catch (error: Exception) {
            body.addView(text("Unable to load local reminders", 18f, true))
            gap(body)
            body.addView(text("Stored records were not cleared. You can open the original " +
                "recovery tools to inspect the issue.", 14f, false, secondary))
            gap(body)
            body.addView(button("Open recovery tools") { openTools() })
        }
        scroller.addView(body)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun remindersPage(body: LinearLayout) {
        body.addView(text("Repeating schedules", 18f, true))
        gap(body, 5)
        val rules = store.rules()
        if (rules.isEmpty()) {
            body.addView(text("No repeating schedules yet.", 14f, false, secondary))
        }
        for (rule in rules) {
            val card = block()
            card.addView(text(rule.title, 17f, true))
            card.addView(text(RecurrenceSchedule.description(rule.mode, rule.daysMask) +
                " · " + if (rule.paused) "Paused" else "Active", 13f, false, secondary))
            gap(card, 10)
            val actions = LinearLayout(this)
            val toggle = button(if (rule.paused) "Resume" else "Pause") {
                setPaused(rule)
            }
            val history = button("History") { showRuleHistory(rule) }
            val archive = button("Archive") { confirmArchiveRule(rule) }
            for (b in listOf(toggle, history, archive)) {
                val p = LinearLayout.LayoutParams(0, dp(48), 1f)
                if (actions.childCount > 0) p.marginStart = dp(6)
                actions.addView(b, p)
            }
            card.addView(actions)
            gap(card, 8)
            card.addView(button("Replace future schedule") {
                startActivity(Intent(this, QuietPrecisionReminderEditorActivity::class.java)
                    .putExtra("rule_id", rule.id))
            })
            body.addView(card)
            gap(body, 10)
        }

        val archivedRules = store.archivedRules()
        if (archivedRules.isNotEmpty()) {
            gap(body, 8)
            body.addView(text("Archived schedules", 18f, true))
            body.addView(text("Recorded outcomes are retained and remain readable.",
                12f, false, secondary))
            for (old in archivedRules.take(30)) {
                val card = block()
                card.addView(text(old.title, 15f, true))
                card.addView(text(RecurrenceSchedule.description(old.mode, old.daysMask),
                    12f, false, secondary))
                card.addView(button("View past outcomes") { showRuleHistory(old) })
                body.addView(card)
                gap(body, 8)
            }
        }

        gap(body, 7)
        body.addView(text("One-time reminders", 18f, true))
        gap(body, 5)
        val singles = store.all().filter { it.seriesId == null }
        if (singles.isEmpty()) {
            body.addView(text("No one-time reminders saved.", 14f, false, secondary))
        }
        for (item in singles.take(35)) {
            val card = block()
            card.addView(text(item.title, 16f, true))
            card.addView(text(DateFormat.getDateTimeInstance().format(Date(item.dueAt)) +
                " · " + when {
                    item.paused -> "Paused"
                    item.status == "DONE" -> "Done"
                    item.status == "SKIPPED" -> "Skipped"
                    item.dueAt < System.currentTimeMillis() -> "Unresolved"
                    else -> "Upcoming"
                }, 12f, false, secondary))
            gap(card, 7)
            val row = LinearLayout(this)
            val history = button("History") {
                val events = store.recentEvents(item.id)
                AlertDialog.Builder(this).setTitle(item.title)
                    .setMessage(if (events.isEmpty()) "No recorded actions" else
                        events.joinToString("\n") { it.kind + " · " +
                            DateFormat.getDateTimeInstance().format(Date(it.atMs)) })
                    .setPositiveButton("Close", null).show()
            }
            row.addView(history, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (item.status == "PENDING") {
                val pause = button(if (item.paused) "Resume" else "Pause") {
                    changeOneTimePause(item)
                }
                val p = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginStart = dp(8)
                }
                row.addView(pause, p)
            }
            val archive = button("Archive") { confirmArchiveSingle(item) }
            val p = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(8)
            }
            row.addView(archive, p)
            card.addView(row)
            body.addView(card)
            gap(body, 10)
        }
        if (singles.size > 35) {
            body.addView(text("More reminder records are available through legacy tools.",
                13f, false, secondary))
        }
        gap(body)
        body.addView(button("Browse full History & Archive") {
            startActivity(Intent(this, QuietPrecisionHistoryActivity::class.java))
        })
        gap(body)
        body.addView(button("+ New reminder", true) {
            startActivity(Intent(this, QuietPrecisionReminderEditorActivity::class.java))
        })
        gap(body, 10)
        body.addView(text("Replacing a schedule archives the original and creates new future dates. " +
            "Past outcomes remain in Archived schedules.", 12f, false, secondary))
    }

    private fun setPaused(rule: RecurrenceRule) {
        try {
            val affected = if (!rule.paused)
                store.pendingOccurrenceIdsForRoutine(rule.id) else emptyList()
            if (!store.setRoutinePaused(rule.id, !rule.paused)) {
                message("Schedule changed. Reopen to refresh.")
            } else {
                affected.forEach { ReminderAlarmEngine.dismiss(this, it) }
                store.ensureRecurring()
                engine.flush()
                message(if (rule.paused) "Schedule resumed" else "Schedule paused")
            }
        } catch (e: Exception) {
            message("Could not update schedule. Current data was retained.")
        }
        render()
    }

    private fun showRuleHistory(rule: RecurrenceRule) {
        try {
            AlertDialog.Builder(this).setTitle(rule.title + " · History")
                .setMessage(store.routineHistory(rule.id))
                .setPositiveButton("Close", null).show()
        } catch (e: Exception) {
            message("Could not read history.")
        }
    }

    private fun confirmArchiveRule(rule: RecurrenceRule) {
        AlertDialog.Builder(this).setTitle("Archive repeating schedule?")
            .setMessage("Stops future alerts and keeps its recorded occurrences. " +
                "This operation cannot be undone in the current alpha.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Archive") { _, _ ->
                try {
                    val ids = store.pendingOccurrenceIdsForRoutine(rule.id)
                    if (store.archiveRoutine(rule.id)) {
                        ids.forEach { ReminderAlarmEngine.dismiss(this, it) }
                        engine.flush()
                        message("Schedule archived")
                    } else message("Schedule changed; refresh and try again")
                } catch (e: Exception) { message("Could not archive schedule") }
                render()
            }.show()
    }

    private fun changeOneTimePause(item: ReminderRecord) {
        try {
            if (store.setPaused(item.id, item.revision, !item.paused)) {
                ReminderAlarmEngine.dismiss(this, item.id)
                engine.flush()
                message(if (item.paused) "Reminder resumed" else "Reminder paused")
            } else message("Cannot resume overdue or changed reminder. Edit its due time.")
        } catch (e: Exception) { message("Could not update reminder") }
        render()
    }

    private fun confirmArchiveSingle(item: ReminderRecord) {
        AlertDialog.Builder(this).setTitle("Archive reminder?")
            .setMessage("Stops its future notification and preserves recorded history.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Archive") { _, _ ->
                try {
                    if (store.archive(item.id, item.revision)) {
                        ReminderAlarmEngine.dismiss(this, item.id)
                        engine.flush()
                        message("Reminder archived")
                    } else message("Reminder changed; refresh and try again")
                } catch (e: Exception) { message("Could not archive reminder") }
                render()
            }.show()
    }

    private fun settingsPage(body: LinearLayout) {
        val permission = block()
        permission.addView(text("Notification reliability", 18f, true))
        gap(permission, 5)
        permission.addView(text(engine.capabilityText(), 13f, false, secondary))
        gap(permission, 8)
        if (Build.VERSION.SDK_INT >= 33 && !engine.notificationAllowed()) {
            permission.addView(button("Enable notifications") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATIONS)
            })
            gap(permission, 8)
        }
        if (Build.VERSION.SDK_INT >= 31 && !engine.exactAllowed()) {
            permission.addView(button("Exact alarm settings") {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + packageName)))
                } catch (e: Exception) { message("Open app settings to adjust exact alarms") }
            })
            gap(permission, 8)
        }
        permission.addView(button("Check & repair future alerts") {
            try {
                val queued = store.reconcileFuture(force = true)
                engine.flush()
                message("Queued " + queued + " future alarm repairs")
            } catch (e: Exception) { message("Could not reconcile future alerts") }
            render()
        })
        body.addView(permission)
        gap(body)

        val health = block()
        health.addView(text("Local diagnostics", 18f, true))
        val state = store.schedulingHealth()
        health.addView(text("Future occurrences: " + state.activeFuture +
            "\nOverdue unresolved: " + state.overdueUnknown +
            "\nPending scheduler jobs: " + state.pendingJobs +
            "\nFailed scheduler jobs: " + state.failedJobs, 13f, false, secondary))
        health.addView(text("Scheduler acceptance is not proof of notification display.",
            12f, false, secondary))
        body.addView(health)
        gap(body)

        val backup = block()
        backup.addView(text("Encrypted backups", 18f, true))
        backup.addView(text("Portable RPOSB2 export and verification are available. " +
            "Portable import/rollback is NOT implemented; do not delete your old installation.",
            13f, false, secondary))
        gap(backup, 9)
        backup.addView(button("Open backup & advanced tools") { openTools() })
        body.addView(backup)
        gap(body)

        val note = block()
        note.addView(text("Privacy & limitations", 18f, true))
        note.addView(text("No account is required for local reminders. " +
            "This alpha is not an emergency alarm service, and Android background " +
            "restrictions can delay alerts.", 13f, false, secondary))
        body.addView(note)
    }

    private fun openTools() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            engine.flush()
            render()
        }
    }
}
