package io.github.kdroidfilter.seforimapp.core.study

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.db.Study_plan
import io.github.kdroidfilter.seforimapp.db.Study_plan_meta
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pardes: a plan of daily study (request 19 of docs/DESKTOP_STAGE2.md). The book [bookTitle] under [categoryPath] (its
 * categories' titles, the root first; [bookId] in the library it was made in), studied by the entries of its table of
 * contents at depth [level], [perDay] of them a day, from the one whose headings are [start] (from the book's top
 * level down), from [startDay] on the [weekdays] chosen; when Shabbat is not one of them, Yom Tov is skipped too. At
 * the book's end it starts again if [restart], else it is done.
 *
 * Request 64 of the list after the third testers' build: a whole [category] studied (its id, 0 for a book; its books in
 * the book tree's order, [bookTitle] its title and [categoryPath] its own path), or its base books alone
 * ([baseBooksOnly], as the search's bar offers a category: תלמוד בבלי's tractates), the unit by its name in each book
 * ([unitName]: "פרק"; "" the depth [level]; [DAF_UNIT] two amudim; [BOOK_UNIT] a book), and [perDay] units for
 * [periodDays] days (1 a day; 7 a week, from Sunday, and 14 two; 2 or 3 from [startDay]).
 *
 * The daily portion's upgrade: a plan has a [name] and a [description]; an [end], the headings of the last unit it
 * studies ("" through the content's end); [books], the books of a category's plan as the user chose them (none: the
 * category's, by [baseBooksOnly], as it was); a [tracking] mode for how its days are reported; a [startMinute], the
 * minute of its first day it starts at, and [postponedDays], the day its start was put off by (once); [startGoal], the
 * goal it began with, [perDay] being the one now, and [goalChanges], the goals after the first; [pauses]; and the day
 * the user ended it ([finished]).
 */
@Immutable
data class StudyPlan(
    val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val categoryPath: List<String> = emptyList(),
    val level: Int,
    val start: List<String>,
    val perDay: Int = 1,
    val startDay: LocalDate,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val restart: Boolean = true,
    val category: Long = 0,
    val baseBooksOnly: Boolean = false,
    val unitName: String = "",
    val periodDays: Int = 1,
    val name: String = "",
    val description: String = "",
    val tracking: TrackingMode = TrackingMode.POSITIVE,
    val end: List<String> = emptyList(),
    val books: List<PlanBookRef> = emptyList(),
    val startMinute: Int = 0,
    val postponedDays: Int = 0,
    val startGoal: Int = perDay,
    val goalChanges: List<GoalChange> = emptyList(),
    val pauses: List<PausePeriod> = emptyList(),
    val finished: LocalDate? = null,
) {
    /** The first day of study: [startDay], or the day after it once the start was put off. */
    val firstDay: LocalDate get() = startDay.plusDays(postponedDays.toLong())

    /** Where the plan stands: ended by the user, paused now, or going on. */
    val state: PlanState
        get() =
            when {
                finished != null -> PlanState.FINISHED
                pauses.any { it.until == null } -> PlanState.PAUSED
                else -> PlanState.ACTIVE
            }

    companion object {
        /** A unit of two amudim, as a daf is learnt (the Bavli's contents go by amud). */
        const val DAF_UNIT = "דף"

        /** A book a unit, in a category's plan. */
        const val BOOK_UNIT = "ספר"
    }
}

/** A book of a category's plan, by its id and its title, as [StudyPlan.bookId] and [StudyPlan.bookTitle] keep a plan's book. */
@Immutable
data class PlanBookRef(
    val id: Long,
    val title: String,
)

/**
 * The user's study plans and the days each one's part was marked done, in the user DB (tables study_plan and
 * study_plan_done), so the backup carries them, for the Home's "הלימוד שלי" (features/home/widgets/study/). [revision]
 * goes up on every change.
 */
class StudyPlanStore(
    database: UserSettingsDb,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val queries = database.studyPlansQueries

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Every plan, in the order they were made. */
    suspend fun plans(): List<StudyPlan> =
        withContext(Dispatchers.IO) {
            val more = queries.selectMore().executeAsList().associateBy { it.planId }
            val meta = queries.selectMeta().executeAsList().associateBy { it.planId }
            val goals = queries.selectGoals().executeAsList().groupBy { it.planId }
            val pauses = queries.selectPauses().executeAsList().groupBy { it.planId }
            val books = queries.selectBooks().executeAsList().groupBy { it.planId }
            queries.selectAll().executeAsList().map { row ->
                val plan = row.toPlan()
                val withMore =
                    more[row.id]?.let {
                        plan.copy(
                            category = it.categoryId,
                            baseBooksOnly = it.baseBooksOnly != 0L,
                            unitName = it.unitName,
                            periodDays = it.periodDays.toInt().coerceAtLeast(1),
                        )
                    } ?: plan
                // A plan's goal now is the last of its changes, the goal it began with the column's
                val changes =
                    goals[row.id].orEmpty().map { GoalChange(LocalDate.ofEpochDay(it.fromDay), it.perDay.toInt().coerceAtLeast(1)) }
                withMore
                    .copy(
                        perDay = changes.lastOrNull()?.perDay ?: withMore.perDay,
                        startGoal = withMore.perDay,
                        goalChanges = changes,
                        pauses =
                            pauses[row.id].orEmpty().map {
                                PausePeriod(LocalDate.ofEpochDay(it.fromDay), it.untilDay?.let(LocalDate::ofEpochDay))
                            },
                        books = books[row.id].orEmpty().map { PlanBookRef(it.bookId, it.bookTitle) },
                    ).withMeta(meta[row.id])
            }
        }

    /** Saves [plan], a new one (id 0) last; its id. */
    suspend fun save(plan: StudyPlan): Long =
        withContext(Dispatchers.IO) {
            val id =
                queries.transactionWithResult {
                    if (plan.id == 0L) {
                        queries.insert(
                            bookId = plan.bookId,
                            bookTitle = plan.bookTitle,
                            categoryPath = plan.categoryPath.joined(),
                            level = plan.level.toLong(),
                            startPath = plan.start.joined(),
                            perDay = plan.perDay.toLong(),
                            startDay = plan.startDay.toEpochDay(),
                            weekdays = plan.weekdays.toMask(),
                            restart = if (plan.restart) 1L else 0L,
                            createdAt = System.currentTimeMillis(),
                        )
                        queries.lastInsertId().executeAsOne().also {
                            saveMore(it, plan)
                            saveExtras(it, plan)
                        }
                    } else {
                        val goal = settleGoal(plan)
                        queries.update(
                            bookId = plan.bookId,
                            bookTitle = plan.bookTitle,
                            categoryPath = plan.categoryPath.joined(),
                            level = plan.level.toLong(),
                            startPath = plan.start.joined(),
                            perDay = goal,
                            startDay = plan.startDay.toEpochDay(),
                            weekdays = plan.weekdays.toMask(),
                            restart = if (plan.restart) 1L else 0L,
                            id = plan.id,
                        )
                        saveMore(plan.id, plan)
                        saveExtras(plan.id, plan)
                        plan.id
                    }
                }
            _revision.update { it + 1 }
            id
        }

    // A plan's more (request 64): a row only for a plan that needs one, a book's by its depth a part a day having none
    private fun saveMore(
        id: Long,
        plan: StudyPlan,
    ) {
        if (plan.category == 0L && plan.unitName.isEmpty() && plan.periodDays <= 1) {
            queries.deleteMore(id)
        } else {
            queries.saveMore(id, plan.category, if (plan.baseBooksOnly) 1L else 0L, plan.unitName, plan.periodDays.toLong())
        }
    }

    /** Deletes plan [id] and its marks. */
    suspend fun delete(id: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.deleteDoneOf(id)
                queries.deleteReportsOf(id)
                queries.deleteGoalsOf(id)
                queries.deletePausesOf(id)
                queries.deleteBooksOf(id)
                queries.deleteMeta(id)
                queries.deleteMore(id)
                queries.delete(id)
            }
            _revision.update { it + 1 }
        }

    /** Whether plan [planId]'s part of [day] (the first day of its period, for a plan of several days) was marked done. */
    suspend fun isDone(
        planId: Long,
        day: LocalDate,
    ): Boolean = reportOn(planId, day) == DayReport.DONE

    /** The plans whose part of [day] was marked done. */
    suspend fun doneOn(day: LocalDate): Set<Long> = reportsOn(day).filterValues { it == DayReport.DONE }.keys

    /** Marks plan [planId]'s part of [day] done, or not. */
    suspend fun setDone(
        planId: Long,
        day: LocalDate,
        done: Boolean,
    ): Unit = setReport(planId, day, if (done) DayReport.DONE else null)

    /** What the user reported for plan [planId] on [day]; null: nothing. */
    suspend fun reportOn(
        planId: Long,
        day: LocalDate,
    ): DayReport? = reportsOf(planId)[day]

    /**
     * Every day plan [planId] was reported on. A mark of before the reports (study_plan_done) is a day done, unless a
     * report of the day says otherwise.
     */
    suspend fun reportsOf(planId: Long): Map<LocalDate, DayReport> =
        withContext(Dispatchers.IO) {
            val reports = HashMap<LocalDate, DayReport>()
            queries.selectDoneOf(planId).executeAsList().forEach { reports[LocalDate.ofEpochDay(it)] = DayReport.DONE }
            queries.selectReportsOf(planId).executeAsList().forEach { row ->
                DayReport.of(row.status)?.let { reports[LocalDate.ofEpochDay(row.day)] = it }
            }
            reports
        }

    /** What was reported on [day], by plan. */
    suspend fun reportsOn(day: LocalDate): Map<Long, DayReport> =
        withContext(Dispatchers.IO) {
            val reports = HashMap<Long, DayReport>()
            queries.selectDoneOn(day.toEpochDay()).executeAsList().forEach { reports[it] = DayReport.DONE }
            queries.selectReportsOn(day.toEpochDay()).executeAsList().forEach { row ->
                DayReport.of(row.status)?.let { reports[row.planId] = it }
            }
            reports
        }

    /** Reports plan [planId]'s [day], replacing what was reported before; null takes the report back. A day has one report. */
    suspend fun setReport(
        planId: Long,
        day: LocalDate,
        report: DayReport?,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.unmarkDone(planId, day.toEpochDay())
                queries.deleteReport(planId, day.toEpochDay())
                if (report != null) queries.saveReport(planId, day.toEpochDay(), report.code)
            }
            _revision.update { it + 1 }
        }

    /** Pauses plan [id] from [day] on; a plan already paused stays as it is. */
    suspend fun pause(
        id: Long,
        day: LocalDate,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                if (queries.selectPauses().executeAsList().none { it.planId == id && it.untilDay == null }) {
                    queries.openPause(id, day.toEpochDay())
                }
            }
            _revision.update { it + 1 }
        }

    /** Takes plan [id] up again on [day]: a pause begun the same day leaves no trace. */
    suspend fun resume(
        id: Long,
        day: LocalDate,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                val open = queries.selectPauses().executeAsList().firstOrNull { it.planId == id && it.untilDay == null }
                if (open != null && open.fromDay >= day.toEpochDay()) {
                    queries.deletePause(id, open.fromDay)
                } else if (open != null) {
                    queries.closePause(day.toEpochDay(), id)
                }
            }
            _revision.update { it + 1 }
        }

    /** Ends plan [id] on [day], or with null takes it up again. */
    suspend fun finish(
        id: Long,
        day: LocalDate?,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction { changeMeta(id) { it.copy(finished = day) } }
            _revision.update { it + 1 }
        }

    /**
     * Puts the start of plan [id] off by a day, once: false for a plan whose start was put off already. The plan's weekdays
     * and goal stay as they were, only its first day moves.
     */
    suspend fun postpone(id: Long): Boolean =
        withContext(Dispatchers.IO) {
            val done =
                queries.transactionWithResult {
                    val postponed = queries.selectMeta().executeAsList().firstOrNull { it.planId == id }?.postponedDays ?: 0L
                    if (postponed != 0L) {
                        false
                    } else {
                        changeMeta(id) { it.copy(postponedDays = 1) }
                        true
                    }
                }
            if (done) _revision.update { it + 1 }
            done
        }

    // What a plan holds besides, in tables of their own (the daily portion's upgrade): a meta row only for a plan that needs one
    private fun saveExtras(
        id: Long,
        plan: StudyPlan,
    ) {
        saveMeta(id, plan)
        queries.deleteBooksOf(id)
        plan.books.forEachIndexed { position, book -> queries.saveBook(id, position.toLong(), book.id, book.title) }
    }

    private fun saveMeta(
        id: Long,
        plan: StudyPlan,
    ) {
        if (plan.hasPlainMeta()) {
            queries.deleteMeta(id)
        } else {
            queries.saveMeta(
                planId = id,
                name = plan.name,
                description = plan.description,
                finishedDay = plan.finished?.toEpochDay() ?: NOT_FINISHED,
                tracking = plan.tracking.code,
                endPath = plan.end.joined(),
                startMinute = plan.startMinute.toLong(),
                postponedDays = plan.postponedDays.toLong(),
            )
        }
    }

    // [plan]'s goal as the column of study_plan keeps it: the one the plan began with. A goal changed once the plan has
    // begun becomes a change from today, which leaves the days before it as they were; before it, it is the one the plan begins with.
    private fun settleGoal(plan: StudyPlan): Long {
        val row = queries.selectOne(plan.id).executeAsOneOrNull() ?: return plan.perDay.toLong()
        val current = queries.selectGoals().executeAsList().lastOrNull { it.planId == plan.id }?.perDay ?: row.perDay
        if (plan.perDay.toLong() == current) return row.perDay
        val day = today()
        if (day < plan.firstDay) {
            queries.deleteGoalsOf(plan.id)
            return plan.perDay.toLong()
        }
        queries.saveGoal(plan.id, day.toEpochDay(), plan.perDay.toLong())
        return row.perDay
    }

    // A plan's meta as it is (a plain one when it has none), changed by [change], and kept
    private fun changeMeta(
        id: Long,
        change: (StudyPlan) -> StudyPlan,
    ) {
        val row = queries.selectOne(id).executeAsOneOrNull() ?: return
        val meta = queries.selectMeta().executeAsList().firstOrNull { it.planId == id }
        saveMeta(id, change(row.toPlan().withMeta(meta)))
    }
}

