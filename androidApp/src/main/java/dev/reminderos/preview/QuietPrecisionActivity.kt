package dev.reminderos.preview

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Quiet Precision R3 vertical slice.
 *
 * Real reminder reads and explicit outcomes use the existing SQLite store;
 * all editing, recurrence and encrypted backups remain in MainActivity.
 * Track/Insights are transparently marked as not implemented. This shell
 * never creates another persistence store or changes an alarm identifier.
 */
class QuietPrecisionActivity : Activity() {
    private val store by lazy { ReminderDatabase(this) }
    private val engine by lazy { ReminderAlarmEngine(this) }
    private enum class Section { TODAY, TRACK, INSIGHTS, LIBRARY }
    private var section = Section.TODAY
    private val dark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val canvas get() = color(if (dark) "#121B27" else "#F6F8FC")
    private val surface get() = color(if (dark) "#1D2938" else "#FFFFFF")
    private val ink get() = color(if (dark) "#F3F6FA" else "#1D2937")
    private val muted get() = color(if (dark) "#A0AFC1" else "#5F6E80")
    private val primary get() = color(if (dark) "#87A7FC" else "#3D63CF")
    private val onPrimary get() = color(if (dark) "#101821" else "#FFFFFF")
    private val outline get() = color(if (dark) "#354457" else "#DFE6F0")
    private val caution get() = color(if (dark) "#E3B279" else "#995B1E")
    private val good get() = color(if (dark) "#79CDB1" else "#196E55")

