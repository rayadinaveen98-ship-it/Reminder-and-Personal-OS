package dev.reminderos.preview

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Android Alpha 0.2: deliberately plain UI for repeatable data and alarm testing.
 * Custom recurring schedules, cross-platform persistence, goals and analysis
 * remain future work. Do not use this preview as a critical alarm service.
 */
class MainActivity : Activity() {
    private val store by lazy { ReminderDatabase(this) }
    private val engine by lazy { ReminderAlarmEngine(this) }
    private lateinit var titleInput: EditText
    private lateinit var dueLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var countsLabel: TextView
    private lateinit var listContainer: LinearLayout
    private var selectedDue = System.currentTimeMillis() + 60 * 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderAlarmEngine.ensureChannel(this)
        buildUi()
        render()
    }

    override fun onResume() {
        super.onResume()
        engine.flush()
        if (::listContainer.isInitialized) render()
    }

    private fun text(message: String, size: Float = 15f) = TextView(this).apply {
        this.text = message
        textSize = size
        setPadding(0, 7, 0, 7)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun addSpace(parent: LinearLayout, px: Int = 10) {
        parent.addView(TextView(this), LinearLayout.LayoutParams(1, px))
    }

    private fun displayDate(epoch: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epoch))

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 30, 32, 40)
        }
        outer.addView(text("Reminder & Personal OS", 24f))
        outer.addView(text("Android Alpha · Local reminders only", 13f))
        outer.addView(text("Save real reminders, receive system alerts, and record what happened.", 14f))
        statusLabel = text("")
        outer.addView(statusLabel)
        if (Build.VERSION.SDK_INT >= 33) {
            outer.addView(button("Enable notification permission") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
            })
        }
        if (Build.VERSION.SDK_INT >= 31) {
            outer.addView(button("Exact alarm settings") {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + packageName))
                )
            })
        }

        addSpace(outer, 18)
        outer.addView(text("Create reminder", 19f))
        titleInput = EditText(this).apply {
            hint = "What should I remind you about?"
            setSingleLine(true)
            maxLines = 1
        }
        outer.addView(titleInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        dueLabel = text("")
        outer.addView(dueLabel)
        outer.addView(button("Choose date and time") { pickDue() })
        outer.addView(button("Save reminder") {
            val title = titleInput.text.toString().trim()
            when {
                title.isBlank() -> toast("Enter a reminder title")
                selectedDue <= System.currentTimeMillis() + 1000L ->
                    toast("Choose a future date and time")
                else -> {
                    try {
                        store.add(title, selectedDue)
                        engine.flush()
                        titleInput.text.clear()
                        toast("Reminder saved")
                        selectedDue = System.currentTimeMillis() + 60 * 60_000L
                        render()
                    } catch (e: Exception) { toast("Save failed: " + (e.message ?: "Unknown error")) }
                }
            }
        })

        addSpace(outer, 18)
        outer.addView(text("Your reminders", 19f))
        countsLabel = text("")
        outer.addView(countsLabel)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(listContainer)
        addSpace(outer, 15)
        outer.addView(text(
            "This early build supports one-time reminders only. Notifications may be delayed by the OS. " +
                "Overdue and unanswered reminders stay unresolved until you act.", 13f))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(outer)
        })
    }

    private fun pickDue() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDue }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply { timeInMillis = selectedDue }
            selected.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour)
                selected.set(Calendar.MINUTE, minute)
                selected.set(Calendar.SECOND, 0)
                selected.set(Calendar.MILLISECOND, 0)
                selectedDue = selected.timeInMillis
                dueLabel.text = "Due: " + displayDate(selectedDue)
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000L
        }.show()
    }

    private fun render() {
        if (!::listContainer.isInitialized) return
        statusLabel.text = engine.capabilityText()
        dueLabel.text = "Due: " + displayDate(selectedDue)
        val (pending, done, skipped) = store.counts()
        countsLabel.text = "Pending " + pending + "  ·  Done " + done + "  ·  Skipped " + skipped
        listContainer.removeAllViews()
        val reminders = store.all()
        if (reminders.isEmpty()) {
            listContainer.addView(text("No reminders saved yet. Create your first one above."))
            return
        }
        val now = System.currentTimeMillis()
        for (item in reminders) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(15, 15, 15, 15)
                setBackgroundColor(0xffeeeeee.toInt())
            }
            card.addView(text(item.title, 18f))
            val humanState = if (item.status == "PENDING" && item.dueAt < now)
                "Overdue · not confirmed" else item.status
            card.addView(text(displayDate(item.dueAt) + "  ·  " + humanState, 13f))
            if (item.status == "PENDING") {
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun mini(label: String, command: String) {
                    val b = button(label) {
                        try {
                            if (store.act(item.id, item.revision, command)) {
                                ReminderAlarmEngine.dismiss(this, item.id)
                                engine.flush()
                                render()
                            } else {
                                toast("Reminder already changed; refresh the list")
                                render()
                            }
                        } catch (e: Exception) {
                            toast("Unable to update: " + (e.message ?: "Unknown error"))
                        }
                    }
                    actions.addView(b, LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                mini("Done", "DONE")
                mini("Snooze 10m", "SNOOZE")
                mini("Skip", "SKIP")
                card.addView(actions)
            }
            listContainer.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addSpace(listContainer, 9)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            engine.flush()
            render()
        }
    }
}
