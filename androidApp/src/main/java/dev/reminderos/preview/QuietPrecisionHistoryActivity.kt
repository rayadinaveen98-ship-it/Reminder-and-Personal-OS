package dev.reminderos.preview

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * R3e read-only archive/history browser. Unlike home projections this page
 * queries every matching SQLite occurrence with deterministic, bounded paging.
 * No new scheduling calls and no mutations are performed by this activity.
 */
class QuietPrecisionHistoryActivity : Activity() {
    companion object {
        const val EXTRA_ARCHIVE_ONLY = "archive_only"
        private const val PAGE_SIZE = 25
    }
    private val store by lazy { ReminderDatabase(this) }
    private var archiveOnly = false
    private var search = ""
    private var outcome = "ALL"
    private var rows = emptyList<ReminderRecord>()
    private var hasMore = false
    private var offset = 0

    private val dark get() = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val canvas get() = Color.parseColor(if (dark) "#121B27" else "#F6F8FC")
    private val surface get() = Color.parseColor(if (dark) "#1D2938" else "#FFFFFF")
    private val ink get() = Color.parseColor(if (dark) "#F3F6FA" else "#1D2937")
    private val muted get() = Color.parseColor(if (dark) "#A0AFC1" else "#5F6E80")
    private val primary get() = Color.parseColor(if (dark) "#87A7FC" else "#3D63CF")
    private val onPrimary get() = Color.parseColor(if (dark) "#101821" else "#FFFFFF")
    private val outline get() = Color.parseColor(if (dark) "#354457" else "#DFE6F0")
    private fun dp(v: Int) = (resources.displayMetrics.density * v + 0.5f).toInt()
    private fun shape(fill: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), outline)
    }
    private fun text(value: String, size: Float = 14f, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(ink)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }
    private fun button(label: String, chosen: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            minHeight = dp(48)
            setTextColor(if (chosen) onPrimary else primary)
            background = shape(if (chosen) primary else surface)
            setOnClickListener { action() }
        }
    private fun gap(parent: LinearLayout, size: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(size)))
    }
    private fun section() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(surface)
        setPadding(dp(14), dp(12), dp(14), dp(13))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        archiveOnly = intent.getBooleanExtra(EXTRA_ARCHIVE_ONLY, false)
        reload()
    }
    override fun onResume() {
        super.onResume()
        reload()
    }
    private fun reload() {
        offset = 0
        rows = emptyList()
        fetchNext()
    }
    private fun fetchNext() {
        try {
            val page = store.historyPage(archiveOnly, search, outcome, PAGE_SIZE + 1, offset)
            val visible = page.take(PAGE_SIZE)
            rows = rows + visible
            offset += visible.size
            hasMore = page.size > PAGE_SIZE
            render()
        } catch (error: Exception) {
            Toast.makeText(this, "Could not read history. Saved data was not changed.",
                Toast.LENGTH_LONG).show()
            rows = emptyList()
            hasMore = false
            render()
        }
    }
    private fun date(whenMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(whenMs))

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
            setPadding(dp(20), dp(14), dp(20), dp(10))
        }
        root.addView(button("← Back") { finish() },
            LinearLayout.LayoutParams(dp(110), dp(48)))
        gap(root, 8)
        root.addView(text(if (archiveOnly) "Archive" else "History", 26f, true))
        root.addView(text(if (archiveOnly)
            "Archived reminders and their recorded outcomes" else
            "Due, resolved and archived occurrences", 13f))
        gap(root, 10)
        val tabs = LinearLayout(this)
        for (archived in listOf(false, true)) {
            val b = button(if (archived) "Archive" else "History", archiveOnly == archived) {
                if (archiveOnly != archived) {
                    archiveOnly = archived
                    reload()
                }
            }
            val params = LinearLayout.LayoutParams(0, dp(48), 1f)
            if (tabs.childCount > 0) params.marginStart = dp(8)
            tabs.addView(b, params)
        }
        root.addView(tabs)
        gap(root, 10)
        val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val edit = EditText(this).apply {
            hint = "Search title"
            setText(search)
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            contentDescription = "Search reminder titles"
        }
        searchRow.addView(edit, LinearLayout.LayoutParams(0, dp(52), 1f))
        val apply = button("Search") {
            search = edit.text.toString().trim()
            reload()
        }
        val params = LinearLayout.LayoutParams(dp(96), dp(48))
        params.marginStart = dp(7)
        searchRow.addView(apply, params)
        root.addView(searchRow)
        gap(root, 8)
        val outcomes = LinearLayout(this)
        for (filter in listOf("ALL", "UNKNOWN", "DONE", "SKIPPED")) {
            val name = when (filter) {
                "ALL" -> "All"
                "UNKNOWN" -> "Unresolved"
                "DONE" -> "Done"
                else -> "Skipped"
            }
            val b = button(name, filter == outcome) {
                if (outcome != filter) {
                    outcome = filter
                    reload()
                }
            }
            val p = LinearLayout.LayoutParams(0, dp(48), 1f)
            if (outcomes.childCount > 0) p.marginStart = dp(4)
            outcomes.addView(b, p)
        }
        root.addView(outcomes)
        gap(root, 12)

        val scroller = ScrollView(this).apply { isFillViewport = true }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        if (rows.isEmpty()) {
            list.addView(text(if (archiveOnly) "No archived records match this filter." else
                "No due or resolved records match this filter.", 15f))
        } else {
            list.addView(text("Showing " + rows.size +
                (if (hasMore) " · More available" else " · End of results"), 12f))
            gap(list, 8)
            for (reminder in rows) {
                val card = section()
                card.addView(text(reminder.title, 16f, true))
                card.addView(text(date(reminder.originalDueAt) + " · " +
                    when (reminder.status) {
                        "DONE" -> "Done"
                        "SKIPPED" -> "Skipped"
                        else -> "Unresolved"
                    } + (if (reminder.archived) " · Archived" else ""), 12f))
                gap(card, 7)
                card.addView(button("View recorded actions") { details(reminder.id) })
                list.addView(card)
                gap(list, 9)
            }
            if (hasMore) list.addView(button("Load more") { fetchNext() })
        }
        scroller.addView(list)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun details(reminderId: String) {
        try {
            // Re-read by stable ID; never use a stale row snapshot for evidence.
            val row = store.get(reminderId) ?: return
            val events = store.recentEvents(reminderId, 50)
            val body = "Originally due: " + date(row.originalDueAt) +
                "\nCurrent due: " + date(row.dueAt) +
                "\nOutcome: " + when (row.status) {
                    "DONE" -> "Done"
                    "SKIPPED" -> "Skipped"
                    else -> "Unresolved (no recorded completion)"
                } + "\nArchived: " + (if (row.archived) "Yes" else "No")
            val audit = if (events.isEmpty()) "No actions recorded" else
                events.joinToString("\n") { it.kind + " · " + date(it.atMs) }
            AlertDialog.Builder(this)
                .setTitle(row.title)
                .setMessage(body + "\n\nRecorded actions\n" + audit)
                .setPositiveButton("Close", null)
                .show()
        } catch (error: Exception) {
            Toast.makeText(this, "Unable to read recorded actions", Toast.LENGTH_SHORT).show()
        }
    }
}
