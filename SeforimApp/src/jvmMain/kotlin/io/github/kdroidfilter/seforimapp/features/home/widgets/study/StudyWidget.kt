package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.study.DayReport
import io.github.kdroidfilter.seforimapp.core.study.PlanState
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.core.study.StudyStats
import io.github.kdroidfilter.seforimapp.core.study.TrackingMode
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.HoverBox
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCardLoading
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetMenuItem
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.shownDateHere
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_study_plans
import seforimapp.seforimapp.generated.resources.scope_base_books_label
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.WeakHashMap

// Pardes's own widget (request 19 of docs/DESKTOP_STAGE2.md, after the mock-up the user approved on 2026-11-12): the
// user's daily study plans, each with the day's part, its pace and a box to tick once it is done; a click opens the
// book on the part, marked through its end. The plans are kept in the user DB (core/study/StudyPlanStore.kt) and edited
// in StudyPlanEditor.kt. Ideas only from Otzaria's "לשקוד" add-on, which has no licence.

/**
 * A plan as the card shows it on a day: its book in this library (null: not there, or a category's plan), its units and
 * the day's part; the part a click opens ([held]): the day's, or on a day without study (before the plan starts too)
 * where it holds, the next day of study's; whether its book or category is in this library ([found]).
 *
 * The daily portion's upgrade: [startIndex], where the plan starts in [units] (cut to the plan's range), what the user
 * [report]ed for the day, the plan's [history] day by day and its [stats].
 */
@Immutable
internal data class ShownPlan(
    val plan: StudyPlan,
    val book: Book?,
    val name: UnitName,
    val units: List<StudyUnit>,
    val part: DayPart,
    val held: DayPart.Part? = part as? DayPart.Part,
    val found: Boolean = book != null,
    val startIndex: Int = 0,
    val report: DayReport? = null,
    val history: List<HistoryDay> = emptyList(),
    val stats: StudyStats? = null,
)

/** [plan]'s book in this library: by its id while it has the same title, else by its title. */
internal suspend fun SeforimRepository.bookOf(plan: StudyPlan): Book? =
    getBookCore(plan.bookId)?.takeIf { it.title == plan.bookTitle } ?: getBookByTitle(plan.bookTitle)

/**
 * Pardes (request 64 of stage 3): [plan]'s category in this library: by its id while it has the same title, else by its
 * path's titles from the root.
 */
internal suspend fun SeforimRepository.categoryOf(plan: StudyPlan): Category? {
    getCategory(plan.category)?.takeIf { it.title == plan.categoryPath.lastOrNull() }?.let { return it }
    var level = getRootCategories()
    var found: Category? = null
    for (title in plan.categoryPath) {
        found = level.firstOrNull { it.title == title } ?: return null
        level = getCategoryChildren(found.id)
    }
    return found
}

/** The books under [category] as the book tree lists them: its own, then each of its categories' in turn. */
internal suspend fun SeforimRepository.booksInTreeOrder(category: Category): List<Book> =
    getBooksByCategory(category.id) + getCategoryChildren(category.id).flatMap { booksInTreeOrder(it) }

/** [category]'s books (its base books alone with [baseBooksOnly]) with their tables of contents, in the book tree's order. */
internal suspend fun SeforimRepository.planBooks(
    category: Category,
    baseBooksOnly: Boolean = false,
): List<PlanBook> =
    booksInTreeOrder(category)
        .filter { !baseBooksOnly || it.isBaseBook }
        .map { PlanBook(it.id, it.title, getTocEntriesForBook(it.id)) }

/**
 * The books a category's plan studies as the user chose them ([StudyPlan.books], in the order they were chosen), each found
 * by its id while it has the same title, else by its title; the ones this library does not have are left out.
 */
internal suspend fun SeforimRepository.chosenBooks(plan: StudyPlan): List<PlanBook> =
    plan.books.mapNotNull { ref ->
        (getBookCore(ref.id)?.takeIf { it.title == ref.title } ?: getBookByTitle(ref.title))
            ?.let { PlanBook(it.id, it.title, getTocEntriesForBook(it.id)) }
    }