    private fun color(hex: String) = Color.parseColor(hex)
    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()
    private fun panel(background: Int, corner: Int = 16) = GradientDrawable().apply {
        setColor(background)
        cornerRadius = dp(corner).toFloat()
        setStroke(dp(1), outline)
    }
    private fun label(value: String, size: Float = 15f, bold: Boolean = false,
                      tone: Int = ink) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(tone)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun stack(spacing: Int = 10) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_NONE
        setPadding(0, 0, 0, dp(spacing))
    }
    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
    private fun cta(name: String, filled: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            text = name
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(if (filled) onPrimary else primary)
            background = panel(if (filled) primary else surface, 12)
            setOnClickListener { action() }
        }
    private fun date(epoch: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epoch))
    private fun openTools() = startActivity(Intent(this, MainActivity::class.java))
    private fun openManagement(settings: Boolean = false) {
        val intent = Intent(this, QuietPrecisionManagementActivity::class.java)
        intent.putExtra(QuietPrecisionManagementActivity.EXTRA_PAGE,
            if (settings) QuietPrecisionManagementActivity.PAGE_SETTINGS else
                QuietPrecisionManagementActivity.PAGE_SCHEDULES)
        startActivity(intent)
    }
    private fun openEditor(reminderId: String? = null) {
        val intent = Intent(this, QuietPrecisionReminderEditorActivity::class.java)
        if (reminderId != null) intent.putExtra("reminder_id", reminderId)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderAlarmEngine.ensureChannel(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        try {
            store.ensureRecurring()
            store.reconcileFuture()
            engine.flush()
        } catch (error: Exception) {
            Toast.makeText(this, "Reminder recovery needs attention. Open tools to inspect.",
                Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
        }
        val heading = label("Reminder OS", 19f, true)
        header.addView(heading, LinearLayout.LayoutParams(0, dp(48), 1f))
        val tools = cta("Settings", false) { openManagement(true) }
        header.addView(tools, LinearLayout.LayoutParams(dp(96), dp(48)))
        root.addView(header)

        val scroller = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(22))
        }
        try {
            when (section) {
                Section.TODAY -> today(body)
                Section.TRACK -> comingSoon(body, "Track", "Activity and measurement tracking",
                    "Tracking is planned, not implemented in this alpha. Reminders remain available.")
                Section.INSIGHTS -> insights(body)
                Section.LIBRARY -> library(body)
            }
        } catch (error: Exception) {
            body.removeAllViews()
            body.addView(label("Your reminders couldn't be read", 22f, true))
            body.addView(spacer(10))
            body.addView(label("The app did not delete or reset any saved records. " +
                "Use the existing tools to inspect or recover your local data.", 14f, false, muted))
            body.addView(spacer(16))
            body.addView(cta("Open recovery tools") { openTools() })
        }
        scroller.addView(body)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(surface)
        }
        for (candidate in Section.values()) {
            val selected = candidate == section
            val item = TextView(this).apply {
                text = when (candidate) {
                    Section.TODAY -> "Today"
                    Section.TRACK -> "Track"
                    Section.INSIGHTS -> "Insights"
                    Section.LIBRARY -> "Library"
                }
                textSize = 13f
                gravity = Gravity.CENTER
                minHeight = dp(52)
                setTextColor(if (selected) primary else muted)
                setTypeface(Typeface.DEFAULT,
                    if (selected) Typeface.BOLD else Typeface.NORMAL)
                contentDescription = text.toString() +
                    (if (selected) ", selected" else "")
                setOnClickListener { section = candidate; render() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun today(parent: LinearLayout) {
        parent.addView(label(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            .format(Date()), 13f, false, muted))
        parent.addView(spacer(5))
        parent.addView(label("Today", 28f, true))
        parent.addView(spacer(18))
        val pending = store.all().filter { it.status == "PENDING" && !it.paused }
        val now = System.currentTimeMillis()
        val future = pending.filter { it.dueAt >= now }.sortedBy { it.dueAt }
        val overdue = pending.filter { it.dueAt < now }.sortedBy { it.dueAt }
        val first = future.firstOrNull()
        if (first == null) {
            val info = stack().apply {
                background = panel(surface)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(label("Nothing upcoming", 18f, true))
                addView(spacer(8))
                addView(label("Create a reminder to get started.", 14f, false, muted))
                addView(spacer(12))
                addView(cta("Add reminder") { openEditor() })
            }
            parent.addView(info)
        } else {
            parent.addView(label("NEXT UP", 12f, true, muted))
            parent.addView(spacer(9))
            parent.addView(reminderCard(first, true))
        }
        parent.addView(spacer(19))
        val unresolved = overdue.size
        parent.addView(label(if (unresolved > 0) "Unresolved · " + unresolved else
            "Upcoming reminders", 18f, true))
        parent.addView(spacer(9))
        val toShow = if (overdue.isNotEmpty()) overdue.take(3) else future.drop(1).take(3)
        if (toShow.isEmpty()) {
            parent.addView(label("No other items to show.", 14f, false, muted))
        } else toShow.forEach {
            parent.addView(reminderCard(it, false))
            parent.addView(spacer(9))
        }
        parent.addView(spacer(16))
        parent.addView(cta("+ New reminder") { openEditor() })
        parent.addView(spacer(10))
        parent.addView(label("Unanswered reminders stay unresolved until you respond.",
            12f, false, muted))
    }

    private fun reminderCard(item: ReminderRecord, focused: Boolean): LinearLayout {
        val card = stack(0).apply {
            background = panel(surface)
            setPadding(dp(16), dp(if (focused) 18 else 13), dp(16), dp(15))
        }
        card.addView(label(item.title, if (focused) 19f else 16f, true))
        card.addView(spacer(6))
        val isOverdue = item.status == "PENDING" && item.dueAt < System.currentTimeMillis()
        val state = when {
            item.paused -> "Paused"
            item.status == "DONE" -> "Done"
            item.status == "SKIPPED" -> "Skipped"
            isOverdue -> "Overdue · no response recorded"
            else -> "Upcoming"
        }
        card.addView(label(date(item.dueAt) + " · " + state, 12f, false,
            if (isOverdue) caution else if (item.status == "DONE") good else muted))
        if (item.status == "PENDING" && !item.paused) {
            card.addView(spacer(12))
            val actions = LinearLayout(this)
            for ((action, title) in listOf("DONE" to "Done", "SNOOZE" to "Snooze", "SKIP" to "Skip")) {
                val b = cta(title, action == "DONE") { respond(item, action) }
                val p = LinearLayout.LayoutParams(0, dp(48), 1f)
                if (actions.childCount > 0) p.marginStart = dp(8)
                actions.addView(b, p)
            }
            card.addView(actions)
        }
        card.addView(spacer(8))
        val tools = LinearLayout(this)
        if (item.status == "PENDING" && item.seriesId == null) {
            val edit = cta("Edit", false) { openEditor(item.id) }
            tools.addView(edit, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        val history = cta("History", false) { showHistory(item) }
        val historyLayout = LinearLayout.LayoutParams(0, dp(48), 1f)
        if (tools.childCount > 0) historyLayout.marginStart = dp(8)
        tools.addView(history, historyLayout)
        card.addView(tools)
        return card
    }

    private fun showHistory(item: ReminderRecord) {
        val events = store.recentEvents(item.id)
        val body = if (events.isEmpty()) "No recorded actions" else events.joinToString("\n") {
            it.kind + " · " + date(it.atMs)
        }
        AlertDialog.Builder(this).setTitle(item.title)
            .setMessage(body).setPositiveButton("Close", null).show()
    }

    private fun respond(item: ReminderRecord, action: String) {
        if (action == "SKIP") {
            AlertDialog.Builder(this)
                .setTitle("Skip this occurrence?")
                .setMessage("This marks only this occurrence as skipped, not future repeats.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Skip") { _, _ -> commitAction(item, action) }.show()
        } else commitAction(item, action)
    }

    private fun commitAction(item: ReminderRecord, action: String) {
        try {
            if (!store.act(item.id, item.revision, action)) {
                Toast.makeText(this, "Reminder changed. Refresh and try again.",
                    Toast.LENGTH_SHORT).show()
            } else {
                engine.flush()
                Toast.makeText(this, if (action == "SNOOZE") "Snoozed for 10 minutes" else
                    if (action == "DONE") "Marked done" else "Skipped", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save response. Original data is retained.",
                Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun library(parent: LinearLayout) {
        parent.addView(label("Library", 28f, true))
        parent.addView(spacer(8))
        parent.addView(label("Reminders and repeating schedules", 14f, false, muted))
        parent.addView(spacer(18))
        val rules = store.rules()
        val reminders = store.all().sortedBy { it.dueAt }
        if (rules.isEmpty() && reminders.isEmpty()) {
            parent.addView(label("Your library is empty.", 15f, false, muted))
        } else {
            if (rules.isNotEmpty()) {
                parent.addView(label("Repeating schedules", 18f, true))
                parent.addView(spacer(8))
                rules.forEach {
                    parent.addView(label(it.title + " · " +
                        if (it.paused) "Paused" else "Active", 15f))
                    parent.addView(spacer(8))
                }
                parent.addView(spacer(12))
            }
            parent.addView(label("Reminder occurrences", 18f, true))
            parent.addView(spacer(10))
            reminders.take(40).forEach {
                parent.addView(reminderCard(it, false))
                parent.addView(spacer(8))
            }
            if (reminders.size > 40) parent.addView(label("More available in Reminder Tools.",
                13f, false, muted))
        }
        parent.addView(spacer(12))
        parent.addView(cta("Browse History & Archive") {
            startActivity(Intent(this, QuietPrecisionHistoryActivity::class.java))
        })
        parent.addView(spacer(10))
        parent.addView(cta("Manage reminders and schedules") { openManagement() })
    }

    private fun insights(parent: LinearLayout) {
        parent.addView(label("Insights", 28f, true))
        parent.addView(spacer(8))
        parent.addView(label("Reminder history snapshot", 14f, false, muted))
        parent.addView(spacer(18))
        val counts = store.counts()
        val box = stack().apply {
            background = panel(surface)
            setPadding(dp(16), dp(18), dp(16), dp(18))
            addView(label("All non-archived reminder occurrences", 14f, true))
            addView(spacer(14))
            addView(label("Confirmed Done: " + counts.second, 16f, true, good))
            addView(spacer(8))
            addView(label("Explicitly Skipped: " + counts.third, 15f))
            addView(spacer(8))
            addView(label("Unresolved: " + counts.first, 15f, false, caution))
            addView(spacer(10))
            addView(label("These are all-time counts, not 7/28/90-day rates. " +
                "No-response is never assumed to mean failure.", 12f, false, muted))
        }
        parent.addView(box)
        parent.addView(spacer(20))
        parent.addView(label("Trend analysis is not implemented yet.", 13f, false, muted))
    }

    private fun comingSoon(parent: LinearLayout, title: String, detail: String,
                           explanation: String) {
        parent.addView(label(title, 28f, true))
        parent.addView(spacer(18))
        val box = stack().apply {
            background = panel(surface)
            setPadding(dp(16), dp(20), dp(16), dp(20))
            addView(label(detail, 18f, true))
            addView(spacer(9))
            addView(label(explanation, 14f, false, muted))
        }
        parent.addView(box)
    }
}
