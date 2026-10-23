package dev.reminderos.preview

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import java.io.ByteArrayOutputStream
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Android Alpha V0.3b: deliberately plain UI for recurrence testing.
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
    private lateinit var repeatSpinner: Spinner
    private var weeklyDaysMask = 31 // Monday to Friday by default

    private val backupRepository by lazy { ReminderBackupRepository(this) }
    private var pendingBackupPassword: CharArray? = null
    private var editingId: String? = null
    private var editingRevision: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderAlarmEngine.ensureChannel(this)
        buildUi()
        render()
    }

    override fun onResume() {
        super.onResume()
        store.ensureRecurring()
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
        outer.addView(text("Android V0.3c · Encrypted backup test", 13f))
        outer.addView(text("Save real reminders, receive system alerts, and record what happened.", 14f))
        statusLabel = text("")
        outer.addView(statusLabel)
        outer.addView(text("Private backup & restore",19f))
        outer.addView(button("Export encrypted backup") { askForBackupPassword(true) })
        outer.addView(button("Restore encrypted backup (empty app only)") { askForBackupPassword(false) })
        outer.addView(text("Keep your backup password safe. Restore only works when this app has no existing reminders. Existing data is never overwritten.",12f))
        outer.addView(button("Check & repair future alerts") {
            val count = store.reconcileFuture()
            engine.flush()
            toast("Rechecked $count active future reminders")
            render()
        })
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
        outer.addView(text("Create or edit reminder", 19f))
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
        outer.addView(text("Repeat", 15f))
        repeatSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Once", "Daily", "Every week · choose days", "Monthly")
            )
        }
        outer.addView(repeatSpinner)
        outer.addView(button("Choose weekly days (when repeating weekly)") {
            val days = arrayOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
            val chosen = BooleanArray(7) { i -> weeklyDaysMask and (1 shl i) != 0 }
            AlertDialog.Builder(this)
                .setTitle("Repeat on")
                .setMultiChoiceItems(days, chosen) { _, which, checked -> chosen[which] = checked }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val mask = chosen.indices.filter { chosen[it] }.fold(0) { acc, i -> acc or (1 shl i) }
                    if(mask == 0) toast("Select at least one weekday")
                    else { weeklyDaysMask = mask; toast("Selected: " + RecurrenceSchedule.description("WEEKLY", mask)) }
                }.show()
        })
        outer.addView(button("Save reminder / Save changes") {
            val title = titleInput.text.toString().trim()
            when {
                title.isBlank() -> toast("Enter a reminder title")
                selectedDue <= System.currentTimeMillis() + 1000L ->
                    toast("Choose a future date and time")
                else -> {
                    try {
                        val edit = editingId
                        val rev = editingRevision
                        if (edit == null || rev == null) {
                            val mode = when (repeatSpinner.selectedItemPosition) {
                                1 -> "DAILY"
                                2 -> "WEEKLY"
                                3 -> "MONTHLY"
                                else -> "ONCE"
                            }
                            if (mode == "ONCE") {
                                store.add(title, selectedDue)
                                toast("Reminder saved")
                            } else {
                                store.addRecurring(title, selectedDue, mode, weeklyDaysMask)
                                toast("Repeating reminder saved")
                            }
                        } else {
                            if (!store.edit(edit, rev, title, selectedDue)) {
                                toast("Could not edit: reminder was changed or resolved")
                                render()
                                return@button
                            }
                            ReminderAlarmEngine.dismiss(this, edit)
                            toast("Reminder updated")
                        }
                        store.ensureRecurring()
                        engine.flush()
                        clearEditor()
                        render()
                    } catch (e: Exception) { toast("Save failed: " + (e.message ?: "Unknown error")) }
                }
            }
        })
        outer.addView(button("Clear form / Cancel edit") {
            clearEditor()
            render()
        })

        addSpace(outer, 18)
        outer.addView(text("Your reminders", 19f))
        countsLabel = text("")
        outer.addView(countsLabel)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(listContainer)
        addSpace(outer, 15)
        outer.addView(text(
            "V0.3b test build. Every repeat has its own outcome. Unanswered slots stay unresolved " +
                "while upcoming slots continue. Notification timing depends on Android settings.", 13f))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(outer)
        })
    }

    private fun askForBackupPassword(export: Boolean) {
        if (!export && backupRepository.hasUserRecords()) {
            AlertDialog.Builder(this)
                .setTitle("Restore blocked to protect existing reminders")
                .setMessage("This app already contains reminder data. Restore cannot overwrite or merge it. Use a new empty installation and retain the current app.")
                .setPositiveButton("OK",null).show()
            return
        }
        val inputs=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(32,12,32,12)
        }
        val pass=EditText(this).apply {
            hint="Backup passphrase"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine=true
        }
        inputs.addView(pass)
        val confirm=if(export) EditText(this).apply {
            hint="Confirm passphrase"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine=true
        } else null
        if(confirm != null) inputs.addView(confirm)
        AlertDialog.Builder(this)
            .setTitle(if(export) "Create encrypted backup" else "Restore encrypted backup")
            .setMessage(if(export)
                "Use a unique passphrase of at least 12 characters. If forgotten, the exported backup cannot be recovered."
                else "The correct password is required. Restoring does not copy Android alarm permissions or OS registrations.")
            .setView(inputs)
            .setNegativeButton("Cancel",null)
            .setPositiveButton(if(export) "Choose backup location" else "Choose backup file") { _, _ ->
                val chosen=pass.text.toString()
                if(chosen.isBlank() || export && chosen.length<12) {
                    toast("Enter a passphrase of at least 12 characters for export")
                    return@setPositiveButton
                }
                if(export && chosen != confirm?.text?.toString()) {
                    toast("Passphrases do not match")
                    return@setPositiveButton
                }
                pendingBackupPassword?.fill('\u0000')
                pendingBackupPassword=chosen.toCharArray()
                val intent=if(export) Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE,"reminder-os-backup.rpos")
                } else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="*/*"
                }
                try { startActivityForResult(intent,if(export) REQUEST_EXPORT else REQUEST_IMPORT) }
                catch (_: ActivityNotFoundException) {
                    pendingBackupPassword?.fill('\u0000')
                    pendingBackupPassword=null
                    toast("System file picker unavailable")
                }
            }.show()
    }

    @Deprecated("Used only for this isolated Android 8+ developer alpha")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode != REQUEST_EXPORT && requestCode != REQUEST_IMPORT) return
        val password=pendingBackupPassword
        pendingBackupPassword=null
        if(resultCode != Activity.RESULT_OK || data?.data == null || password == null) {
            password?.fill('\u0000')
            return
        }
        val uri=data.data!!
        try {
            if(requestCode == REQUEST_EXPORT) {
                val sealed=backupRepository.exportEncrypted(password)
                try {
                    contentResolver.openOutputStream(uri,"w")?.use { it.write(sealed); it.flush() }
                        ?: throw IllegalStateException("Cannot write the selected document")
                    toast("Encrypted backup saved. Keep the passphrase safe.")
                } finally { sealed.fill(0) }
            } else {
                val encrypted=contentResolver.openInputStream(uri)?.use { input ->
                    val out=ByteArrayOutputStream()
                    val chunk=ByteArray(8192)
                    while(true) {
                        val n=input.read(chunk)
                        if(n<0) break
                        out.write(chunk,0,n)
                        require(out.size() <= EncryptedBackup.MAX_FILE_BYTES) {
                            "Backup exceeds supported size limit"
                        }
                    }
                    out.toByteArray()
                } ?: throw IllegalStateException("Cannot read backup file")
                try {
                    val imported=backupRepository.importEncrypted(encrypted,password)
                    store.ensureRecurring()
                    store.recoverFuture()
                    engine.flush()
                    render()
                    toast("Restored $imported occurrences. Future alerts rebuilt.")
                } finally { encrypted.fill(0) }
            }
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Backup operation not completed")
                .setMessage(e.message ?: "File could not be processed. Your existing reminders have not been overwritten.")
                .setPositiveButton("OK",null).show()
        } finally { password.fill('\u0000') }
    }

    private companion object {
        const val REQUEST_EXPORT=7201
        const val REQUEST_IMPORT=7202
    }

    private fun clearEditor() {
        editingId = null
        editingRevision = null
        titleInput.text.clear()
        selectedDue = System.currentTimeMillis() + 60 * 60_000L
        if (::repeatSpinner.isInitialized) repeatSpinner.setSelection(0)
    }

    private fun editItem(item: ReminderRecord) {
        editingId = item.id
        editingRevision = item.revision
        titleInput.setText(item.title)
        repeatSpinner.setSelection(0)
        selectedDue = if (item.dueAt > System.currentTimeMillis() + 1000L)
            item.dueAt else System.currentTimeMillis() + 60 * 60_000L
        render()
        titleInput.requestFocus()
        toast("Edit in the form above, then Save changes")
    }

    private fun archiveItem(item: ReminderRecord) {
        AlertDialog.Builder(this)
            .setTitle("Archive reminder?")
            .setMessage("This hides the reminder from the list, cancels its future alert, and retains its history.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Archive") { _, _ ->
                if (store.archive(item.id, item.revision)) {
                    ReminderAlarmEngine.dismiss(this, item.id)
                    engine.flush()
                    if (editingId == item.id) clearEditor()
                    render()
                } else toast("Reminder changed; reload and try again")
            }.show()
    }

    private fun snoozeItem(item: ReminderRecord) {
        val choices = arrayOf("5 minutes", "10 minutes", "30 minutes", "60 minutes")
        val durations = intArrayOf(5, 10, 30, 60)
        AlertDialog.Builder(this)
            .setTitle("Snooze reminder")
            .setItems(choices) { _, index ->
                applyAction(item, "SNOOZE", durations[index])
            }.show()
    }

    private fun showHistory(item: ReminderRecord) {
        val events = store.recentEvents(item.id)
        val details = if (events.isEmpty()) "No actions recorded yet"
            else events.joinToString("\n") { it.kind + " · " + displayDate(it.atMs) }
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun applyAction(item: ReminderRecord, action: String, minutes: Int = 10) {
        try {
            if (store.act(item.id, item.revision, action, snoozeMinutes = minutes)) {
                ReminderAlarmEngine.dismiss(this, item.id)
                engine.flush()
                render()
            } else {
                toast("Reminder already changed or paused")
                render()
            }
        } catch (e: Exception) {
            toast("Unable to update: " + (e.message ?: "Unknown error"))
        }
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
        val routines = store.rules()
        if (routines.isNotEmpty()) {
            listContainer.addView(text("Repeating schedules", 19f))
            for (rule in routines) {
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(15, 12, 15, 12)
                    setBackgroundColor(0xffe6f2f3.toInt())
                }
                panel.addView(text(rule.title,18f))
                panel.addView(text(
                    RecurrenceSchedule.description(rule.mode,rule.daysMask) +
                        if (rule.paused) " · Paused" else " · Active",13f))
                val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun option(label: String, click: () -> Unit) {
                    buttons.addView(button(label,click),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                }
                option(if(rule.paused) "Resume" else "Pause") {
                    if(store.setRoutinePaused(rule.id,!rule.paused)) {
                        if(!rule.paused) {
                            for (occ in store.all().filter { it.seriesId == rule.id }) {
                                ReminderAlarmEngine.dismiss(this,occ.id)
                            }
                        }
                        store.ensureRecurring()
                        engine.flush()
                        render()
                    } else toast("Routine was changed. Reopen this screen.")
                }
                option("History") {
                    AlertDialog.Builder(this).setTitle(rule.title+" · History")
                        .setMessage(store.routineHistory(rule.id))
                        .setPositiveButton("Close",null).show()
                }
                option("Archive") {
                    AlertDialog.Builder(this).setTitle("Archive repeating schedule?")
                        .setMessage("Stops all future alerts and retains occurrence history.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Archive") { _, _ ->
                            if(store.archiveRoutine(rule.id)) {
                                engine.flush()
                                render()
                            }
                        }.show()
                }
                panel.addView(buttons)
                listContainer.addView(panel)
                addSpace(listContainer,8)
            }
            listContainer.addView(text("Upcoming reminders",19f))
        }
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
            if(item.seriesId != null) {
                card.addView(text("Repeat · " + (item.slotKey ?: "") +
                    " · one independent occurrence",12f))
            }
            val humanState = when {
                item.paused -> "Paused"
                item.status == "PENDING" && item.dueAt < now -> "Overdue · not confirmed"
                else -> item.status
            }
            card.addView(text(displayDate(item.dueAt) + "  ·  " + humanState, 13f))
            if (item.status == "PENDING") {
                if (!item.paused) {
                    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    fun mini(label: String, click: () -> Unit) {
                        actions.addView(button(label, click),
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    mini("Done") { applyAction(item, "DONE") }
                    mini("Snooze") { snoozeItem(item) }
                    mini("Skip") { applyAction(item, "SKIP") }
                    card.addView(actions)
                }
                val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun tool(label: String, click: () -> Unit) {
                    tools.addView(button(label, click),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                if (item.seriesId == null) {
                    tool("Edit") { editItem(item) }
                    tool(if (item.paused) "Resume" else "Pause") {
                        if (store.setPaused(item.id, item.revision, !item.paused)) {
                            ReminderAlarmEngine.dismiss(this, item.id)
                            engine.flush()
                            render()
                        } else toast("Cannot resume an overdue reminder; edit its due time first")
                    }
                    tool("Archive") { archiveItem(item) }
                    card.addView(tools)
                }
            }
            card.addView(button("History") { showHistory(item) })
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
