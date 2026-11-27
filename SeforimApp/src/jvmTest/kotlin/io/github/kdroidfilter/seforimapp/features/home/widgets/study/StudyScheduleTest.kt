package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import io.github.kdroidfilter.seforimapp.core.study.DayOutcome
import io.github.kdroidfilter.seforimapp.core.study.DayReport
import io.github.kdroidfilter.seforimapp.core.study.GoalChange
import io.github.kdroidfilter.seforimapp.core.study.PausePeriod
import io.github.kdroidfilter.seforimapp.core.study.PlanState
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.core.study.PlanBookRef
import io.github.kdroidfilter.seforimapp.core.study.TrackingMode
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A plan's days by the goal each had, its pauses and its start put off, its end, and its history. */
class StudyScheduleTest {
    private val first = LocalDate.of(2026, 11, 1)

    private fun plan(
        perDay: Int = 2,
        weekdays: Set<java.time.DayOfWeek> = java.time.DayOfWeek.entries.toSet(),
        periodDays: Int = 1,
        restart: Boolean = false,
        change: StudyPlan.() -> StudyPlan = { this },
    ) = StudyPlan(
        bookId = 1,
        bookTitle = "משנה",
        level = 1,
        start = emptyList(),
        perDay = perDay,
        startDay = first,
        weekdays = weekdays,
        restart = restart,
        periodDays = periodDays,
    ).change()

    private fun calendar(plan: StudyPlan) = plan.calendar(inIsrael = true)

    private fun units(count: Int) = (1..count).map { StudyUnit(it.toLong(), 1, listOf("פרק $it"), it * 10L) }

    private fun part(
        plan: StudyPlan,
        day: LocalDate,
        count: Int = 100,
    ): DayPart {
        val calendar = calendar(plan)
        val before = day < calendar.firstDay
        return dayPartFrom(
            startIndex = 0,
            count = count,
            perDay = calendar.goalOn(day),
            restart = plan.restart,
            unitsBefore = if (before) 0 else calendar.unitsBefore(day),
            day = calendar.partsBefore(day) + 1,
            studyDay = calendar.isStudyDay(day),
            beforeStart = before,
            paused = calendar.pausedOn(day),
        )
    }

    private fun indexes(part: DayPart) = (part as DayPart.Part).indexes

    @Test
    fun `a goal changed from a day leaves the days before it as they were`() {
        val changed = plan(perDay = 5) { copy(startGoal = 2, goalChanges = listOf(GoalChange(first.plusDays(3), 5))) }
        // Days 0..2 at two a day: units 0-1, 2-3, 4-5; from the fourth day five a day, on from unit 6
        assertEquals(listOf(0, 1), indexes(part(changed, first)))
        assertEquals(listOf(4, 5), indexes(part(changed, first.plusDays(2))))
        assertEquals(listOf(6, 7, 8, 9, 10), indexes(part(changed, first.plusDays(3))))
        assertEquals(listOf(11, 12, 13, 14, 15), indexes(part(changed, first.plusDays(4))))
        // The same plan reckoned by its present goal alone would have put the third day at unit 10
        assertFalse(indexes(part(changed, first.plusDays(2))) == listOf(10, 11, 12, 13, 14))
    }

    @Test
    fun `a pause does not move the plan on and the days after it go on from where it stopped`() {
        val paused = plan(perDay = 1) { copy(pauses = listOf(PausePeriod(first.plusDays(2), first.plusDays(5)))) }
        assertEquals(listOf(1), indexes(part(paused, first.plusDays(1))))
        assertEquals(DayPart.Paused, part(paused, first.plusDays(2)))
        assertEquals(DayPart.Paused, part(paused, first.plusDays(4)))
        assertEquals(listOf(2), indexes(part(paused, first.plusDays(5))))
        assertEquals(listOf(3), indexes(part(paused, first.plusDays(6))))
        assertEquals(PlanState.ACTIVE, paused.state)
        assertEquals(PlanState.PAUSED, paused.copy(pauses = listOf(PausePeriod(first))).state)
    }

