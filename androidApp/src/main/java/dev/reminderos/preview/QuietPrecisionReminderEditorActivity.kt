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
import android.view.View
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
    private val ui by lazy { QpVisual(this) }
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

    /**
     * R4b: R2 visual hierarchy while retaining existing validation/save logic.
     * The editor does not add unsupported notes/follow-up data fields.
     */
    private fun showEditor() {
        val root = ui.column().apply {
            setBackgroundColor(ui.canvas)
            setPadding(ui.px(20), ui.px(14), ui.px(20), ui.px(28))
        }
        root.addView(ui.action("‹  Back", secondary = true) { finish() },
            LinearLayout.LayoutParams(ui.px(112), ui.px(48)))
        root.addView(ui.space(20))
        val heading = when {
            replacing != null -> "Replace schedule"
            editing != null -> "Edit reminder"
            else -> "New reminder"
        }
        root.addView(ui.label("REMINDER DETAILS"))
        root.addView(ui.space(5))
        root.addView(ui.heading(heading, 27f))
        root.addView(ui.space(9))
        if (replacing != null) {
            val notice = ui.card(ui.tonal, ui.tonal, 14)
            notice.addView(ui.text(
                "A new schedule will replace future reminders. Previous outcomes stay archived.",
                13f, color = ui.tonalInk))
            root.addView(notice)
            root.addView(ui.space(16))
        } else {
            root.addView(ui.text("Create a clear reminder for what matters.",
                13f, color = ui.muted))
            root.addView(ui.space(18))
        }

        root.addView(ui.label("REMINDER TITLE"))
        root.addView(ui.space(7))
        titleField = EditText(this).apply {
            hint = "e.g. Call Mom"
            setText(editing?.title ?: replacing?.title ?: "")
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(200))
            setTextColor(ui.ink)
            setHintTextColor(ui.muted)
            textSize = 15f
            background = ui.shape(ui.surface, 11)
            setPadding(ui.px(14), ui.px(12), ui.px(14), ui.px(12))
            minimumHeight = ui.px(54)
            contentDescription = "Reminder title"
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
        root.addView(titleField, LinearLayout.LayoutParams(-1, ui.px(56)))
        root.addView(ui.space(18))

        root.addView(ui.label("DATE & TIME"))
        root.addView(ui.space(8))
        val dateCard = ui.card(ui.surface, ui.outline, 14)
        dateCard.isClickable = true
        dateCard.isFocusable = true
        dateCard.contentDescription = "Edit reminder date and time"
        dateCard.setOnClickListener { chooseDateTime() }
        val dateRow = ui.row()
        dateRow.addView(ui.insetIcon(R.drawable.qp_ic_calendar))
        val dueColumn = ui.column().apply {
            setPadding(ui.px(12),0,0,0)
            addView(ui.text("Scheduled for",12f,color=ui.muted))
            addView(ui.space(3))
        }
        dueView = ui.text(formattedDue(), 16f, android.graphics.Typeface.BOLD)
        dueColumn.addView(dueView)
        dateRow.addView(dueColumn, LinearLayout.LayoutParams(0,-2,1f))
        dateRow.addView(ui.icon(R.drawable.qp_ic_chevron,ui.muted))
        dateCard.addView(dateRow)
        root.addView(dateCard)
        root.addView(ui.space(18))

        root.addView(ui.label("SCHEDULE"))
        root.addView(ui.space(8))
        val options = arrayOf("Once", "Daily", "Selected weekdays", "Monthly")
        repeatSelector = Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@QuietPrecisionReminderEditorActivity,
                android.R.layout.simple_spinner_dropdown_item, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return ui.text(getItem(position) ?: "Once", 14f,
                        android.graphics.Typeface.BOLD).apply {
                        setPadding(ui.px(14),ui.px(8),ui.px(12),ui.px(8))
                    }
                }
                override fun getDropDownView(position: Int, convertView: View?,
                                             parent: ViewGroup): View {
                    return ui.text(getItem(position) ?: "Once", 14f).apply {
                        background = ui.shape(ui.surface, 11)
                        setPadding(ui.px(15),ui.px(13),ui.px(15),ui.px(13))
                    }
                }
            }
            isEnabled = editing == null
            background = ui.shape(ui.surface, 11)
            minimumHeight = ui.px(54)
            contentDescription = "Reminder recurrence"
        }
        replacing?.let { rule ->
            repeatSelector.setSelection(when (rule.mode) {
                "DAILY" -> 1
                "WEEKLY" -> 2
                else -> 3
            })
        }
        val recurrenceCard = ui.card(ui.surface,ui.outline,12)
        val recurrenceRow = ui.row()
        recurrenceRow.addView(ui.insetIcon(R.drawable.qp_ic_calendar))
        recurrenceRow.addView(ui.text("Repeat",14f,android.graphics.Typeface.BOLD)
            .apply { setPadding(ui.px(12),0,0,0) },LinearLayout.LayoutParams(0,-2,1f))
        recurrenceCard.addView(recurrenceRow)
        recurrenceCard.addView(ui.space(8))
        recurrenceCard.addView(repeatSelector,
            LinearLayout.LayoutParams(-1,ui.px(52)))
        recurrenceCard.addView(ui.space(8))
        weeklyChoice = button("Choose weekdays") { chooseWeekdays() }
        weeklyChoice.isEnabled = editing == null
        recurrenceCard.addView(weeklyChoice, LinearLayout.LayoutParams(-1,ui.px(48)))
        root.addView(recurrenceCard)
        root.addView(ui.space(11))

        val followup = ui.card(ui.surface,ui.outline,13)
        val followupRow = ui.row()
        followupRow.addView(ui.insetIcon(R.drawable.qp_ic_calendar))
        val followupColumn=ui.column().apply {
            setPadding(ui.px(12),0,0,0)
            addView(ui.text("Follow-up",14f,android.graphics.Typeface.BOLD))
            addView(ui.text("Not configurable in this alpha",12f,color=ui.muted))
        }
        followupRow.addView(followupColumn,LinearLayout.LayoutParams(0,-2,1f))
        followup.addView(followupRow)
        root.addView(followup)
        root.addView(ui.space(20))

        saveButton = button(if (replacing != null) "Replace schedule" else
            if (editing == null) "Save reminder" else "Save changes", true) { save() }
        root.addView(saveButton, LinearLayout.LayoutParams(-1,ui.px(54)))
        root.addView(ui.space(10))
        root.addView(ui.text("Saved offline. Notification delivery depends on device settings.",
            12f, color=ui.muted))

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(ui.canvas)
            addView(root,ViewGroup.LayoutParams(-1,-2))
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
