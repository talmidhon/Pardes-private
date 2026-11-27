package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import io.github.kdroidfilter.seforimapp.core.study.GoalChange
import io.github.kdroidfilter.seforimapp.core.study.PausePeriod
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import java.time.LocalDate
import java.time.LocalDateTime

// Pardes (the daily portion's upgrade): the days of a plan and how far into its content each one reaches, with the goal
// it had that day. A day's part used to be reckoned from the plan's present goal, so changing it moved every day before;
// here each day counts by the goal in force on it, and a paused day, or a part of several days paused at its start,
// does not move the plan on.

/** One day of a plan: whether it is a day of study, whether the plan was paused, its goal, and where its part starts. */
internal data class CalendarDay(
    val day: LocalDate,
    val studyDay: Boolean,
    val paused: Boolean,
    val goal: Int,
    val unitsBefore: Long,
    val partsBefore: Int,
)

/**
 * What a plan's days are reckoned from: its [firstDay], the day it was ended on ([lastDay], null while it goes on), its goal
 * at the start and the changes after it, the days it was paused, and [studyDay], whether a day is one of its days of study
 * (its weekdays, not Yom Tov while it skips Shabbat).
 */
internal class PlanCalendar(
    val firstDay: LocalDate,
    private val lastDay: LocalDate?,
    private val startMinute: Int,
    private val periodDays: Int,
    private val startGoal: Int,
    goalChanges: List<GoalChange>,
    private val pauses: List<PausePeriod>,
    private val studyDay: (LocalDate) -> Boolean,
) {
    private val goals = goalChanges.sortedBy { it.from }

    /** The units a part is of on [day]: the goal in force then. */
    fun goalOn(day: LocalDate): Int = (goals.lastOrNull { it.from <= day }?.perDay ?: startGoal).coerceAtLeast(1)

    /** Whether the plan was paused on [day]. */
    fun pausedOn(day: LocalDate): Boolean = pauses.any { day in it }

    /** Whether the plan had started by [now]: on its first day, from the minute it starts. */
    fun startedBy(now: LocalDateTime): Boolean =
        now.toLocalDate() > firstDay || now.toLocalDate() == firstDay && now.hour * MINUTES_IN_HOUR + now.minute >= startMinute

    /** Whether [day] is a day the plan is learnt on: it has begun, it is not over, it is not paused, and it is a day of study. */
    fun isStudyDay(day: LocalDate): Boolean = !pausedOn(day) && isUnpausedStudyDay(day)

    // A day of study but for a pause
    private fun isUnpausedStudyDay(day: LocalDate): Boolean =
        day >= firstDay && (lastDay == null || day <= lastDay) && (periodDays > 1 || studyDay(day))

    /**
     * The days from the first through [until], each with where its part starts: the units that went by before it, each part
     * counted by the goal in force when it began (a part a day on each study day; a part of several days not paused at its
     * start, its first day), so that a goal changed later leaves the days before as they were, and a pause does not move
     * the plan on. One pass, the Yom Tov of a day asked once.
     */
    fun days(until: LocalDate): List<CalendarDay> {
        val rows = ArrayList<CalendarDay>()
        var units = 0L
        var parts = 0
        var slot: LocalDate? = null
        var day = firstDay
        while (day <= until) {
            if (periodDays > 1) {
                val start = periodStart(firstDay, day, periodDays)
                // Entering a part: the one before it went by, unless it began paused
                if (slot != null && start != slot) {
                    val began = maxOf(slot, firstDay)
                    if (!pausedOn(began)) {
                        units += goalOn(began)
                        parts++
                    }
                }
                slot = start
            }
            val paused = pausedOn(day)
            val study = !paused && isUnpausedStudyDay(day)
            rows += CalendarDay(day, study, paused, goalOn(day), units, parts)
            // A part a day goes by once its day has
            if (periodDays <= 1 && study) {
                units += goalOn(day)
                parts++
            }
            day = day.plusDays(1)
        }
        return rows
    }

    /** The units that went by before [day]: where the day's part starts; 0 before the plan's first day. */
    fun unitsBefore(day: LocalDate): Long = days(day).lastOrNull()?.unitsBefore ?: 0L

    /** The parts that went by before [day]: the day's is the one after them. */
    fun partsBefore(day: LocalDate): Int = days(day).lastOrNull()?.partsBefore ?: 0
}

/** [this] plan's calendar, its days of study in Israel or abroad ([inIsrael]). */
internal fun StudyPlan.calendar(inIsrael: Boolean): PlanCalendar =
    PlanCalendar(
        firstDay = firstDay,
        lastDay = finished,
        startMinute = startMinute,
        periodDays = periodDays,
        startGoal = startGoal,
        goalChanges = goalChanges,
        pauses = pauses,
        studyDay = { isStudyDay(it, weekdays, inIsrael) },
    )

/**
 * [units] cut to the plan's range: from the unit it starts at ([start], found as [startIndexOf] does) through the one
 * whose headings are [end], which is where it ends, and the index it starts at in what is left. A plan with no [end]
 * goes on to the content's end, whole, as before.
 */
internal fun planRange(
    units: List<StudyUnit>,
    start: List<String>,
    end: List<String>,
): Pair<List<StudyUnit>, Int> {
    val from = startIndexOf(units, start)
    if (end.isEmpty() || units.isEmpty()) return units to from
    val to =
        (units.indexOfFirst { it.headings == end }.takeIf { it >= from }
            ?: units.indexOfLast { it.headings.lastOrNull() == end.lastOrNull() }.takeIf { it >= from }
            ?: units.lastIndex).coerceAtLeast(from)
    return units.subList(from, to + 1) to 0
}

/** Whether the start of a plan can still be put off by a day: it has not begun and was not put off before. */
internal fun canPostpone(
    plan: StudyPlan,
    now: LocalDateTime,
    inIsrael: Boolean,
): Boolean = plan.postponedDays == 0 && !plan.calendar(inIsrael).startedBy(now)

private const val MINUTES_IN_HOUR = 60
