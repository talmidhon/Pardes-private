package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import io.github.kdroidfilter.seforimapp.core.study.DayOutcome
import io.github.kdroidfilter.seforimapp.core.study.DayReport
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.core.study.StudyStats
import io.github.kdroidfilter.seforimapp.core.study.dayOutcome
import io.github.kdroidfilter.seforimapp.core.study.studyStats
import java.time.LocalDate
import java.time.LocalDateTime

// Pardes (the daily portion's upgrade): what a plan's days were, for the history and the statistics. The day's part is
// still reckoned from the date; what was reported for it is the store's.

/** A day of a plan as the history shows it: its [calendar] day, what the user [reported], and what it [outcome] was. */
internal data class HistoryDay(
    val calendar: CalendarDay,
    val report: DayReport?,
    val outcome: DayOutcome,
) {
    val day: LocalDate get() = calendar.day
}

/**
 * Every day of [plan] from its first through [now]'s, in order, each with its outcome: by what was reported, else by the
 * plan's tracking mode ([dayOutcome]). With [reports] by day, and [count] units from [startIndex] studied (as
 * [dayPartFrom] takes them), a day after the content's end, when the plan does not start again, is not a day of study.
 * Days past the first are not counted before the plan's start minute is reached.
 */
internal fun historyOf(
    plan: StudyPlan,
    calendar: PlanCalendar,
    reports: Map<LocalDate, DayReport>,
    now: LocalDateTime,
    count: Int,
    startIndex: Int,
): List<HistoryDay> {
    val today = now.toLocalDate()
    val period = plan.periodDays
    return calendar.days(today).map { row ->
        val part = dayPartFrom(startIndex, count, row.goal, plan.restart, row.unitsBefore, row.partsBefore + 1, studyDay = true)
        val hasPart = row.studyDay && part is DayPart.Part
        val started = row.day < today || row.day != plan.firstDay || calendar.startedBy(now)
        // A part of several days is reported on its first day, and counts once, on the first day the plan has in it
        val slot = periodStart(plan.firstDay, row.day, period)
        val report = reports[slot]
        val outcome =
            when {
                period <= 1 -> dayOutcome(row.day, today, plan.tracking, report, hasPart, row.paused, started)
                row.day != maxOf(slot, plan.firstDay) -> if (row.paused) DayOutcome.PAUSED else DayOutcome.REST
                // Its period still running, it may yet be reported
                report == null && !row.paused && hasPart && slot.plusDays(period.toLong()) > today -> DayOutcome.PENDING
                else -> dayOutcome(row.day, row.day.plusDays(1), plan.tracking, report, hasPart, row.paused, started)
            }
        HistoryDay(row, report, outcome)
    }
}

/** The statistics of [history]: the days by outcome, the share of the goal kept, the streaks. */
internal fun statsOf(history: List<HistoryDay>): StudyStats = studyStats(history.map { it.outcome })
