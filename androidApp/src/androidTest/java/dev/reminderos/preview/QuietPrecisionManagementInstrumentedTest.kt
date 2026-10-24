package dev.reminderos.preview

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only smoke test: does not create, archive, pause or delete reminders. */
@RunWith(AndroidJUnit4::class)
class QuietPrecisionManagementInstrumentedTest {
    private fun labels(root: View): List<String> {
        val items = mutableListOf<String>()
        if (root is TextView) items.add(root.text.toString())
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) items.addAll(labels(root.getChildAt(index)))
        }
        return items
    }

    @Test fun schedulesAndSettingsScreensExposeAccurateCapabilities() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrument.targetContext,
            QuietPrecisionManagementActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(QuietPrecisionManagementActivity.EXTRA_PAGE,
                QuietPrecisionManagementActivity.PAGE_SETTINGS)
        val activity = instrument.startActivitySync(intent) as QuietPrecisionManagementActivity
        try {
            instrument.waitForIdleSync()
            val settings = labels(activity.window.decorView)
            assertTrue(settings.contains("Settings"))
            assertTrue(settings.contains("Notification reliability"))
            assertTrue(settings.contains("Local diagnostics"))
            assertTrue(settings.contains("Encrypted backups"))
            assertTrue(settings.any { it.contains("NOT implemented") })
            val select = findClickable(activity.window.decorView, "Reminders")
            assertTrue(select != null)
            instrument.runOnMainSync { select!!.performClick() }
            instrument.waitForIdleSync()
            val management = labels(activity.window.decorView)
            assertTrue(management.contains("Manage reminders"))
            assertTrue(management.contains("Repeating schedules"))
        } finally {
            instrument.runOnMainSync { activity.finish() }
        }
    }
    private fun findClickable(root: View, target: String): TextView? {
        if (root is TextView && root.text.toString() == target &&
            root.hasOnClickListeners()) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findClickable(root.getChildAt(i), target)
                if (found != null) return found
            }
        }
        return null
    }
}