// Between two headings or two categories: a character no title has
private const val SEPARATOR = '\u001F'

private fun List<String>.joined(): String = joinToString(SEPARATOR.toString())

private fun String.parts(): List<String> = if (isEmpty()) emptyList() else split(SEPARATOR)

/** Sunday the lowest bit, Shabbat the highest. */
internal fun Set<DayOfWeek>.toMask(): Long = fold(0L) { mask, day -> mask or (1L shl (day.value % 7)) }

internal fun weekdaysOf(mask: Long): Set<DayOfWeek> = DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and (1L shl (it.value % 7)) != 0L }

private fun Study_plan.toPlan(): StudyPlan =
    StudyPlan(
        id = id,
        bookId = bookId,
        bookTitle = bookTitle,
        categoryPath = categoryPath.parts(),
        level = level.toInt(),
        start = startPath.parts(),
        perDay = perDay.toInt().coerceAtLeast(1),
        startDay = LocalDate.ofEpochDay(startDay),
        weekdays = weekdaysOf(weekdays),
        restart = restart != 0L,
    )

// The day a plan was ended on, in its meta row, while it goes on
private const val NOT_FINISHED = -1L

private fun StudyPlan.hasPlainMeta(): Boolean =
    name.isEmpty() && description.isEmpty() && finished == null && tracking == TrackingMode.POSITIVE && end.isEmpty() &&
        startMinute == 0 && postponedDays == 0

private fun StudyPlan.withMeta(meta: Study_plan_meta?): StudyPlan =
    if (meta == null) {
        this
    } else {
        copy(
            name = meta.name,
            description = meta.description,
            finished = meta.finishedDay.takeIf { it != NOT_FINISHED }?.let(LocalDate::ofEpochDay),
            tracking = TrackingMode.of(meta.tracking),
            end = meta.endPath.parts(),
            startMinute = meta.startMinute.toInt().coerceIn(0, MINUTES_IN_DAY - 1),
            postponedDays = meta.postponedDays.toInt().coerceIn(0, 1),
        )
    }

private const val MINUTES_IN_DAY = 24 * 60
