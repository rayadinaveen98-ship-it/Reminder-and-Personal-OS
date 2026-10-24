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

/** R3b UI smoke: no reminder is created or modified by this test. */
@RunWith(AndroidJUnit4::class)
class QuietPrecisionEditorInstrumentedTest {
    @Test fun editorLaunchesAndDisplaysFieldsWithoutMutatingReminderStore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(ctx, QuietPrecisionReminderEditorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as QuietPrecisionReminderEditorActivity
        try {
            instrumentation.waitForIdleSync()
            val texts = mutableListOf<String>()
            fun walk(view: View) {
                if (view is TextView) texts.add(view.text.toString())
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) walk(view.getChildAt(index))
                }
            }
            walk(activity.window.decorView)
            assertTrue(texts.contains("New reminder"))
            assertTrue(texts.contains("Save reminder"))
            assertTrue(texts.contains("DATE & TIME"))
            assertTrue(texts.contains("Choose weekdays"))
            assertTrue(texts.contains("SCHEDULE"))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
