package dev.reminderos.preview

import android.content.Intent
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
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

            capture(instrumentation,root,"quiet_precision_r4a_today.png")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun editorAndManagementCaptureReadableNativeStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ctx = instrumentation.targetContext
        val editor = instrumentation.startActivitySync(
            Intent(ctx, QuietPrecisionReminderEditorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as QuietPrecisionReminderEditorActivity
        try {
            instrumentation.waitForIdleSync()
            val root=editor.window.decorView
            val texts=labels(root)
            assertTrue(texts.contains("New reminder"))
            assertTrue(texts.contains("REMINDER TITLE"))
            assertTrue(texts.contains("DATE & TIME"))
            assertTrue(texts.contains("SCHEDULE"))
            assertTrue(texts.contains("Save reminder"))
            capture(instrumentation,root,"quiet_precision_r4b_editor.png")
        } finally {
            instrumentation.runOnMainSync { editor.finish() }
        }

        val management = instrumentation.startActivitySync(
            Intent(ctx, QuietPrecisionManagementActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as QuietPrecisionManagementActivity
        try {
            instrumentation.waitForIdleSync()
            val root=management.window.decorView
            val texts=labels(root)
            assertTrue(texts.contains("Manage reminders"))
            assertTrue(texts.contains("Repeating schedules"))
            capture(instrumentation,root,"quiet_precision_r4b_management.png")
        } finally {
            instrumentation.runOnMainSync { management.finish() }
        }
    }

    private fun capture(
        instrumentation: android.app.Instrumentation,
        root: View, filename: String
    ) {
        assertTrue(root.width>0 && root.height>0)
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { root.draw(Canvas(bitmap)) }
        val ctx=instrumentation.targetContext
        val folder=File(ctx.filesDir,"visual-qa").apply { mkdirs() }
        val file=File(folder,filename)
        FileOutputStream(file).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
        }
        assertTrue(file.length()>9_000L)

        // Use emulator MediaStore to retain QA screenshots after Gradle's
        // instrumentation task uninstalls its test/target packages.
        // Never runs outside androidTest; no production app permission needed.
        val resolver=ctx.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/QuietPrecisionQA")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri=requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
        ) { "Could not create native QA screenshot in shared media" }
        try {
            val stream=requireNotNull(resolver.openOutputStream(uri)) {
                "Could not write native QA screenshot"
            }
            stream.use { require(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            val ready=ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) }
            resolver.update(uri,ready,null,null)
        } catch(e:Exception) {
            resolver.delete(uri,null,null)
            throw e
        } finally {
            bitmap.recycle()
        }
    }
}
