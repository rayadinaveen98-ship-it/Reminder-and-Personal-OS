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

/** Device-only R3 smoke: no deletion, import, scheduling mutation or fake data. */
@RunWith(AndroidJUnit4::class)
class QuietPrecisionShellInstrumentedTest {
    private fun visibleText(view: View): List<String> {
        val result = mutableListOf<String>()
        if (view is TextView) result.add(view.text.toString())
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) result.addAll(visibleText(view.getChildAt(index)))
        }
        return result
    }

    @Test fun shellShowsNativeTabsAndNeverClaimsTrackingIsImplemented() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, QuietPrecisionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as QuietPrecisionActivity
        try {
            instrumentation.waitForIdleSync()
            val original = visibleText(activity.window.decorView)
            assertTrue(original.contains("Today"))
            assertTrue(original.contains("Track"))
            assertTrue(original.contains("Insights"))
            assertTrue(original.contains("Library"))
            val trackButton = findText(activity.window.decorView, "Track")
            assertTrue(trackButton != null)
            instrumentation.runOnMainSync { trackButton!!.performClick() }
            instrumentation.waitForIdleSync()
            val after = visibleText(activity.window.decorView)
            assertTrue(after.any { it.contains("not implemented in this alpha") })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun findText(root: View, text: String): TextView? {
        if (root is TextView && root.text.toString() == text && root.hasOnClickListeners()) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findText(root.getChildAt(index), text)
                if (found != null) return found
            }
        }
        return null
    }
}
