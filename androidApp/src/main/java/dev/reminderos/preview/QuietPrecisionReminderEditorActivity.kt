package dev.reminderos.preview

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * R3b: native Quiet Precision editor built directly on the V0.5d reminder store.
 * One-off edits remain revision guarded. Recurring rules can be CREATED here;
 * routine editing remains in the original management tools.
 */
class QuietPrecisionReminderEditorActivity : Activity() {
    private val store by lazy { ReminderDatabase(this) }
    private val engine by lazy { ReminderAlarmEngine(this) }
    private lateinit var titleField: EditText
    private lateinit var dueView: TextView
    private lateinit var repeatSelector: Spinner
    private lateinit var weeklyChoice: Button
    private lateinit var saveButton: Button
    private var dueAt = System.currentTimeMillis() + 60 * 60_000L
    private var weekdays = 31 // Monday-Friday; bit 0 is Monday.
    private var editing: ReminderRecord? = null
    private var replacing: RecurrenceRule? = null
    private var saving = false

    private val dark get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val canvas get() = Color.parseColor(if (dark) "#121B27" else "#F6F8FC")
    private val surface get() = Color.parseColor(if (dark) "#1D2938" else "#FFFFFF")
    private val ink get() = Color.parseColor(if (dark) "#F3F6FA" else "#1D2937")
    private val secondary get() = Color.parseColor(if (dark) "#A0AFC1" else "#5F6E80")
    private val primary get() = Color.parseColor(if (dark) "#87A7FC" else "#3D63CF")
    private val onPrimary get() = Color.parseColor(if (dark) "#101821" else "#FFFFFF")
    private val outline get() = Color.parseColor(if (dark) "#354457" else "#DFE6F0")
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun shape(bg: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(bg)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), outline)
    }
    private fun text(value: String, size: Float = 14f, bold: Boolean = false,
                     color: Int = ink) = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun button(name: String, filled: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            text = name
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(if (filled) onPrimary else primary)
            background = shape(if (filled) primary else surface, 11)
            setOnClickListener { action() }
        }
    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(TextView(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun formattedDue(): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(dueAt))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ruleId = intent.getStringExtra("rule_id")
        if (ruleId != null) {
            val rule = store.rule(ruleId)
            if (rule == null || rule.archived) {
                Toast.makeText(this, "Schedule changed. Reopen to refresh.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            replacing = rule
            weekdays = rule.daysMask
            val anchor = Calendar.getInstance().apply { timeInMillis = rule.anchorMs }
            val proposed = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, anchor.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, anchor.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (proposed.timeInMillis <= System.currentTimeMillis() + 2_000L)
                proposed.add(Calendar.DAY_OF_MONTH, 1)
            dueAt = proposed.timeInMillis
        }
        val id = intent.getStringExtra("reminder_id")
        if (id != null) {
            val row = store.get(id)
            if (row == null || row.archived || row.seriesId != null || row.status != "PENDING") {
                Toast.makeText(this, "This reminder can no longer be edited here.",
                    Toast.LENGTH_LONG).show()
                finish()
                return
            }
            editing = row
            dueAt = if (row.dueAt > System.currentTimeMillis() + 1000) row.dueAt
                    else System.currentTimeMillis() + 60 * 60_000L
        }
        showEditor()
    }

    private fun showEditor() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
            setPadding(dp(20), dp(22), dp(20), dp(20))
        }
        root.addView(button("← Back") { finish() },
            LinearLayout.LayoutParams(dp(106), dp(48)))
        gap(root, 14)
        root.addView(text(if (replacing != null) "Replace schedule" else if (editing == null) "New reminder" else "Edit reminder", 26f, true))
        root.addView(text(if (replacing != null)
            "Future dates use the new schedule. The original and its history remain archived."
            else "Choose when you want to be reminded.", 14f, false, secondary))
        gap(root, 15)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(surface, 16)
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        panel.addView(text("REMINDER", 12f, true, secondary))
        titleField = EditText(this).apply {
            hint = "What do you need to remember?"
            setText(editing?.title ?: replacing?.title ?: "")
            setSingleLine(true)
            maxLines = 1
            maxEms = 200
            setTextColor(ink)
            setHintTextColor(secondary)
            contentDescription = "Reminder title"
        }
        panel.addView(titleField)
        gap(panel, 12)
        panel.addView(text("DATE & TIME", 12f, true, secondary))
        dueView = text(formattedDue(), 16f, true)
        panel.addView(dueView)
        panel.addView(button("Change date and time") { chooseDateTime() })
        gap(panel, 12)

        panel.addView(text("REPEAT", 12f, true, secondary))
        repeatSelector = Spinner(this).apply {
            adapter = ArrayAdapter(this@QuietPrecisionReminderEditorActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Once", "Daily", "Selected weekdays", "Monthly"))
            isEnabled = editing == null
            contentDescription = "Reminder recurrence"
        }
        panel.addView(repeatSelector)
        replacing?.let { rule -> repeatSelector.setSelection(when (rule.mode) {
            "DAILY" -> 1
            "WEEKLY" -> 2
            else -> 3
        }) }
        if (editing != null) {
            panel.addView(text("Editing a one-time reminder. Manage recurring schedules in Tools.",
                12f, false, secondary))
        }
        weeklyChoice = button("Select weekly days") { chooseWeekdays() }
        weeklyChoice.isEnabled = editing == null
        panel.addView(weeklyChoice)
        panel.addView(text("For weekly reminders, choose at least one day. " +
            "Each due occurrence keeps its own outcome.", 12f, false, secondary))
        root.addView(panel)
        gap(root, 16)

        saveButton = button(if (replacing != null) "Replace schedule" else
            if (editing == null) "Save reminder" else "Save changes", true) {
            save()
        }
        root.addView(saveButton, LinearLayout.LayoutParams(-1, dp(52)))
        gap(root, 12)
        root.addView(text("Saved reminders work offline. Notification delivery depends on " +
            "device permissions and scheduling limits.", 12f, false, secondary))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(-1, -2))
        })
    }

    private fun chooseDateTime() {
        val original = Calendar.getInstance().apply { timeInMillis = dueAt }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                timeInMillis = dueAt
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(this, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                dueAt = selected.timeInMillis
                dueView.text = formattedDue()
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), false).show()
        }, original.get(Calendar.YEAR), original.get(Calendar.MONTH),
            original.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000L
        }.show()
    }

    private fun chooseWeekdays() {
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday", "Saturday", "Sunday")
        val chosen = BooleanArray(7) { weekdays and (1 shl it) != 0 }
        AlertDialog.Builder(this)
            .setTitle("Repeat on")
            .setMultiChoiceItems(days, chosen) { _, index, checked -> chosen[index] = checked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val mask = chosen.indices.fold(0) { n, i ->
                    if (chosen[i]) n or (1 shl i) else n
                }
                if (mask == 0) {
                    Toast.makeText(this, "Choose at least one weekday",
                        Toast.LENGTH_SHORT).show()
                } else {
                    weekdays = mask
                    weeklyChoice.text = "Weekdays selected: " + chosen.count { it }
                }
            }.show()
    }

    private fun save() {
        if (saving) return
        val title = titleField.text.toString().trim()
        if (title.isBlank() || title.length > 200) {
            titleField.error = "Enter a title (up to 200 characters)"
            return
        }
        if (dueAt <= System.currentTimeMillis() + 2000L) {
            Toast.makeText(this, "Select a future date and time", Toast.LENGTH_LONG).show()
            return
        }
        saving = true
        saveButton.isEnabled = false
        try {
            val originalRule = replacing
            val original = editing
            if (originalRule != null) {
                val mode = when (repeatSelector.selectedItemPosition) {
                    1 -> "DAILY"
                    2 -> "WEEKLY"
                    3 -> "MONTHLY"
                    else -> "ONCE"
                }
                if (mode == "ONCE") {
                    Toast.makeText(this, "Replacement must be a recurring schedule",
                        Toast.LENGTH_LONG).show()
                    return
                }
                val oldIds = store.pendingOccurrenceIdsForRoutine(originalRule.id)
                if (store.replaceRoutine(originalRule, title, dueAt, mode, weekdays) == null) {
                    Toast.makeText(this, "Schedule changed. Reopen to refresh.",
                        Toast.LENGTH_LONG).show()
                    return
                }
                oldIds.forEach { ReminderAlarmEngine.dismiss(this, it) }
            } else if (original != null) {
                // Never edit an old recurring occurrence using the one-time path.
                if (!store.edit(original.id, original.revision, title, dueAt)) {
                    Toast.makeText(this, "Reminder changed. Reopen to refresh.",
                        Toast.LENGTH_LONG).show()
                    return
                }
                ReminderAlarmEngine.dismiss(this, original.id)
            } else {
                val mode = when (repeatSelector.selectedItemPosition) {
                    1 -> "DAILY"
                    2 -> "WEEKLY"
                    3 -> "MONTHLY"
                    else -> "ONCE"
                }
                if (mode == "WEEKLY" && weekdays == 0) {
                    Toast.makeText(this, "Choose at least one weekday",
                        Toast.LENGTH_LONG).show()
                    return
                }
                if (mode == "ONCE") store.add(title, dueAt)
                else store.addRecurring(title, dueAt, mode, weekdays)
            }
            store.ensureRecurring()
            engine.flush()
            Toast.makeText(this,
                if (originalRule != null) "Schedule replaced; old history archived"
                else "Reminder saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save reminder: " +
                (e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        } finally {
            saving = false
            saveButton.isEnabled = true
        }
    }
}
