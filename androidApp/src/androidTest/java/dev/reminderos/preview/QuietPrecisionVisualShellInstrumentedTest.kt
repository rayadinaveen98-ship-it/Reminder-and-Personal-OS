package dev.reminderos.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * R4a: semantic shell acceptance + real emulator screenshot.
 *
 * This proves layout is RENDERED, not yet that pixels match the R2 prototype.
 * No fixture is inserted, no data is deleted and no user reminders are edited.
 */
@RunWith(AndroidJUnit4::class)
class QuietPrecisionVisualShellInstrumentedTest {
    private fun labels(view: View): List<String> {
        val found = mutableListOf<String>()
        if (view is TextView) found.add(view.text.toString())
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) found.addAll(labels(view.getChildAt(i)))
        }
        return found
    }

    @Test fun homeHasReferenceHierarchyNavigationAndCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(ctx, QuietPrecisionVisualActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as QuietPrecisionVisualActivity
        try {
            instrumentation.waitForIdleSync()
            val root = activity.window.decorView
            val texts = labels(root)
            assertTrue(texts.any { it.startsWith("Good ") })
            assertTrue(texts.contains("NEXT REMINDER"))
            assertTrue(texts.contains("Upcoming"))
            assertTrue(texts.contains("Unresolved"))
            assertTrue(texts.contains("Up next"))
            assertTrue(texts.contains("Needs your response"))
            assertTrue(texts.contains("Today"))
            assertTrue(texts.contains("Track"))
            assertTrue(texts.contains("Insights"))
            assertTrue(texts.contains("Library"))
            assertTrue(root.width > 0 && root.height > 0)

            // A captured emulator render is saved for human comparison with R2.
            val bitmap = Bitmap.createBitmap(root.width, root.height,
                Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync { root.draw(Canvas(bitmap)) }
            val folder = File(ctx.filesDir, "visual-qa").apply { mkdirs() }
            val result = File(folder, "quiet_precision_r4a_today.png")
            FileOutputStream(result).use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            assertTrue(result.length() > 15_000L)
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