/** Where [plan]'s part of [day] is marked done: the day itself, or the first day of a part for several days. */
internal fun markDay(
    plan: StudyPlan,
    day: LocalDate,
): LocalDate = periodStart(plan.firstDay, day, plan.periodDays)

/**
 * [plan] on [day], in Israel or abroad ([inIsrael]): its book read from the library (a category's books, request 64 of
 * stage 3: the ones the user chose, else its own by its base books), its units by its unit, cut to where the plan starts
 * and ends, the day's part reckoned from the goals the plan had, its pauses and its first day ([PlanCalendar]): of the days
 * of study for a part a day, of its period for one of several days, every day of which shows it. With the [reports] of the
 * plan's days, its history and its statistics as of [now].
 */
internal suspend fun SeforimRepository.shownPlan(
    plan: StudyPlan,
    day: LocalDate,
    inIsrael: Boolean,
    reports: Map<LocalDate, DayReport> = emptyMap(),
    now: LocalDateTime = LocalDateTime.now(),
): ShownPlan {
    val missing = ShownPlan(plan, null, UnitName("יחידה", "יחידות"), emptyList(), DayPart.Finished, found = false)
    val book: Book?
    val units: List<StudyUnit>
    val name: UnitName
    if (plan.category != 0L) {
        val books =
            if (plan.books.isNotEmpty()) chosenBooks(plan) else planBooks(categoryOf(plan) ?: return missing, plan.baseBooksOnly)
        book = null
        units = categoryUnits(books, plan.unitName)
        name = categoryUnitNames(books).firstOrNull { it.one == plan.unitName } ?: UnitName(plan.unitName, "יחידות")
    } else {
        book = bookOf(plan) ?: return missing
        val entries = getTocEntriesForBook(book.id)
        val chunk = chunkOf(plan.unitName)
        units = if (chunk != null) chunkUnits(book.totalLines, chunk) else bookUnits(entries, plan.level, plan.unitName)
        name =
            chunk?.let { UnitName(it.name, it.name) }
                ?: bookUnitNames(studyLevels(entries)).firstOrNull { it.one == plan.unitName }
                ?: unitNameOf(entries.filter { it.level == plan.level }.map { it.text })
    }

    val calendar = plan.calendar(inIsrael)
    val (range, startIndex) = planRange(units, plan.start, plan.end)
    val before = day < calendar.firstDay || day == calendar.firstDay && day == now.toLocalDate() && !calendar.startedBy(now)
    val row = calendar.days(day).lastOrNull()
    val unitsBefore = row?.unitsBefore ?: 0L
    val partNumber = (row?.partsBefore ?: 0) + 1
    val goal = calendar.goalOn(day)

    fun partOf(studyDay: Boolean) =
        dayPartFrom(startIndex, range.size, goal, plan.restart, unitsBefore, partNumber, studyDay, before, row?.paused == true)
    val part = partOf(row?.studyDay == true)
    // On a day without study, a click opens where the plan holds: the next day of study's part
    val held =
        part as? DayPart.Part
            ?: (dayPartFrom(startIndex, range.size, goal, plan.restart, unitsBefore, partNumber, studyDay = true) as? DayPart.Part)
                .takeIf { part == DayPart.Rest || part == DayPart.NotYet || part == DayPart.Paused }
    val history = historyOf(plan, calendar, reports, now, range.size, startIndex)
    return ShownPlan(
        plan = plan,
        book = book,
        name = name,
        units = range,
        part = part,
        held = held,
        found = true,
        startIndex = startIndex,
        report = reports[markDay(plan, day)],
        history = history,
        stats = statsOf(history),
    )
}

/** Where a part is in its book: the line its first unit starts on, and the last line of its last one. */
internal suspend fun SeforimRepository.linesOf(
    book: Book,
    units: List<StudyUnit>,
    indexes: List<Int>,
): Pair<Long?, Long?> {
    val first = units.getOrNull(indexes.first()) ?: return null to null
    // A whole book (a category's plan by books): opened at its start, nothing marked
    if (first.tocId == 0L) return null to null
    // A unit the user cut by lines: from its first line through the last of the last unit of the part
    first.firstLine?.let { firstLine ->
        val lastLine = units[indexes.last()].takeIf { indexes.last() >= indexes.first() }?.lastLine ?: (book.totalLines - 1)
        return getLineByIndex(book.id, firstLine)?.id to getLineByIndex(book.id, lastLine.coerceAtMost(book.totalLines - 1))?.id
    }
    // A part across the book's end is marked to the end
    val last = units[indexes.last()].takeIf { indexes.last() >= indexes.first() }
    val endIndex = (last?.nextLineId?.let { getLine(it)?.lineIndex }?.minus(1) ?: (book.totalLines - 1)).coerceAtLeast(0)
    return first.lineId to getLineByIndex(book.id, endIndex.coerceAtMost(book.totalLines - 1))?.id
}

