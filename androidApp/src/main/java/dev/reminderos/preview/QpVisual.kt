package dev.reminderos.preview

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * R4a visual tokens for the approved Quiet Precision R2 reference.
 *
 * Presentation only: never accesses reminders, status or notification state.
 * All tokens and components are designed for native system font scaling.
 */
internal class QpVisual(private val context: Context) {
    private val dark = (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val canvas = hex(if (dark) "#121B27" else "#F6F8FC")
    val surface = hex(if (dark) "#1D2938" else "#FFFFFF")
    val secondarySurface = hex(if (dark) "#263346" else "#F1F5FA")
    val tonal = hex(if (dark) "#263656" else "#EEF3FF")
    val ink = hex(if (dark) "#F3F6FA" else "#1D2937")
    val muted = hex(if (dark) "#A0AFC1" else "#5F6E80")
    val primary = hex(if (dark) "#87A7FC" else "#3D63CF")
    val tonalInk = hex(if (dark) "#B3C6FF" else "#355CBF")
    val whiteOnPrimary = hex(if (dark) "#101821" else "#FFFFFF")
    val outline = hex(if (dark) "#354457" else "#DFE6F0")
    val positive = hex(if (dark) "#79CDB1" else "#196E55")
    val caution = hex(if (dark) "#E3B279" else "#995B1E")
    val caption = 12f
    fun px(v: Int) = (v * context.resources.displayMetrics.density + 0.5f).toInt()
    private fun hex(s: String) = Color.parseColor(s)

    fun shape(background: Int = surface, radius: Int = 16, border: Int = outline):
        GradientDrawable = GradientDrawable().apply {
            setColor(background)
            cornerRadius = px(radius).toFloat()
            setStroke(px(1), border)
        }

    fun text(value: String, size: Float = 14f, weight: Int = Typeface.NORMAL,
             color: Int = ink): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        typeface = Typeface.create("sans-serif", weight)
        includeFontPadding = true
        setLineSpacing(px(1).toFloat(), 1.03f)
    }

    fun heading(value: String, size: Float = 23f): TextView =
        text(value, size, Typeface.BOLD, ink)

    fun label(value: String, color: Int = muted): TextView =
        text(value, caption, Typeface.BOLD, color).apply { letterSpacing = .075f }

    fun column(padding: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (padding != 0) setPadding(px(padding),px(padding),px(padding),px(padding))
    }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(background: Int = surface, border: Int = outline, pad: Int = 16) =
        column().apply {
            this.background = shape(background,16,border)
            setPadding(px(pad),px(pad),px(pad),px(pad))
        }

    fun space(height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, px(height))
    }

    fun icon(resource: Int, tint: Int = primary, description: String? = null,
             size: Int = 20): ImageView = ImageView(context).apply {
        setImageResource(resource)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = description
        layoutParams = LinearLayout.LayoutParams(px(size),px(size))
        importantForAccessibility = if (description == null)
            View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun action(value: String, selected: Boolean = false, secondary: Boolean = false,
               onClick: () -> Unit): TextView = text(value,13f,Typeface.BOLD,
            if (selected && !secondary) whiteOnPrimary else
                if (secondary) tonalInk else primary).apply {
        gravity = Gravity.CENTER
        minHeight = px(48)
        setPadding(px(13),px(10),px(13),px(10))
        background = RippleDrawable(ColorStateList.valueOf(tonalInk),
            shape(if (selected && !secondary) primary else surface,11,
                if (selected && !secondary) primary else outline),null)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun iconButton(resource: Int, description: String, onClick: () -> Unit):
        LinearLayout = row().apply {
        gravity = Gravity.CENTER
        minWidth = px(48)
        minHeight = px(48)
        background = RippleDrawable(ColorStateList.valueOf(tonal),
            shape(surface,12),null)
        addView(icon(resource, ink, null, 20))
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun insetIcon(resource: Int): LinearLayout = row().apply {
        gravity = Gravity.CENTER
        background = shape(tonal,12,tonal)
        addView(icon(resource, primary, null, 20))
        layoutParams = LinearLayout.LayoutParams(px(42),px(42))
    }

    fun divider(): View = View(context).apply {
        setBackgroundColor(outline)
        layoutParams = LinearLayout.LayoutParams(-1,px(1))
    }
}