    @Test
    fun `a plan that was ended has no study days after the day it ended on`() {
        val ended = plan(perDay = 1) { copy(finished = first.plusDays(2)) }
        assertTrue(calendar(ended).isStudyDay(first.plusDays(2)))
        assertFalse(calendar(ended).isStudyDay(first.plusDays(3)))
        assertEquals(PlanState.FINISHED, ended.state)
    }

    @Test
    fun `a start put off by a day moves the first day and nothing else`() {
        val put = plan(perDay = 3) { copy(postponedDays = 1) }
        assertEquals(first.plusDays(1), put.firstDay)
        assertEquals(DayPart.NotYet, part(put, first))
        assertEquals(listOf(0, 1, 2), indexes(part(put, first.plusDays(1))))
        assertEquals(listOf(3, 4, 5), indexes(part(put, first.plusDays(2))))
        // Its weekdays are the plan's own, as they were
        assertEquals(plan().weekdays, put.weekdays)
    }

    @Test
    fun `the start can be put off once, before it has begun`() {
        val before = LocalDateTime.of(2026, 10, 31, 12, 0)
        val first = LocalDateTime.of(2026, 11, 1, 9, 0)
        assertTrue(canPostpone(plan(), before, inIsrael = true))
        assertFalse(canPostpone(plan { copy(postponedDays = 1) }, before, inIsrael = true))
        assertFalse(canPostpone(plan(), LocalDateTime.of(2026, 11, 2, 0, 0), inIsrael = true))
        // On the first day, before the minute it starts at, it has not begun
        assertTrue(canPostpone(plan { copy(startMinute = 18 * 60) }, first, inIsrael = true))
        assertFalse(canPostpone(plan { copy(startMinute = 8 * 60) }, first, inIsrael = true))
    }

    @Test
    fun `a plan stops at the end of the content chosen and does not go on to another`() {
        val all = units(10)
        val (cut, from) = planRange(all, start = listOf("פרק 3"), end = listOf("פרק 6"))
        assertEquals(listOf("פרק 3", "פרק 4", "פרק 5", "פרק 6"), cut.map { it.headings.single() })
        assertEquals(0, from)
        // Three a day: 3-5, 6 then nothing, then done: the plan does not slide on into פרק 7
        val plan = plan(perDay = 3, restart = false)
        val calendar = calendar(plan)
        fun partOn(offset: Long) =
            calendar.days(first.plusDays(offset)).last().let {
                dayPartFrom(from, cut.size, it.goal, plan.restart, it.unitsBefore, it.partsBefore + 1, studyDay = true)
            }
        assertEquals(listOf(0, 1, 2), indexes(partOn(0)))
        assertEquals(listOf(3), indexes(partOn(1)))
        assertEquals(DayPart.Finished, partOn(2))
        assertEquals(DayPart.Finished, partOn(30))
    }

    @Test
    fun `a plan without an end goes on to the content's end, as it did`() {
        val all = units(10)
        val (cut, from) = planRange(all, start = listOf("פרק 4"), end = emptyList())
        assertEquals(10, cut.size)
        assertEquals(3, from)
        // An end before the start, or one the content no longer has, is the content's end
        assertEquals(6, planRange(all, listOf("פרק 5"), listOf("פרק 2")).first.size)
        assertEquals(6, planRange(all, listOf("פרק 5"), listOf("פרק 99")).first.size)
    }

    @Test
    fun `a range that restarts goes round inside itself`() {
        val (cut, from) = planRange(units(10), start = listOf("פרק 3"), end = listOf("פרק 4"))
        val plan = plan(perDay = 1, restart = true)
        val calendar = calendar(plan)
        val indexes =
            (0L..3L).map { offset ->
                calendar.days(first.plusDays(offset)).last().let {
                    dayPartFrom(from, cut.size, it.goal, plan.restart, it.unitsBefore, it.partsBefore + 1, studyDay = true)
                }
            }.map { (it as DayPart.Part).indexes.single() }
        assertEquals(listOf(0, 1, 0, 1), indexes)
    }