// The plan being edited from each Home (a window's), a new one with id 0: Home's state is Zayit's, so it is kept here
private val editing = WeakHashMap<HomeWidgetsState, MutableState<StudyPlan?>>()

internal val HomeWidgetsState.editedPlan: MutableState<StudyPlan?> get() = editing.getOrPut(this) { mutableStateOf(null) }

// The plan whose history is open from each Home (a window's), as the edited one is
private val viewing = WeakHashMap<HomeWidgetsState, MutableState<StudyPlan?>>()

internal val HomeWidgetsState.viewedPlan: MutableState<StudyPlan?> get() = viewing.getOrPut(this) { mutableStateOf(null) }

/**
 * A new plan, from [day] on, every day but Shabbat and Yom Tov, its book still to choose. It ends where its content does:
 * it does not start again unless the user asks (a plan of before keeps what it had).
 */
internal fun newPlan(day: LocalDate): StudyPlan =
    StudyPlan(
        bookId = 0,
        bookTitle = "",
        level = 1,
        start = emptyList(),
        startDay = day,
        weekdays =
            java.time.DayOfWeek.entries
                .toSet() - java.time.DayOfWeek.SATURDAY,
        restart = false,
    )

/** The user's study plans: the day's part of each, a tick once it is learnt, a click to learn it. */
internal object StudyWidget : HomeWidget {
    override val id = "study_plans"
    override val title = Res.string.home_widget_name_study_plans
    override val defaultSpan = CellSpan(6, 4)
    override val minSpan = CellSpan(4, 3)
    override val maxSpan = CellSpan(12, 8)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val graph = LocalAppGraph.current
        val store = graph.studyPlanStore
        val revision by store.revision.collectAsState()
        val day = state.shownDateHere()
        val inIsrael = state.inIsrael
        val plans by produceState<List<ShownPlan>?>(null, revision, day, inIsrael) {
            // No library yet (being installed or replaced): the plans without their parts
            value =
                runSuspendCatching {
                    store.plans().map { graph.repository.shownPlan(it, day, inIsrael, store.reportsOf(it.id)) }
                }.getOrNull() ?: emptyList()
        }
        // Each plan's mark on the day, or on its period's first day for a part of several days (request 64 of stage 3)
        val done = plans.orEmpty().filter { it.report == DayReport.DONE }.mapTo(HashSet()) { it.plan.id }
        val shown = plans ?: return WidgetCardLoading(modifier)
        val scope = rememberCoroutineScope()
        StudyCard(
            plans = shown,
            done = done,
            today = day == LocalDate.now(),
            onDone = { plan, checked -> scope.launch { store.setDone(plan.id, markDay(plan, day), checked) } },
            onReport = { plan, report -> scope.launch { store.setReport(plan.id, markDay(plan, day), report) } },
            onHistory = { state.viewedPlan.value = it },
            onOpen = { plan ->
                val part = plan.held
                if (plan.found && part != null) {
                    scope.launch {
                        // A category's part opens in its first unit's book, marked through its last unit there
                        val first = plan.units[part.indexes.first()]
                        val book = plan.book ?: graph.repository.getBookCore(first.bookId) ?: return@launch
                        val inBook = part.indexes.takeWhile { plan.units[it].bookId == first.bookId }
                        val (lineId, endLineId) = graph.repository.linesOf(book, plan.units, inBook)
                        state.openTab(
                            TabsDestination.BookContent(
                                bookId = book.id,
                                tabId = UUID.randomUUID().toString(),
                                lineId = lineId,
                                endLineId = endLineId,
                            ),
                        )
                    }
                }
            },
            onEdit = { state.editedPlan.value = it },
            onNew = { state.editedPlan.value = newPlan(LocalDate.now()) },
            modifier = modifier,
        )
    }

    @Composable
    override fun menuItems(state: HomeWidgetsState) =
        listOf(WidgetMenuItem("תוכנית חדשה…", icon = AllIconsKeys.General.Add) { state.editedPlan.value = newPlan(LocalDate.now()) })

    @Composable
    override fun Detached(state: HomeWidgetsState) {
        val edited by state.editedPlan
        edited?.let { plan -> StudyPlanEditor(plan, state, onClose = { state.editedPlan.value = null }) }
        val viewed by state.viewedPlan
        viewed?.let { plan -> StudyHistoryWindow(plan, state, onClose = { state.viewedPlan.value = null }) }
    }
}

