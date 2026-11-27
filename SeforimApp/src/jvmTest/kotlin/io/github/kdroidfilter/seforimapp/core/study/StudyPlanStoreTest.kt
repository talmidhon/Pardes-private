package io.github.kdroidfilter.seforimapp.core.study

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The study plans and their marks, kept in the user DB. */
class StudyPlanStoreTest {
    private val db = UserSettingsDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(UserSettingsDb.Schema::create))

    // The day the store takes for today: a plan's goal changed from then on, after the plan began
    private var now = LocalDate.of(2026, 11, 20)
    private val store = StudyPlanStore(db) { now }

    private val tehillim =
        StudyPlan(
            bookId = 28,
            bookTitle = "תהילים",
            categoryPath = listOf("תנ״ך", "כתובים"),
            level = 1,
            start = listOf("פרק כג"),
            perDay = 5,
            startDay = LocalDate.of(2026, 11, 12),
            weekdays = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY,
            restart = true,
        )

    @Test
    fun `a plan is kept with every field, a new one last, and changed in place`() =
        runBlocking {
            val first = store.save(tehillim)
            val second = store.save(tehillim.copy(bookId = 3243, bookTitle = "משנה ברורה", start = listOf("סימן קכח"), restart = false))
            assertEquals(
                listOf(
                    tehillim.copy(id = first),
                    tehillim.copy(id = second, bookId = 3243, bookTitle = "משנה ברורה", start = listOf("סימן קכח"), restart = false),
                ),
                store.plans(),
            )

            // The plan began on the 12th: its goal changed on the 20th holds from then, the first one stays what it was
            store.save(tehillim.copy(id = first, perDay = 3, weekdays = setOf(DayOfWeek.SUNDAY)))
            assertEquals(
                tehillim.copy(
                    id = first,
                    perDay = 3,
                    weekdays = setOf(DayOfWeek.SUNDAY),
                    startGoal = 5,
                    goalChanges = listOf(GoalChange(now, 3)),
                ),
                store.plans().first(),
            )
        }

    @Test
    fun `a day's part is marked done and unmarked, and a plan deleted takes its marks along`() =
        runBlocking {
            val id = store.save(tehillim)
            val other = store.save(tehillim.copy(bookTitle = "משלי"))
            val today = LocalDate.of(2026, 11, 12)
            store.setDone(id, today, true)
            store.setDone(other, today.plusDays(1), true)
            assertEquals(setOf(id), store.doneOn(today))
            store.setDone(id, today, false)
            assertEquals(emptySet(), store.doneOn(today))

            store.delete(other)
            assertEquals(listOf(id), store.plans().map { it.id })
            assertEquals(emptySet(), store.doneOn(today.plusDays(1)))
        }

    @Test
    fun `the weekdays go into one number and back`() {
        val days = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
        assertEquals(0b1001001L, days.toMask())
        assertEquals(days, weekdaysOf(days.toMask()))
        assertEquals(DayOfWeek.entries.toSet(), weekdaysOf(DayOfWeek.entries.toSet().toMask()))
    }

    @Test
    fun `a category's plan keeps its category, its base books alone, unit and days, and a book's plain plan keeps none`() =
        runBlocking {
            val mishnah =
                tehillim.copy(
                    bookId = 0,
                    bookTitle = "סדר מועד",
                    categoryPath = listOf("משנה", "סדר מועד"),
                    start = listOf("שבת", "פרק א"),
                    category = 77,
                    baseBooksOnly = true,
                    unitName = "פרק",
                    periodDays = 7,
                )
            val id = store.save(mishnah)
            val plain = store.save(tehillim)
            assertEquals(listOf(mishnah.copy(id = id), tehillim.copy(id = plain)), store.plans())
            val plainAgain = mishnah.copy(id = id, category = 0, baseBooksOnly = false, unitName = "", periodDays = 1)
            store.save(plainAgain)
            assertEquals(plainAgain, store.plans().first())
        }

    @Test
    fun `a part for several days is marked on its first day`() =
        runBlocking {
            val id = store.save(tehillim.copy(periodDays = 7))
            val sunday = LocalDate.of(2026, 11, 22)
            store.setDone(id, sunday, true)
            assertEquals(true, store.isDone(id, sunday))
            assertEquals(false, store.isDone(id, sunday.plusDays(1)))
            store.delete(id)
            assertEquals(false, store.isDone(id, sunday))
        }

    @Test
    fun `a day has one report, which a new one replaces, and a report taken back leaves none`() =
        runBlocking {
            val id = store.save(tehillim)
            val day = LocalDate.of(2026, 11, 15)
            store.setReport(id, day, DayReport.PARTIAL)
            assertEquals(DayReport.PARTIAL, store.reportOn(id, day))
            store.setReport(id, day, DayReport.DONE)
            store.setReport(id, day, DayReport.DONE)
            assertEquals(mapOf(day to DayReport.DONE), store.reportsOf(id))
            assertEquals(mapOf(id to DayReport.DONE), store.reportsOn(day))
            store.setReport(id, day, DayReport.NOT_DONE)
            assertEquals(emptySet(), store.doneOn(day))
            assertEquals(DayReport.NOT_DONE, store.reportsOf(id)[day])
            store.setReport(id, day, null)
            assertEquals(emptyMap(), store.reportsOf(id))
        }

    @Test
    fun `the marks of before the reports are days done, and a report of the day replaces its mark`() =
        runBlocking {
            val id = store.save(tehillim)
            val marked = LocalDate.of(2026, 11, 13)
            val other = LocalDate.of(2026, 11, 14)
            db.studyPlansQueries.markDone(id, marked.toEpochDay())
            db.studyPlansQueries.markDone(id, other.toEpochDay())
            assertEquals(true, store.isDone(id, marked))
            assertEquals(setOf(id), store.doneOn(other))
            store.setReport(id, other, DayReport.PARTIAL)
            assertEquals(mapOf(marked to DayReport.DONE, other to DayReport.PARTIAL), store.reportsOf(id))
            // Taken back with the old call too
            store.setDone(id, marked, false)
            assertEquals(mapOf(other to DayReport.PARTIAL), store.reportsOf(id))
        }

    @Test
    fun `a plan's name, description, tracking, end, start minute and books are kept, and a plain plan keeps no more`() =
        runBlocking {
            val full =
                tehillim.copy(
                    name = "תהילים בבוקר",
                    description = "אחרי התפילה",
                    tracking = TrackingMode.FULL,
                    end = listOf("פרק קיט"),
                    startMinute = 6 * 60 + 30,
                    books = listOf(PlanBookRef(5, "שבת"), PlanBookRef(6, "עירובין")),
                )
            val id = store.save(full)
            assertEquals(full.copy(id = id), store.plans().single())
            assertEquals(1, db.studyPlansQueries.selectMeta().executeAsList().size)
            store.save(
                full.copy(
                    id = id,
                    name = "",
                    description = "",
                    tracking = TrackingMode.POSITIVE,
                    end = emptyList(),
                    startMinute = 0,
                    books = emptyList(),
                ),
            )
            assertEquals(tehillim.copy(id = id), store.plans().single())
            assertEquals(0, db.studyPlansQueries.selectMeta().executeAsList().size)
            assertEquals(0, db.studyPlansQueries.selectBooks().executeAsList().size)
        }

    @Test
    fun `a goal changed once the plan began is a change from today and the goal it began with stays`() =
        runBlocking {
            val id = store.save(tehillim)
            store.save(tehillim.copy(id = id, perDay = 8))
            now = now.plusDays(10)
            store.save(tehillim.copy(id = id, perDay = 2))
            val plan = store.plans().single()
            assertEquals(2, plan.perDay)
            assertEquals(5, plan.startGoal)
            assertEquals(listOf(GoalChange(now.minusDays(10), 8), GoalChange(now, 2)), plan.goalChanges)
            // Saved again with the same goal, nothing is added
            store.save(plan)
            assertEquals(2, store.plans().single().goalChanges.size)
        }

    @Test
    fun `a goal changed before the plan began is the goal it begins with`() =
        runBlocking {
            val id = store.save(tehillim.copy(startDay = now.plusDays(3)))
            store.save(tehillim.copy(id = id, startDay = now.plusDays(3), perDay = 9))
            val plan = store.plans().single()
            assertEquals(9, plan.perDay)
            assertEquals(9, plan.startGoal)
            assertEquals(emptyList(), plan.goalChanges)
        }

    @Test
    fun `a plan is paused from a day and taken up again, a pause of one day leaving no trace`() =
        runBlocking {
            val id = store.save(tehillim)
            val from = LocalDate.of(2026, 11, 16)
            store.pause(id, from)
            store.pause(id, from.plusDays(1))
            assertEquals(listOf(PausePeriod(from)), store.plans().single().pauses)
            assertEquals(PlanState.PAUSED, store.plans().single().state)
            store.resume(id, from.plusDays(3))
            assertEquals(listOf(PausePeriod(from, from.plusDays(3))), store.plans().single().pauses)
            assertEquals(PlanState.ACTIVE, store.plans().single().state)
            store.pause(id, from.plusDays(5))
            store.resume(id, from.plusDays(5))
            assertEquals(1, store.plans().single().pauses.size)
        }

    @Test
    fun `a plan ended is taken up again, and its start is put off once`() =
        runBlocking {
            val id = store.save(tehillim)
            store.finish(id, now)
            assertEquals(PlanState.FINISHED, store.plans().single().state)
            assertEquals(now, store.plans().single().finished)
            store.finish(id, null)
            assertEquals(PlanState.ACTIVE, store.plans().single().state)
            assertEquals(0, db.studyPlansQueries.selectMeta().executeAsList().size)
            assertEquals(true, store.postpone(id))
            assertEquals(false, store.postpone(id))
            assertEquals(tehillim.startDay.plusDays(1), store.plans().single().firstDay)
            // Saving the plan again does not take the postponement back
            store.save(store.plans().single())
            assertEquals(1, store.plans().single().postponedDays)
        }

    @Test
    fun `a plan deleted takes everything it holds along`() =
        runBlocking {
            val id = store.save(tehillim.copy(name = "א", books = listOf(PlanBookRef(1, "שבת"))))
            val kept = store.save(tehillim.copy(bookTitle = "משלי"))
            store.setReport(id, now, DayReport.PARTIAL)
            store.pause(id, now)
            store.save(tehillim.copy(id = id, name = "א", books = listOf(PlanBookRef(1, "שבת")), perDay = 1))
            store.delete(id)
            val queries = db.studyPlansQueries
            assertEquals(listOf(kept), store.plans().map { it.id })
            assertEquals(0, queries.selectMeta().executeAsList().size + queries.selectGoals().executeAsList().size)
            assertEquals(0, queries.selectPauses().executeAsList().size + queries.selectBooks().executeAsList().size)
            assertEquals(emptyMap(), store.reportsOf(id))
        }
}