    @Test
    fun `a part of several days counts once for each period and a pause at its start skips it`() {
        // A week from Sunday: 2026-11-01 is a Sunday; three units a week
        val weekly = plan(perDay = 3, periodDays = 7)
        assertEquals(listOf(0, 1, 2), indexes(part(weekly, first.plusDays(3))))
        assertEquals(listOf(3, 4, 5), indexes(part(weekly, first.plusDays(7))))
        val skipped = weekly.copy(pauses = listOf(PausePeriod(first.plusDays(7), first.plusDays(14))))
        assertEquals(DayPart.Paused, part(skipped, first.plusDays(9)))
        assertEquals(listOf(3, 4, 5), indexes(part(skipped, first.plusDays(14))))
    }

    @Test
    fun `the history counts each day by what was reported and by the tracking mode`() {
        val plan = plan(perDay = 1)
        val now = LocalDateTime.of(2026, 11, 6, 20, 0)
        val reports = mapOf(first to DayReport.DONE, first.plusDays(1) to DayReport.PARTIAL, first.plusDays(2) to DayReport.NOT_DONE)
        fun outcomes(mode: TrackingMode) =
            historyOf(plan.copy(tracking = mode), calendar(plan), reports, now, count = 100, startIndex = 0).map { it.outcome }
        assertEquals(
            listOf(
                DayOutcome.DONE,
                DayOutcome.PARTIAL,
                DayOutcome.MISSED,
                DayOutcome.MISSED,
                DayOutcome.MISSED,
                DayOutcome.PENDING,
            ),
            outcomes(TrackingMode.POSITIVE),
        )
        assertEquals(DayOutcome.DONE, outcomes(TrackingMode.NEGATIVE)[3])
        assertEquals(DayOutcome.UNREPORTED, outcomes(TrackingMode.FULL)[3])
        // The reports are the same in every mode
        listOf(0, 1, 2).forEach { assertEquals(outcomes(TrackingMode.POSITIVE)[it], outcomes(TrackingMode.FULL)[it]) }
        val stats = statsOf(historyOf(plan, calendar(plan), reports, now, 100, 0))
        assertEquals(1, stats.done)
        assertEquals(1, stats.partial)
        // Day 3 reported not done, days 4 and 5 not reported (positive mode); today is still to be reported
        assertEquals(3, stats.missed)
        assertEquals(20, stats.ratePercent)
    }

    @Test
    fun `days after the content's end, rest days and the days before the start are not counted`() {
        val plan = plan(perDay = 5, weekdays = java.time.DayOfWeek.entries.toSet() - java.time.DayOfWeek.FRIDAY)
        val now = LocalDateTime.of(2026, 11, 10, 20, 0)
        // 7 units at 5 a day: the plan is over after its second day
        val outcomes = historyOf(plan, calendar(plan), emptyMap(), now, count = 7, startIndex = 0).map { it.outcome }
        assertEquals(DayOutcome.MISSED, outcomes[0])
        assertEquals(DayOutcome.MISSED, outcomes[1])
        assertTrue(outcomes.drop(2).all { it == DayOutcome.REST || it == DayOutcome.PENDING })
        // A plan whose first day is in the future has no days yet
        val later = plan.copy(startDay = LocalDate.of(2026, 12, 1))
        assertTrue(historyOf(later, calendar(later), emptyMap(), now, 100, 0).isEmpty())
    }

    @Test
    fun `on its first day a plan has not started before its minute`() {
        val plan = plan(perDay = 1) { copy(startMinute = 18 * 60) }
        val morning = LocalDateTime.of(2026, 11, 1, 9, 0)
        val evening = LocalDateTime.of(2026, 11, 1, 19, 0)
        assertEquals(DayOutcome.NOT_YET, historyOf(plan, calendar(plan), emptyMap(), morning, 100, 0).single().outcome)
        assertEquals(DayOutcome.PENDING, historyOf(plan, calendar(plan), emptyMap(), evening, 100, 0).single().outcome)
    }

    @Test
    fun `the old reckoning is what a plan with no changes gives`() {
        val plain = plan(perDay = 3)
        (0L..40L).forEach { offset ->
            val day = first.plusDays(offset)
            val old = dayPart(startIndex = 0, count = 100, perDay = 3, restart = false, daysBefore = offset.toInt(), studyDay = true)
            assertEquals(old, part(plain, day), "day $offset")
        }
    }