/** The card: its title, a row for each plan, and the link to a new one; a word on what it is for while it has none. */
@Composable
internal fun StudyCard(
    plans: List<ShownPlan>,
    done: Set<Long>,
    today: Boolean,
    onDone: (StudyPlan, Boolean) -> Unit,
    onOpen: (ShownPlan) -> Unit,
    onEdit: (StudyPlan) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
    onReport: (StudyPlan, DayReport?) -> Unit = { _, _ -> },
    onHistory: (StudyPlan) -> Unit = {},
) {
    val accent = rememberAccentColor(JewelTheme.isDark)
    val info = JewelTheme.globalColors.text.info
    PanelCard(modifier) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.size(8.dp))
                Text("הלימוד שלי", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            if (plans.isEmpty()) {
                Text(
                    "עוד אין כאן תוכניות. בוחרים ספר, כמה לומדים ביום ובאילו ימים, והחלק של כל יום מופיע כאן.",
                    fontSize = 12.sp,
                    color = info,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                plans.forEach { plan ->
                    PlanRow(plan, plan.plan.id in done, today, accent, onDone, onOpen, onEdit, onReport, onHistory)
                }
            }
            HoverBox(onClick = onNew, modifier = Modifier.testTag("study-new-plan")) {
                Text(
                    "+ תוכנית חדשה",
                    fontSize = 13.sp,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** One plan: the box ticked once the day's part is learnt, the book, the part, its pace and, while learning, how far. */
@Suppress("LongParameterList") // the row's data and its three actions
@Composable
private fun PlanRow(
    plan: ShownPlan,
    isDone: Boolean,
    today: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onDone: (StudyPlan, Boolean) -> Unit,
    onOpen: (ShownPlan) -> Unit,
    onEdit: (StudyPlan) -> Unit,
    onReport: (StudyPlan, DayReport?) -> Unit,
    onHistory: (StudyPlan) -> Unit,
) {
    val info = JewelTheme.globalColors.text.info
    val part = plan.part
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(hover)
            .padding(vertical = 4.dp)
            .testTag("study-plan-${plan.plan.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = isDone,
            onCheckedChange = { onDone(plan.plan, it) },
            enabled = part is DayPart.Part,
            modifier = Modifier.padding(top = 2.dp),
        )
        HoverBox(
            onClick = if (plan.held != null) ({ onOpen(plan) }) else null,
            modifier = Modifier.weight(1f).alpha(if (isDone) 0.55f else 1f),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(
                    // A category's plan: its title, marked in its base books alone as the bar marks it (request 64 of stage 3)
                    plan.plan.name.ifEmpty {
                        val baseOnly = plan.plan.baseBooksOnly && plan.plan.books.isEmpty()
                        plan.book?.title ?: plan.plan.bookTitle.let {
                            if (baseOnly) stringResource(Res.string.scope_base_books_label, it) else it
                        }
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(partText(plan), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metaText(plan, isDone, today), fontSize = 12.sp, color = info, maxLines = 1, overflow = TextOverflow.Ellipsis)
                statsText(plan)?.let { Text(it, fontSize = 12.sp, color = info, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (part is DayPart.Part && !isDone) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(info.copy(alpha = 0.25f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(part.progress).background(accent))
                    }
                }
            }
        }
        ReportChips(plan, accent, onReport, onHistory)
        IconButton(
            onClick = { onEdit(plan.plan) },
            modifier = Modifier.size(24.dp).alpha(if (hovered) 1f else 0f).testTag("study-edit-${plan.plan.id}"),
        ) {
            Icon(AllIconsKeys.Actions.Edit, contentDescription = "עריכת התוכנית", tint = info)
        }
    }
}

/** What the day holds: its part by headings, or why there is none. */
private fun partText(plan: ShownPlan): String =
    when (val part = plan.part) {
        is DayPart.Part -> partLabel(part.indexes.map { plan.units[it] })
        DayPart.Rest -> if (plan.plan.state == PlanState.FINISHED) "התוכנית הסתיימה" else "אין לימוד היום"
        DayPart.Paused -> "התוכנית מושהית"
        DayPart.NotYet -> "מתחילים ב־${plan.plan.startDay.dayOfMonth}.${plan.plan.startDay.monthValue}"
        DayPart.Finished ->
            when {
                plan.plan.category != 0L && !plan.found -> "הקטגוריה לא נמצאה בספרייה"
                !plan.found -> "הספר לא נמצא בספרייה"
                plan.plan.category != 0L -> "סיימת את הקטגוריה"
                else -> "סיימת את הספר"
            }
    }

/** "6 סעיפים ביום · יום 41", "5 פרקים ביום, חוץ משבת ויום טוב", "פרק ביום · סיימת להיום", "10 פרקים בשבוע · שבוע 3". */
private fun metaText(
    plan: ShownPlan,
    isDone: Boolean,
    today: Boolean,
): String {
    val daily = plan.plan.periodDays <= 1
    val days = weekdaysLabel(plan.plan.weekdays).takeIf { daily }
    val pace = listOfNotNull(perPeriodLabel(plan.plan.perDay, plan.name, plan.plan.periodDays), days).joinToString(", ")
    val part = plan.part as? DayPart.Part ?: return pace
    val which =
        when (plan.plan.periodDays) {
            1 -> "יום"
            7 -> "שבוע"
            else -> "חלק"
        }
    return when {
        isDone -> "$pace · ${if (today && daily) "סיימת להיום" else "סומן"}"
        else -> "$pace · $which ${part.day}"
    }
}

/** "רצף 5 · עמידה ביעד 92%": the run of days the goal was reached and the share kept; null while no day has been counted. */
private fun statsText(plan: ShownPlan): String? {
    val stats = plan.stats ?: return null
    val rate = stats.ratePercent ?: return null
    return "רצף ${stats.currentStreak} · עמידה ביעד $rate%"
}

/**
 * What a plan's day can be reported as besides done (the box beside it): partly done, and, unless only the days done are
 * reported, not done; pressed again, a report is taken back. And the way to the plan's history.
 */
@Composable
private fun ReportChips(
    plan: ShownPlan,
    accent: Color,
    onReport: (StudyPlan, DayReport?) -> Unit,
    onHistory: (StudyPlan) -> Unit,
) {
    val id = plan.plan.id
    val reportable = plan.part is DayPart.Part
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReportChip("½", plan.report == DayReport.PARTIAL, accent, reportable, "study-partial-$id") {
                onReport(plan.plan, if (plan.report == DayReport.PARTIAL) null else DayReport.PARTIAL)
            }
            if (plan.plan.tracking != TrackingMode.POSITIVE) {
                ReportChip("✗", plan.report == DayReport.NOT_DONE, accent, reportable, "study-missed-$id") {
                    onReport(plan.plan, if (plan.report == DayReport.NOT_DONE) null else DayReport.NOT_DONE)
                }
            }
        }
        if (plan.history.isNotEmpty()) {
            HoverBox(onClick = { onHistory(plan.plan) }, modifier = Modifier.testTag("study-history-$id")) {
                Text("היסטוריה", fontSize = 12.sp, color = accent, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
    }
}

/** A day's report as a round button: full in the accent while it is the one reported. */
@Composable
internal fun ReportChip(
    label: String,
    chosen: Boolean,
    accent: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(22.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (chosen) accent else Color.Transparent)
            .border(1.dp, if (chosen) accent else JewelTheme.globalColors.borders.normal, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, color = if (chosen) Color.White else JewelTheme.globalColors.text.normal)
    }
}
