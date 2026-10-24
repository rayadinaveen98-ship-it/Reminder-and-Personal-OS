package dev.reminderos.preview

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * R4a: R2 design-faithful native Android shell.
 *
 * Only PRESENTATION is replaced. Reads, writes, action revisions, scheduled
 * alarms, archives and encrypted backup continue through existing R3e code.
 * Track and evidence-backed Insights remain clearly unimplemented.
 */
class QuietPrecisionVisualActivity : Activity() {
    private val store by lazy { ReminderDatabase(this) }
    private val alarms by lazy { ReminderAlarmEngine(this) }
    private val ui by lazy { QpVisual(this) }
    private enum class Tab(val title: String, val icon: Int) {
        TODAY("Today", R.drawable.qp_ic_home),
        TRACK("Track", R.drawable.qp_ic_track),
        INSIGHTS("Insights", R.drawable.qp_ic_insights),
        LIBRARY("Library", R.drawable.qp_ic_library)
    }
    private var tab = Tab.TODAY

    private fun lp(width: Int = -1, height: Int = -2) =
        LinearLayout.LayoutParams(width, height)
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun spacer(n: Int) = ui.space(n)
    private fun date(time: Long) =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
    private fun time(time: Long) =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        ReminderAlarmEngine.ensureChannel(this)
        if (state != null) {
            tab = Tab.values().getOrElse(state.getInt("tab",0)) { Tab.TODAY }
        }
        window.statusBarColor = ui.canvas
        window.navigationBarColor = ui.surface
        window.decorView.systemUiVisibility = if (
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            != android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        else 0
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", tab.ordinal)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        try {
            store.ensureRecurring()
            store.reconcileFuture()
            alarms.flush()
        } catch (error: Exception) {
            Toast.makeText(this,
                "Reminder scheduling needs attention. See Settings for recovery.",
                Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun editor(id: String? = null) {
        val intent = Intent(this, QuietPrecisionReminderEditorActivity::class.java)
        if (id != null) intent.putExtra("reminder_id",id)
        startActivity(intent)
    }
    private fun management(settings: Boolean) {
        startActivity(Intent(this,QuietPrecisionManagementActivity::class.java).apply {
            putExtra(QuietPrecisionManagementActivity.EXTRA_PAGE,
                if (settings) QuietPrecisionManagementActivity.PAGE_SETTINGS
                else QuietPrecisionManagementActivity.PAGE_SCHEDULES)
        })
    }
    private fun archive() {
        startActivity(Intent(this,QuietPrecisionHistoryActivity::class.java))
    }
    private fun quickAdd() {
        val options = arrayOf("Reminder", "Activity log · Planned",
            "Tracker · Planned", "Routine · Planned", "Goal · Planned")
        AlertDialog.Builder(this)
            .setTitle("Quick Add")
            .setItems(options) { _, index ->
                if (index == 0) editor()
                else Toast.makeText(this,
                    "This feature is not available in the current alpha.",
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel",null)
            .show()
    }

    private fun render() {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(ui.canvas)
        }
        val column = ui.column()
        frame.addView(column, FrameLayout.LayoutParams(-1,-1))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = ui.column().apply {
            setPadding(ui.px(20),ui.px(20),ui.px(20),ui.px(104))
        }
        try {
            when(tab) {
                Tab.TODAY -> today(content)
                Tab.TRACK -> track(content)
                Tab.INSIGHTS -> insights(content)
                Tab.LIBRARY -> library(content)
            }
        } catch (error: Exception) {
            content.removeAllViews()
            content.addView(ui.heading("Unable to load reminders"))
            content.addView(spacer(12))
            content.addView(ui.text("Your saved records were not cleared. " +
                "Open the original recovery tools to inspect the issue.",14f,
                color = ui.muted))
            content.addView(spacer(20))
            content.addView(ui.action("Open recovery tools",selected = true) {
                startActivity(Intent(this, MainActivity::class.java))
            })
        }
        scroll.addView(content)
        column.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        column.addView(bottomNavigation(), lp(-1,ui.px(75)))

        val add = ui.iconButton(R.drawable.qp_ic_plus,"Quick Add") { quickAdd() }.apply {
            background = ui.shape(ui.primary,19,ui.primary)
            elevation = ui.px(9).toFloat()
            removeAllViews()
            addView(ui.icon(R.drawable.qp_ic_plus,ui.whiteOnPrimary,null,26))
            contentDescription = "Quick Add"
        }
        frame.addView(add,FrameLayout.LayoutParams(ui.px(57),ui.px(57),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=ui.px(46) })
        setContentView(frame)
    }

    private fun bottomNavigation(): LinearLayout {
        val nav = ui.row().apply {
            setBackgroundColor(ui.surface)
            setPadding(ui.px(9),ui.px(8),ui.px(9),ui.px(7))
        }
        val tabs = listOf(Tab.TODAY,Tab.TRACK,null,Tab.INSIGHTS,Tab.LIBRARY)
        for (entry in tabs) {
            if (entry == null) {
                nav.addView(View(this), LinearLayout.LayoutParams(0,-1,0.85f))
                continue
            }
            val current = entry == tab
            val cell = ui.column().apply {
                gravity = Gravity.CENTER
                minHeight = ui.px(57)
                contentDescription = entry.title + if (current) ", selected" else ""
                isSelected = current
                isFocusable = true
                isClickable = true
                setOnClickListener { tab=entry; render() }
                addView(ui.icon(entry.icon, if (current) ui.primary else ui.muted,null,21))
                addView(spacer(4))
                addView(ui.text(entry.title,11f,if (current) Typeface.BOLD
                    else Typeface.NORMAL,if (current) ui.primary else ui.muted))
            }
            nav.addView(cell,LinearLayout.LayoutParams(0,-1,1f))
        }
        return nav
    }

    private fun header(parent: LinearLayout, title: String, dateLine: Boolean = false) {
        if (dateLine) {
            val date = SimpleDateFormat("EEEE · d MMM", Locale.getDefault())
                .format(Date()).uppercase(Locale.getDefault())
            parent.addView(ui.label(date))
            parent.addView(spacer(5))
        }
        val row = ui.row()
        row.addView(ui.heading(title,28f),LinearLayout.LayoutParams(0,-2,1f))
        row.addView(ui.iconButton(R.drawable.qp_ic_settings,"Settings") {
            management(true)
        },lp(ui.px(48),ui.px(48)))
        parent.addView(row)
        parent.addView(spacer(22))
    }

    private fun greeting(): String {
        return when(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning."
            in 12..16 -> "Good afternoon."
            else -> "Good evening."
        }
    }

    private fun today(parent: LinearLayout) {
        header(parent,greeting(),true)
        val now = System.currentTimeMillis()
        val incoming = store.all().filter { it.status=="PENDING" && !it.paused }
        val future = incoming.filter { it.dueAt>=now }.sortedBy { it.dueAt }
        val overdue = incoming.filter { it.dueAt<now }.sortedByDescending { it.dueAt }
        val first = future.firstOrNull()

        val focus = ui.card(ui.tonal,ui.outline,18)
        val band = ui.row()
        val tag = ui.label("NEXT REMINDER",ui.tonalInk).apply {
            background = ui.shape(ui.secondarySurface,8,ui.secondarySurface)
            setPadding(ui.px(9),ui.px(5),ui.px(9),ui.px(5))
            letterSpacing = .025f
        }
        band.addView(tag,LinearLayout.LayoutParams(0,-2,1f))
        band.addView(ui.text(if(first!=null) time(first.dueAt) else "No upcoming time",
            12f, color=ui.muted))
        focus.addView(band)
        focus.addView(spacer(18))
        focus.addView(ui.heading(first?.title ?: "Your day, in focus.",20f))
        focus.addView(spacer(5))
        focus.addView(ui.text(if(first==null)
            "Create a reminder to start your day." else
            "Scheduled for " + date(first.dueAt) +
                if (first.seriesId!=null) " · Repeating" else " · One-time",
            13f,color=ui.muted))
        focus.addView(spacer(18))
        val actions=ui.row()
        val primary=ui.action(if(first==null) "Add reminder" else "View reminder",
            selected=true) {
            if(first==null) editor() else showReminder(first)
        }
        actions.addView(primary,LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(View(this),lp(ui.px(10),1))
        actions.addView(ui.action("See upcoming",secondary=true) {
            management(false)
        },LinearLayout.LayoutParams(0,-2,1f))
        focus.addView(actions)
        parent.addView(focus)
        parent.addView(spacer(11))

        val health = store.schedulingHealth()
        val stats=ui.row()
        val items=listOf(
            Pair(health.activeFuture.toString(),"Upcoming"),
            Pair(health.overdueUnknown.toString(),"Unresolved"),
            Pair("—","Not enough data")
        )
        for((i,entry) in items.withIndex()){
            val tile=ui.column(8).apply{
                gravity=Gravity.CENTER
                background=ui.shape(ui.surface,14)
                minimumHeight=ui.px(76)
                addView(ui.text(entry.first,22f,Typeface.BOLD,ui.ink))
                addView(spacer(3))
                addView(ui.text(entry.second,11f,Typeface.NORMAL,ui.muted))
            }
            val params=LinearLayout.LayoutParams(0,ui.px(80),1f)
            if(i>0)params.marginStart=ui.px(8)
            stats.addView(tile,params)
        }
        parent.addView(stats)
        parent.addView(spacer(21))

        val nextHeading=ui.row()
        nextHeading.addView(ui.heading("Up next",17f),
            LinearLayout.LayoutParams(0,-2,1f))
        nextHeading.addView(ui.action("View all  →",secondary=true) {
            management(false)
        })
        parent.addView(nextHeading)
        parent.addView(spacer(10))
        val list=ui.card(pad=10)
        if(future.isEmpty()) {
            list.addView(ui.text("No upcoming reminders yet.",14f,color=ui.muted)
                .apply { setPadding(ui.px(8),ui.px(16),0,ui.px(16)) })
        } else {
            future.take(2).forEachIndexed { index,item ->
                list.addView(reminderRow(item))
                if(index<minOf(1,future.size-1)) list.addView(ui.divider())
            }
        }
        parent.addView(list)
        parent.addView(spacer(22))

        val needHeading=ui.row()
        needHeading.addView(ui.heading("Needs your response",17f),
            LinearLayout.LayoutParams(0,-2,1f))
        needHeading.addView(ui.action("History  →",secondary=true) { archive() })
        parent.addView(needHeading)
        parent.addView(spacer(10))
        if(overdue.isEmpty()){
            val empty=ui.card(pad=16)
            empty.addView(ui.text("Nothing awaiting your response.",14f,color=ui.muted))
            parent.addView(empty)
        } else {
            val unresolvedCard=ui.card(pad=8)
            overdue.take(3).forEachIndexed { index,item->
                unresolvedCard.addView(reminderRow(item,true))
                if(index<minOf(2,overdue.size-1)) unresolvedCard.addView(ui.divider())
            }
            parent.addView(unresolvedCard)
        }
        parent.addView(spacer(17))
        parent.addView(ui.text("No response is recorded as Unresolved, not Skipped.",
            11f,color=ui.muted))
    }

    private fun reminderRow(item: ReminderRecord, overdue: Boolean=false): LinearLayout {
        val row=ui.row().apply {
            setPadding(ui.px(4),ui.px(13),ui.px(3),ui.px(13))
            minimumHeight=ui.px(67)
            isClickable=true
            isFocusable=true
            contentDescription=item.title+", "+date(item.dueAt)+
                (if(overdue) ", unresolved" else ", upcoming")
            setOnClickListener { showReminder(item) }
        }
        row.addView(ui.insetIcon(if(item.seriesId!=null)
            R.drawable.qp_ic_book else R.drawable.qp_ic_calendar))
        val middle=ui.column().apply{
            setPadding(ui.px(12),0,ui.px(8),0)
            addView(ui.text(item.title,14f,Typeface.BOLD))
            addView(spacer(4))
            addView(ui.text(time(item.dueAt)+" · "+
                if(overdue) "No response recorded" else
                    (if(item.seriesId==null) "One-time" else "Repeating"),
                12f,color=if(overdue)ui.caution else ui.muted))
        }
        row.addView(middle,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(ui.icon(R.drawable.qp_ic_chevron,ui.muted,null,18))
        return row
    }

    private fun showReminder(item: ReminderRecord) {
        val values=if(item.status=="PENDING" && !item.paused)
            arrayOf("Done", "Snooze 10 minutes", "Skip this occurrence",
                "History", "Edit or manage")
        else arrayOf("History","Edit or manage")
        AlertDialog.Builder(this).setTitle(item.title)
            .setMessage(date(item.dueAt) + if(item.seriesId!=null)
                " · Repeating" else " · One-time")
            .setItems(values) { _,index ->
                when(values[index]){
                    "Done" -> commit(item,"DONE")
                    "Snooze 10 minutes" -> commit(item,"SNOOZE")
                    "Skip this occurrence" -> AlertDialog.Builder(this)
                        .setTitle("Skip this occurrence?")
                        .setMessage("Future repeats stay separate. An unanswered reminder is not a skip.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Skip") { _,_ -> commit(item,"SKIP") }.show()
                    "History" -> {
                        val events=store.recentEvents(item.id)
                        AlertDialog.Builder(this).setTitle("Recorded actions")
                            .setMessage(if(events.isEmpty()) "No recorded actions" else
                                events.joinToString("\n"){it.kind+" · "+date(it.atMs)})
                            .setPositiveButton("Close",null).show()
                    }
                    else -> if(item.seriesId==null && item.status=="PENDING")
                        editor(item.id) else management(false)
                }
            }.setNegativeButton("Close",null).show()
    }

    private fun commit(item: ReminderRecord, action:String) {
        try {
            if(!store.act(item.id,item.revision,action)) {
                Toast.makeText(this,"Reminder changed. Reload and try again.",
                    Toast.LENGTH_SHORT).show()
            } else {
                alarms.flush()
                Toast.makeText(this,when(action){
                    "DONE"->"Marked Done";"SNOOZE"->"Snoozed 10 minutes"
                    else->"Marked Skipped"
                },Toast.LENGTH_SHORT).show()
            }
        }catch(error:Exception){
            Toast.makeText(this,"Could not save action. Existing record preserved.",
                Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun track(parent: LinearLayout){
        header(parent,"Track")
        val box=ui.card()
        box.addView(ui.heading("Activity tracking",18f))
        box.addView(spacer(8))
        box.addView(ui.text("Check-ins, measurements and activity logs are not implemented in this alpha.",
            14f,color=ui.muted))
        parent.addView(box)
    }

    private fun insights(parent: LinearLayout){
        header(parent,"Insights")
        val box=ui.card()
        box.addView(ui.heading("Recorded reminder outcomes",17f))
        box.addView(spacer(15))
        val (unresolved,done,skipped)=store.counts()
        for((name,value) in listOf("Confirmed Done" to done,
            "Explicitly Skipped" to skipped,"Unresolved" to unresolved)){
            val row=ui.row()
            row.addView(ui.text(name,14f,color=ui.muted),weight())
            row.addView(ui.heading(value.toString(),18f))
            box.addView(row)
            box.addView(spacer(10))
        }
        box.addView(ui.text("All-time non-archived occurrence counts; not 7/28/90-day analytics.",
            12f,color=ui.muted))
        parent.addView(box)
        parent.addView(spacer(16))
        parent.addView(ui.text("Evidence-backed trends and weekly reviews are not implemented yet.",
            13f,color=ui.muted))
    }

    private fun library(parent: LinearLayout){
        header(parent,"Library")
        parent.addView(ui.text("Reminders and repeating schedules",14f,color=ui.muted))
        parent.addView(spacer(18))
        val rules=store.rules()
        if(rules.isNotEmpty()){
            parent.addView(ui.heading("Repeating schedules",17f))
            parent.addView(spacer(10))
            val card=ui.card(pad=12)
            rules.take(4).forEachIndexed { index,r ->
                val row=ui.row().apply {
                    setPadding(ui.px(4),ui.px(10),ui.px(4),ui.px(10))
                    isClickable=true
                    isFocusable=true
                    contentDescription="Manage "+r.title
                    setOnClickListener { management(false) }
                }
                row.addView(ui.insetIcon(R.drawable.qp_ic_calendar))
                val text=ui.column().apply{
                    setPadding(ui.px(12),0,0,0)
                    addView(ui.text(r.title,14f,Typeface.BOLD))
                    addView(ui.text(if(r.paused)"Paused" else "Active",
                        12f,color=ui.muted))
                }
                row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(ui.icon(R.drawable.qp_ic_chevron,ui.muted))
                card.addView(row)
                if(index < minOf(3,rules.size-1)) card.addView(ui.divider())
            }
            parent.addView(card)
            parent.addView(spacer(14))
        }
        val history=ui.card()
        history.addView(ui.heading("History & Archive",17f))
        history.addView(spacer(8))
        history.addView(ui.text("Search previous reminders, recorded responses and archived schedules.",
            13f,color=ui.muted))
        history.addView(spacer(12))
        history.addView(ui.action("Browse history",secondary=true){archive()})
        parent.addView(history)
        parent.addView(spacer(12))
        parent.addView(ui.action("Manage reminders",selected=true){management(false)})
        parent.addView(spacer(10))
        parent.addView(ui.action("New reminder",secondary=true){editor()})
    }
}