    @Test
    fun `a week's part is one day of the history, reported on its first day and still to come while the week runs`() {
        val weekly = plan(perDay = 3, periodDays = 7)
        val inWeek3 = LocalDateTime.of(2026, 11, 18, 20, 0)
        val reports = mapOf(first to DayReport.DONE)
        val outcomes = historyOf(weekly, calendar(weekly), reports, inWeek3, count = 100, startIndex = 0)
        // The first week was reported, the second not, and the third is running: only the first days of the weeks are counted
        assertEquals(DayOutcome.DONE, outcomes[0].outcome)
        assertTrue(outcomes.subList(1, 7).all { it.outcome == DayOutcome.REST })
        assertEquals(DayOutcome.MISSED, outcomes[7].outcome)
        assertEquals(DayOutcome.PENDING, outcomes[14].outcome)
        val stats = statsOf(outcomes)
        assertEquals(1, stats.done)
        assertEquals(1, stats.missed)
    }

    @Test
    fun `a unit the user defines is so many lines with a name of the user's, the last one the rest`() {
        val chunk = ChunkUnit(20, "חצי עמוד")
        assertEquals(chunk, chunkOf(chunk.key))
        assertEquals(null, chunkOf("פרק"))
        assertEquals(null, chunkOf("#0#x"))
        assertEquals(ChunkUnit(5, "יחידה"), chunkOf("#5#"))
        val units = chunkUnits(totalLines = 45, chunk = chunk)
        assertEquals(listOf("חצי עמוד 1", "חצי עמוד 2", "חצי עמוד 3"), units.map { it.headings.single() })
        assertEquals(listOf(0 to 19, 20 to 39, 40 to 44), units.map { it.firstLine!! to it.lastLine!! })
        assertEquals(emptyList(), chunkUnits(0, chunk))
        // A plan by them studies them as any other: two a day from the second
        val (cut, from) = planRange(units, start = listOf("חצי עמוד 2"), end = emptyList())
        assertEquals(1, from)
        assertEquals(3, cut.size)
    }

    @Test
    fun `a start time is read as a minute of the day and written back`() {
        assertEquals(390, parseMinute("06:30"))
        assertEquals(390, parseMinute(" 6:30 "))
        assertEquals(0, parseMinute("00:00"))
        assertEquals(23 * 60 + 59, parseMinute("23:59"))
        assertEquals(null, parseMinute("24:00"))
        assertEquals(null, parseMinute("6:5"))
        assertEquals(null, parseMinute("630"))
        assertEquals(null, parseMinute(""))
        assertEquals("06:30", formatMinute(390))
        assertEquals("00:00", formatMinute(0))
        assertEquals("18:05", formatMinute(parseMinute("18:05")!!))
    }

    @Test
    fun `a category's books are its base books before the user changes any, and all of them when it has none`() {
        fun book(
            id: Long,
            title: String,
            base: Boolean,
        ) = Book(id = id, categoryId = 1, sourceId = 1, title = title, isBaseBook = base)
        val books = listOf(book(1, "שבת", true), book(2, "רש״י על שבת", false), book(3, "עירובין", true))
        // Not the commentaries: choosing a category does not bring them along
        assertEquals(setOf(1L, 3L), initialBooks(books, emptyList(), legacy = false, baseBooksOnly = false))
        val none = books.map { it.copy(isBaseBook = false) }
        assertEquals(setOf(1L, 2L, 3L), initialBooks(none, emptyList(), legacy = false, baseBooksOnly = false))
        // A plan of before keeps what it studied: the whole category, or its base books
        assertEquals(setOf(1L, 2L, 3L), initialBooks(books, emptyList(), legacy = true, baseBooksOnly = false))
        assertEquals(setOf(1L, 3L), initialBooks(books, emptyList(), legacy = true, baseBooksOnly = true))
        // A plan with books chosen keeps them, found by id while the title is the same, else by title
        assertEquals(setOf(2L), initialBooks(books, listOf(PlanBookRef(2, "רש״י על שבת")), legacy = false, baseBooksOnly = false))
        assertEquals(setOf(3L), initialBooks(books, listOf(PlanBookRef(99, "עירובין")), legacy = false, baseBooksOnly = false))
    }
}
