package io.github.kdroidfilter.seforimapp.core.study

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a plan's days are reported and counted, in each of the three tracking modes. */
class StudyTrackingTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val past = today.minusDays(3)

    private fun outcome(
        mode: TrackingMode,
        report: DayReport?,
        day: LocalDate = past,
        studyDay: Boolean = true,
        paused: Boolean = false,
        started: Boolean = true,
    ) = dayOutcome(day, today, mode, report, studyDay, paused, started)

    @Test
    fun `positive tracking counts a day without a report as missed`() {
        assertEquals(DayOutcome.MISSED, outcome(TrackingMode.POSITIVE, null))
        assertEquals(DayOutcome.DONE, outcome(TrackingMode.POSITIVE, DayReport.DONE))
    }

    @Test
    fun `negative tracking counts a day without a report as done`() {
        assertEquals(DayOutcome.DONE, outcome(TrackingMode.NEGATIVE, null))
        assertEquals(DayOutcome.MISSED, outcome(TrackingMode.NEGATIVE, DayReport.NOT_DONE))
    }

    @Test
    fun `full tracking counts a day without a report as neither`() {
        assertEquals(DayOutcome.UNREPORTED, outcome(TrackingMode.FULL, null))
        assertFalse(DayOutcome.UNREPORTED == DayOutcome.MISSED)
    }

    @Test
    fun `a partly done day is partial in every mode`() {
        TrackingMode.entries.forEach { assertEquals(DayOutcome.PARTIAL, outcome(it, DayReport.PARTIAL)) }
    }

    @Test
    fun `today without a report, the future, rest days, pauses and days before the start are not counted`() {
        TrackingMode.entries.forEach { mode ->
            assertEquals(DayOutcome.PENDING, outcome(mode, null, day = today))
            assertEquals(DayOutcome.NOT_YET, outcome(mode, null, day = today.plusDays(1)))
            assertEquals(DayOutcome.REST, outcome(mode, null, studyDay = false))
            assertEquals(DayOutcome.PAUSED, outcome(mode, null, paused = true))
            assertEquals(DayOutcome.NOT_YET, outcome(mode, null, started = false))
        }
        assertTrue(listOf(DayOutcome.PENDING, DayOutcome.NOT_YET, DayOutcome.REST, DayOutcome.PAUSED).none { it.counted })
    }

    @Test
    fun `the same reports are read by each mode, so switching mode loses none`() {
        val reports = listOf(DayReport.DONE, null, DayReport.NOT_DONE, DayReport.PARTIAL)
        val kept = reports.map { outcome(TrackingMode.FULL, it) }
        // The reported days read alike under every mode; only the days without a report change
        reports.forEachIndexed { at, report ->
            if (report != null) TrackingMode.entries.forEach { assertEquals(kept[at], outcome(it, report)) }
        }
    }

    @Test
    fun `statistics count each kind, the share of the goal kept, and the streaks`() {
        val outcomes =
            listOf(
                DayOutcome.DONE,
                DayOutcome.DONE,
                DayOutcome.REST,
                DayOutcome.DONE,
                DayOutcome.MISSED,
                DayOutcome.PAUSED,
                DayOutcome.DONE,
                DayOutcome.DONE,
                DayOutcome.PENDING,
            )
        val stats = studyStats(outcomes)
        assertEquals(5, stats.done)
        assertEquals(1, stats.missed)
        // 5 of 6 counted days
        assertEquals(83, stats.ratePercent)
        // Rest, pause and today's pending day do not end a run
        assertEquals(3, stats.bestStreak)
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun `a partly done or unreported day ends a run and a plan without counted days has no share`() {
        assertEquals(0, studyStats(listOf(DayOutcome.DONE, DayOutcome.PARTIAL)).currentStreak)
        assertEquals(0, studyStats(listOf(DayOutcome.DONE, DayOutcome.UNREPORTED)).currentStreak)
        assertEquals(1, studyStats(listOf(DayOutcome.DONE, DayOutcome.UNREPORTED)).bestStreak)
        assertNull(studyStats(listOf(DayOutcome.REST, DayOutcome.NOT_YET, DayOutcome.PENDING)).ratePercent)
        assertNull(studyStats(emptyList()).ratePercent)
    }

    @Test
    fun `a pause is a period from a day up to another left out, open while it has no end`() {
        val pause = PausePeriod(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13))
        assertTrue(LocalDate.of(2026, 11, 10) in pause)
        assertTrue(LocalDate.of(2026, 11, 12) in pause)
        assertFalse(LocalDate.of(2026, 11, 13) in pause)
        assertTrue(LocalDate.of(2030, 1, 1) in PausePeriod(LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun `a code the version does not know reads as the default`() {
        assertEquals(TrackingMode.POSITIVE, TrackingMode.of(99))
        assertEquals(TrackingMode.FULL, TrackingMode.of(2))
        assertNull(DayReport.of(99))
        assertEquals(DayReport.PARTIAL, DayReport.of(2))
    }
}
