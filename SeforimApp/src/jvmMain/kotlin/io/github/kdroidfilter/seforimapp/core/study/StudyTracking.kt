package io.github.kdroidfilter.seforimapp.core.study

import java.time.LocalDate

// Pardes: how a study plan's days are reported and counted. Plain Kotlin on java.time alone, no Compose and no DB, so the
// logic can be taken out of Pardes: the store (StudyPlanStore.kt) keeps the reports, the Home's widget shows what is
// worked out here.

/** What the user reports for a day of a plan. */
enum class DayReport(
    val code: Long,
) {
    /** The day's goal was reached. */
    DONE(1),

    /** Some of it was. */
    PARTIAL(2),

    /** None of it was. */
    NOT_DONE(3),
    ;

    companion object {
        /** The report kept as [code], null for a code this version does not know. */
        fun of(code: Long): DayReport? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Which days the user reports, and so what a day without a report means. Switching from one to another loses nothing:
 * the reports kept are the same, only the reading of a missing one changes.
 */
enum class TrackingMode(
    val code: Long,
) {
    /** Only the days the goal was reached are reported; a day passed with no report was not. */
    POSITIVE(0),

    /** Only the days it was not reached are reported; a day passed with no report was. */
    NEGATIVE(1),

    /** Every day is reported; a day passed with no report is counted as neither. */
    FULL(2),
    ;

    companion object {
        /** The mode kept as [code]: [POSITIVE], what a plan had before it could be chosen, for a code not known. */
        fun of(code: Long): TrackingMode = entries.firstOrNull { it.code == code } ?: POSITIVE
    }
}

/** Where a plan stands: learning, paused by the user, or over. */
enum class PlanState { ACTIVE, PAUSED, FINISHED }

/** From [from] on, [perDay] units a part: a change of a plan's goal, kept after the goals before it, whose days stay as they were. */
data class GoalChange(
    val from: LocalDate,
    val perDay: Int,
)

/** Days a plan was paused, from [from] up to [until] left out; while [until] is null, it still is. */
data class PausePeriod(
    val from: LocalDate,
    val until: LocalDate? = null,
) {
    /** Whether [day] is within the pause. */
    operator fun contains(day: LocalDate): Boolean = day >= from && (until == null || day < until)
}

/** What a day of a plan was, for the history and the statistics. */
enum class DayOutcome {
    /** The goal was reached. */
    DONE,

    /** Some of it was. */
    PARTIAL,

    /** It was not. */
    MISSED,

    /** A day of [TrackingMode.FULL] passed with no report. */
    UNREPORTED,

    /** Today, with no report yet: not counted either way. */
    PENDING,

    /** Not a day of study: a weekday the plan skips, Yom Tov, or a day after the plan's end. */
    REST,

    /** A day the plan was paused. */
    PAUSED,

    /** Before the plan's first day, or in the future. */
    NOT_YET,
    ;

    /** Whether the day counts in the statistics, for or against the goal. */
    val counted: Boolean get() = this == DONE || this == PARTIAL || this == MISSED || this == UNREPORTED
}

/**
 * What [day] was, seen on [today]: by the [report] the user made for it or, with none, by [mode]. A day that is not a
 * day of study ([studyDay]), a [paused] one, one before the plan [started] and the future are not counted; neither is
 * today without a report, which may still come.
 */
fun dayOutcome(
    day: LocalDate,
    today: LocalDate,
    mode: TrackingMode,
    report: DayReport?,
    studyDay: Boolean,
    paused: Boolean,
    started: Boolean,
): DayOutcome =
    when {
        !started || day > today -> DayOutcome.NOT_YET
        paused -> DayOutcome.PAUSED
        !studyDay -> DayOutcome.REST
        report == DayReport.DONE -> DayOutcome.DONE
        report == DayReport.PARTIAL -> DayOutcome.PARTIAL
        report == DayReport.NOT_DONE -> DayOutcome.MISSED
        day == today -> DayOutcome.PENDING
        mode == TrackingMode.NEGATIVE -> DayOutcome.DONE
        mode == TrackingMode.POSITIVE -> DayOutcome.MISSED
        else -> DayOutcome.UNREPORTED
    }

/** What the days of a plan add up to: each kind of day, the share of the goal kept, and the runs of days it was reached. */
data class StudyStats(
    val done: Int = 0,
    val partial: Int = 0,
    val missed: Int = 0,
    val unreported: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
) {
    /** The share of the reported days the goal was reached on, 0 to 100; null while there is none. */
    val ratePercent: Int?
        get() = (done + partial + missed).takeIf { it > 0 }?.let { (done * 100.0 / it).toInt() }
}

/**
 * The statistics of [outcomes], the days of a plan in order, the latest last. A run of days reached goes on over days
 * that were not days of study, paused ones and today's, which has no report yet; a day partly done, missed or unreported
 * ends it.
 */
fun studyStats(outcomes: List<DayOutcome>): StudyStats {
    var run = 0
    var best = 0
    outcomes.forEach { outcome ->
        when (outcome) {
            DayOutcome.DONE -> best = maxOf(best, ++run)
            DayOutcome.PARTIAL, DayOutcome.MISSED, DayOutcome.UNREPORTED -> run = 0
            else -> Unit
        }
    }
    return StudyStats(
        done = outcomes.count { it == DayOutcome.DONE },
        partial = outcomes.count { it == DayOutcome.PARTIAL },
        missed = outcomes.count { it == DayOutcome.MISSED },
        unreported = outcomes.count { it == DayOutcome.UNREPORTED },
        currentStreak = run,
        bestStreak = best,
    )
}
