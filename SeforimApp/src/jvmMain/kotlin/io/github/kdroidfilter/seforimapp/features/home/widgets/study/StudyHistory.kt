package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.study.DayOutcome
import io.github.kdroidfilter.seforimapp.core.study.DayReport
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.core.study.StudyStats
import io.github.kdroidfilter.seforimapp.core.study.TrackingMode
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import java.time.LocalDate

// Pardes (the daily portion's upgrade): a plan's history, day by day, with what was reported for each day, which can be
// corrected, and the statistics the days add up to. In the window the editor's is (EditorWindow).

/** [plan]'s history in a window of its own, [onClose] closing it. The plan is read again as it is now, as it changes. */
@Composable
internal fun StudyHistoryWindow(
    plan: StudyPlan,
    state: HomeWidgetsState,
    onClose: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val store = graph.studyPlanStore
    val revision by store.revision.collectAsState()
    val inIsrael = state.inIsrael
    val shown by produceState<ShownPlan?>(null, revision, plan.id, inIsrael) {
        value =
            runSuspendCatching {
                store.plans().firstOrNull { it.id == plan.id }?.let {
                    graph.repository.shownPlan(it, LocalDate.now(), inIsrael, store.reportsOf(it.id))
                }
            }.getOrNull()
    }
    val scope = rememberCoroutineScope()
    EditorWindow(onClose, title = "היסטוריית ביצוע") {
        val current = shown
        if (current == null) {
            Text("אין עוד מה להציג", color = JewelTheme.globalColors.text.info)
            return@EditorWindow
        }
        Text(current.plan.name.ifEmpty { current.plan.bookTitle }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(trackingLabel(current.plan.tracking), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
        Spacer(Modifier.height(8.dp))
        current.stats?.let { StatsBox(it, current) }
        Spacer(Modifier.height(8.dp))
        Divider(orientation = Orientation.Horizontal)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(historyRows(current), key = { it.day.toEpochDay() }) { row ->
                HistoryRow(current, row) { report ->
                    scope.launch { store.setReport(current.plan.id, markDay(current.plan, row.day), report) }
                }
            }
        }
    }
}

/** The days the history lists, the latest first: those the plan counted or paused, a part of several days once, on its first day. */
internal fun historyRows(plan: ShownPlan): List<HistoryDay> {
    val period = plan.plan.periodDays
    return plan.history
        .filter { it.outcome != DayOutcome.NOT_YET }
        .filter { period <= 1 || it.day == maxOf(periodStart(plan.plan.firstDay, it.day, period), plan.plan.firstDay) }
        .asReversed()
}

/** How the days are reported, in a few words. */
internal fun trackingLabel(mode: TrackingMode): String =
    when (mode) {
        TrackingMode.POSITIVE -> "שיטת מעקב: מסמנים רק ימים שהושלמו"
        TrackingMode.NEGATIVE -> "שיטת מעקב: מסמנים רק ימים שלא הושלמו"
        TrackingMode.FULL -> "שיטת מעקב: מדווחים בכל יום"
    }

/** The statistics: the days by outcome, the share of the goal kept, the runs, and how far into its content the plan is. */
@Composable
private fun StatsBox(
    stats: StudyStats,
    plan: ShownPlan,
) {
    val info = JewelTheme.globalColors.text.info
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val unreported = if (stats.unreported > 0) " · לא דווחו ${stats.unreported}" else ""
        Text("הושלמו ${stats.done} · חלקית ${stats.partial} · לא הושלמו ${stats.missed}$unreported")
        Text("עמידה ביעד: ${stats.ratePercent?.let { "$it%" } ?: "אין עוד נתונים"}")
        Text("רצף נוכחי: ${stats.currentStreak} · שיא הרצף: ${stats.bestStreak}")
        val progress = (plan.part as? DayPart.Part)?.progress ?: (plan.held?.progress)
        if (progress != null) Text("התקדמות בתוכן: ${(progress * 100).toInt()}%", fontSize = 12.sp, color = info)
    }
}

/** One day: its date, its part, what it was, and the buttons to report it again. */
@Composable
private fun HistoryRow(
    plan: ShownPlan,
    row: HistoryDay,
    onReport: (DayReport?) -> Unit,
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    val info = JewelTheme.globalColors.text.info
    val reportable = row.outcome != DayOutcome.REST && row.outcome != DayOutcome.PAUSED
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val date = "${weekdayLetter(row.day.dayOfWeek)} ${row.day.dayOfMonth}.${row.day.monthValue}.${row.day.year}"
            Text(date, fontWeight = FontWeight.Medium)
            partOfRow(plan, row)?.let { Text(it, fontSize = 13.sp, maxLines = 1) }
            val goal = if (plan.plan.goalChanges.isNotEmpty()) " · יעד ${row.calendar.goal}" else ""
            Text(outcomeLabel(row.outcome) + goal, fontSize = 12.sp, color = info)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp))
            listOf("✓" to DayReport.DONE, "½" to DayReport.PARTIAL, "✗" to DayReport.NOT_DONE).forEach { (label, report) ->
                ReportChip(label, row.report == report, accent, reportable, "study-history-${row.day}-${report.code}") {
                    onReport(if (row.report == report) null else report)
                }
            }
        }
    }
}

// The part of the day by headings, for a day that had one
private fun partOfRow(
    plan: ShownPlan,
    row: HistoryDay,
): String? {
    val day = row.calendar
    val part = dayPartFrom(plan.startIndex, plan.units.size, day.goal, plan.plan.restart, day.unitsBefore, day.partsBefore + 1, true)
    return (part as? DayPart.Part)?.let { partLabel(it.indexes.map { index -> plan.units[index] }) }?.takeIf { row.calendar.studyDay }
}

/** What a day was, in a word. */
internal fun outcomeLabel(outcome: DayOutcome): String =
    when (outcome) {
        DayOutcome.DONE -> "הושלם"
        DayOutcome.PARTIAL -> "הושלם חלקית"
        DayOutcome.MISSED -> "לא הושלם"
        DayOutcome.UNREPORTED -> "לא דווח"
        DayOutcome.PENDING -> "עוד אפשר לדווח"
        DayOutcome.REST -> "אין לימוד"
        DayOutcome.PAUSED -> "התוכנית הייתה מושהית"
        DayOutcome.NOT_YET -> ""
    }
