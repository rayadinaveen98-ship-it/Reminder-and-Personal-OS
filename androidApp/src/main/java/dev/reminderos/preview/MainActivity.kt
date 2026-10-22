package dev.reminderos.preview

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.reminderos.core.AlertIntent
import dev.reminderos.core.PlatformRegistration
import dev.reminderos.core.PlatformScheduleResult
import java.text.DateFormat
import java.util.Date

/**
 * Android-only native scheduling test harness, NOT the V1 product UI.
 * One reminder, 60 seconds from button press, no outcomes or analytics.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private val gateway by lazy { PreviewAlarmGateway(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreviewAlarmGateway.ensureChannel(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        fun addButton(title: String, handler: () -> Unit) {
            layout.addView(Button(this).apply {
                text = title
                setOnClickListener { handler() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        layout.addView(TextView(this).apply {
            textSize = 23f
            text = "Reminder OS — native scheduling preview"
        })
        layout.addView(TextView(this).apply {
            text = "This is a test harness. Scheduling an alert is not task completion."
            textSize = 15f
        })
        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)
        addButton("Schedule test reminder in 60 seconds") { scheduleTest() }
        addButton("Cancel test reminder") { cancelTest() }
        if (Build.VERSION.SDK_INT >= 31) {
            addButton("Open exact-alarm permission settings") {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName"))
                )
            }
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() {
        super.onResume()
        // A previous native scheduling attempt may have failed after OS permission changes.
        val due = PreviewStore.due(this)
        if (due > System.currentTimeMillis()) {
            gateway.schedule(AlertIntent(PreviewAlarmGateway.PREVIEW_ID, due, 1))
        }
        refreshStatus()
    }

    private fun scheduleTest() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 301)
            return
        }
        val due = System.currentTimeMillis() + 60_000L
        // Save before registering; restore can retry after interruption.
        PreviewStore.save(this, due)
        val result = gateway.schedule(AlertIntent(PreviewAlarmGateway.PREVIEW_ID, due, 1))
        if (result is PlatformScheduleResult.Rejected) {
            status.text = "Could not schedule: ${result.reasonCode}. Check permissions."
        } else {
            refreshStatus()
        }
    }

    private fun cancelTest() {
        val due = PreviewStore.due(this)
        if (due > 0) {
            val cancelled = gateway.cancel(
                PlatformRegistration(PreviewAlarmGateway.PREVIEW_ID,
                    PreviewAlarmGateway.PREVIEW_ID, due, 1)
            )
            if (cancelled) PreviewStore.clear(this)
            else status.text = "Cancellation not confirmed; try again."
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val capability = gateway.capabilities()
        val due = PreviewStore.due(this)
        val pending = if (due > System.currentTimeMillis())
            DateFormat.getDateTimeInstance().format(Date(due))
        else if (due > 0) "Overdue / unresolved test alert"
        else "None"
        status.text = "Capability: ${capability.capability}\n${capability.note.orEmpty()}\n" +
            "Stored test due: $pending"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 301) {
            refreshStatus()
        }
    }
}
