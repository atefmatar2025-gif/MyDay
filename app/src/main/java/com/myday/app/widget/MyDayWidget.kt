package com.myday.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.myday.app.MainActivity
import com.myday.app.R
import com.myday.app.container
import com.myday.app.data.TaskAction
import com.myday.app.data.action
import com.myday.app.data.isRecurring
import com.myday.app.prayer.Prayer
import com.myday.app.prayer.PrayerStore
import com.myday.app.ui.TaskQueries
import com.myday.app.util.Fmt
import com.myday.app.util.TaskTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------- البيانات ----------------

private data class WidgetItem(
    val id: Long,
    val title: String,
    val time: String,
    val extra: String?,
    val done: Boolean,
    val overdue: Boolean,
    /** يمكن إتمامها من الودجت (الموعد الحالي للمهمة) */
    val canComplete: Boolean,
)

private data class WidgetData(
    val dayTitle: String,
    val subtitle: String,
    val done: Int,
    val total: Int,
    val overdueOtherDays: Int,
    val next: WidgetItem?,
    val items: List<WidgetItem>,
)

private suspend fun loadData(context: Context): WidgetData {
    val c = context.container
    val (tasks, _, completions) = c.repository.snapshot()
    val use24h = c.settings.current().use24h
    val now = System.currentTimeMillis()
    val today = LocalDate.now()

    val entries = TaskQueries.entriesForDay(today, tasks, completions)
    val items = entries.map { e ->
        val t = e.task
        val extra = TaskTime.anchorLabel(t) ?: t.action?.let { a ->
            val who = t.actionContactName ?: t.actionPhone.orEmpty()
            when (a) {
                TaskAction.SMS -> "رسالة لـ $who"
                TaskAction.WHATSAPP -> "واتساب لـ $who"
                TaskAction.CALL -> "اتصال بـ $who"
            }
        }
        WidgetItem(
            id = t.id, title = t.title, time = Fmt.time(e.time, use24h), extra = extra,
            done = e.done, overdue = !e.done && e.time < now,
            canComplete = !e.done && (!t.isRecurring || e.time == t.dueAt),
        )
    }
    val pending = entries.zip(items).filter { !it.second.done }
    val next = pending.firstOrNull { it.first.time >= now }?.second ?: pending.firstOrNull()?.second
    val overdueOther = tasks.count { TaskQueries.isOverdue(it, now) && Fmt.toDate(it.dueAt) != today }

    // العنوان الفرعي: الصلاة القادمة إن حُدِّدت المدينة، وإلا التاريخ الهجري
    val subtitle = PrayerStore.timesFor(today)?.let { times ->
        val p = Prayer.entries.filter { it != Prayer.SUNRISE }.firstOrNull { (times[it] ?: 0L) > now }
        if (p != null) "${p.label} ${Fmt.time(times[p]!!, use24h)}" else null
    } ?: Fmt.hijri(today)

    // المعلقة أولاً ثم المنجزة
    val ordered = items.filter { !it.done } + items.filter { it.done }
    return WidgetData(
        dayTitle = "${Fmt.dayName(today)} ${Fmt.dayMonth(today)}",
        subtitle = subtitle,
        done = items.count { it.done },
        total = items.size,
        overdueOtherDays = overdueOther,
        next = next,
        items = ordered,
    )
}

// ---------------- الألوان ----------------

private object WColors {
    val bg = ColorProvider(day = Color(0xFFF4FBF9), night = Color(0xFF161D1C))
    val card = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF252B2A))
    val text = ColorProvider(day = Color(0xFF161D1C), night = Color(0xFFDDE4E2))
    val muted = ColorProvider(day = Color(0xFF5B6664), night = Color(0xFFA8B3B0))
    val primary = ColorProvider(day = Color(0xFF0F766E), night = Color(0xFF7DD8CB))
    val onPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF003731))
    val track = ColorProvider(day = Color(0xFFB7F0E6), night = Color(0xFF005048))
    val error = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))
    val nextBg = ColorProvider(day = Color(0xFFE0F5F1), night = Color(0xFF1F3330))
}

// ---------------- الودجت ----------------

class MyDayWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 120.dp)
        private val LARGE = DpSize(250.dp, 260.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try { loadData(context) } catch (e: Exception) { null }
        provideContent { WidgetContent(context, data) }
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData?) {
    val size = LocalSize.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).setData(Uri.parse("myday://widget/open")))
    val quickAdd = actionStartActivity(MainActivity.quickAddIntent(context))

    Column(
        GlanceModifier.fillMaxSize().background(WColors.bg).cornerRadius(22.dp).padding(12.dp)
    ) {
        if (data == null) {
            Text("افتح التطبيق مرة واحدة", style = TextStyle(color = WColors.text), modifier = GlanceModifier.clickable(openApp))
            return@Column
        }
        // الرأس: اليوم + الصلاة القادمة + زر الإضافة الصوتية
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(openApp)) {
                Text(data.dayTitle, style = TextStyle(color = WColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(data.subtitle, style = TextStyle(color = WColors.primary, fontSize = 12.sp), maxLines = 1)
            }
            Box(
                GlanceModifier.size(36.dp).background(WColors.primary).cornerRadius(18.dp).clickable(quickAdd),
                contentAlignment = Alignment.Center
            ) {
                Image(ImageProvider(R.drawable.ic_w_mic), "أضف بصوتك", GlanceModifier.size(20.dp))
            }
        }
        Spacer(GlanceModifier.height(8.dp))

        // التقدّم
        val progress = if (data.total == 0) 0f else data.done.toFloat() / data.total
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (data.total == 0) "لا مهام اليوم" else "أنجزت ${data.done} من ${data.total}",
                style = TextStyle(color = WColors.muted, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight()
            )
            if (data.overdueOtherDays > 0) {
                Text("${data.overdueOtherDays} متأخرة", style = TextStyle(color = WColors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = WColors.primary,
            backgroundColor = WColors.track,
        )
        Spacer(GlanceModifier.height(8.dp))

        val small = size.height < 180.dp || size.width < 200.dp
        if (data.items.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(quickAdd), contentAlignment = Alignment.Center) {
                Text("يومك فارغ ✨ اضغط 🎤 لإضافة مهمة", style = TextStyle(color = WColors.muted, fontSize = 13.sp))
            }
        } else if (small) {
            // الحجم الصغير: المهمة التالية فقط
            data.next?.let { NextCard(context, it) } ?: Text("أنجزت كل مهام اليوم 🎉", style = TextStyle(color = WColors.primary, fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(data.items, itemId = { it.id * 31 + it.time.hashCode() }) { item ->
                    Column {
                        TaskRow(context, item, highlight = item == data.next)
                        Spacer(GlanceModifier.height(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NextCard(context: Context, item: WidgetItem) {
    Column(
        GlanceModifier.fillMaxWidth().background(WColors.nextBg).cornerRadius(14.dp).padding(10.dp)
            .clickable(actionStartActivity(MainActivity.openTaskIntent(context, item.id)))
    ) {
        Text("التالي • ${item.time}", style = TextStyle(color = if (item.overdue) WColors.error else WColors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Text(item.title, style = TextStyle(color = WColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 2)
    }
}

@Composable
private fun TaskRow(context: Context, item: WidgetItem, highlight: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().background(if (highlight) WColors.nextBg else WColors.card).cornerRadius(14.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val check = if (item.done) R.drawable.ic_w_check else R.drawable.ic_w_circle
        val checkMod = GlanceModifier.size(26.dp).let {
            if (item.canComplete) it.clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to item.id))) else it
        }
        Image(ImageProvider(check), if (item.done) "منجزة" else "تحديد كمنجزة", checkMod)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.openTaskIntent(context, item.id)))) {
            Text(
                item.title,
                style = TextStyle(
                    color = if (item.done) WColors.muted else WColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                ),
                maxLines = 1
            )
            val meta = listOfNotNull(item.time, item.extra).joinToString(" • ")
            Text(meta, style = TextStyle(color = if (item.overdue) WColors.error else WColors.muted, fontSize = 11.sp), maxLines = 1)
        }
    }
}

// ---------------- الإتمام بلمسة ----------------

val TaskIdKey = ActionParameters.Key<Long>("task_id")

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        context.container.repository.complete(id)
        MyDayWidget().updateAll(context)
    }
}

class MyDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyDayWidget()
}

/** تحديث الودجت بعد أي تغيير في المهام (مع تجميع التغييرات المتتالية). */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Synchronized
    fun request(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            delay(600)
            try { MyDayWidget().updateAll(app) } catch (e: Exception) { }
        }
    }
}
