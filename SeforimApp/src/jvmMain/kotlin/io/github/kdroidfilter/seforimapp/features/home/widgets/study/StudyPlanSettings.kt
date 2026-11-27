package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.study.PlanState
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.core.study.TrackingMode
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.time.LocalDate
import java.time.LocalDateTime

// Pardes (the daily portion's upgrade): the rows of the plan's editor (StudyPlanEditor.kt) for what a plan holds besides:
// its name, the books of a category that it studies, where it ends, a unit of the user's own, how its days are reported,
// the minute it starts at, and where it stands (paused, ended, its start put off). Their fields are PlanExtras'.

/** The plan's name and description: the name is the book's, or the category's, while it is empty. */
@Composable
internal fun NameRows(extras: PlanExtras) {
    FormRow("שם") {
        TextField(
            extras.nameField,
            Modifier.fillMaxWidth().testTag("study-name"),
            placeholder = { Text("אפשר להשאיר ריק: שם הספר") },
        )
    }
    FormRow("תיאור") { TextField(extras.descriptionField, Modifier.fillMaxWidth().testTag("study-description")) }
}

/**
 * The books of a category's plan, as the user chooses them: its base books to begin with (choosing a category does not bring
 * its commentaries along), the others when ticked, in the book tree's order. For a category's plan only.
 */
@Composable
internal fun BooksRows(
    extras: PlanExtras,
    books: List<Book>,
) {
    val chosen = extras.chosen ?: return
    val base = books.filter { it.isBaseBook }.ifEmpty { books }.mapTo(LinkedHashSet()) { it.id }
    FormRow("ספרים בתוכנית") {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice("ספרי יסוד", chosen == base) { extras.chosen = base }
                Choice("הכול", chosen.size == books.size) { extras.chosen = books.mapTo(LinkedHashSet()) { it.id } }
                Choice("נקה", false) { extras.chosen = emptySet() }
            }
            LazyColumn(Modifier.fillMaxWidth().height(BOOKS_LIST_HEIGHT.dp).testTag("study-books")) {
                items(books, key = { it.id }) { book ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Checkbox(
                            checked = book.id in chosen,
                            onCheckedChange = { on -> extras.chosen = if (on) chosen + book.id else chosen - book.id },
                        )
                        Text(if (book.isBaseBook) "${book.title} (ספר יסוד)" else book.title)
                    }
                }
            }
            Hint("מפרשים נכללים רק אם סימנת אותם")
        }
    }
}

private const val BOOKS_LIST_HEIGHT = 180

/** Where the plan ends: the last unit it studies, or the content's end. When the content chosen is over, the plan is. */
@Composable
internal fun EndRow(
    extras: PlanExtras,
    units: List<StudyUnit>,
    startIndex: Int,
) {
    if (units.isEmpty()) return
    val after = units.drop(startIndex)
    val items = listOf("עד סוף התוכן") + after.map { it.headings.joinToString(", ") }
    val selected = after.indexOfFirst { it.headings == extras.end }.takeIf { it >= 0 }?.plus(1) ?: 0
    FormRow("מסיימים ב־") {
        ListComboBox(
            items = items,
            selectedIndex = selected,
            onSelectedItemChange = { index -> extras.end = if (index == 0) emptyList() else after[index - 1].headings },
            modifier = Modifier.fillMaxWidth().testTag("study-end"),
        )
    }
}

/** A unit of the user's own for a book: so many lines, with a name, for a book whose contents have no division to learn by. */
@Composable
internal fun CustomUnitRows(
    fields: PlanFields,
) {
    val extras = fields.extras
    LaunchedEffect(extras) {
        snapshotFlow { extras.chunk() }.collect { chunk ->
            if (extras.custom && chunk != null && fields.unitName != chunk.key) fields.pickUnit(chunk.key)
        }
    }
    if (!extras.custom) return
    FormRow("יחידה משלי") {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(extras.customNameField, Modifier.width(140.dp).testTag("study-custom-name"), placeholder = { Text("חצי עמוד") })
                TextField(extras.customLinesField, Modifier.width(72.dp).testTag("study-custom-lines"), placeholder = { Text("20") })
                Text("שורות")
            }
            Hint("כל יחידה היא כך וכך שורות של הספר, ברצף")
        }
    }
}

/** How the days of the plan are reported, and the minute it starts at on its first day. */
@Composable
internal fun TrackingRows(extras: PlanExtras) {
    FormRow("מעקב") {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice("ימים שהושלמו", extras.tracking == TrackingMode.POSITIVE) { extras.tracking = TrackingMode.POSITIVE }
                Choice("ימים שלא הושלמו", extras.tracking == TrackingMode.NEGATIVE) { extras.tracking = TrackingMode.NEGATIVE }
                Choice("כל יום", extras.tracking == TrackingMode.FULL) { extras.tracking = TrackingMode.FULL }
            }
            Hint(
                when (extras.tracking) {
                    TrackingMode.POSITIVE -> "מסמנים רק ימים שהושלמו; יום שלא סומן לא נחשב"
                    TrackingMode.NEGATIVE -> "מסמנים רק ימים שלא הושלמו; יום שלא סומן נחשב כהשלמה"
                    TrackingMode.FULL -> "מדווחים בכל יום: הושלם, חלקית או לא; יום שלא דווח לא נספר"
                },
            )
        }
    }
    FormRow("שעת התחלה") {
        Column {
            TextField(extras.timeField, Modifier.width(96.dp).testTag("study-time"), placeholder = { Text("06:30") })
            Hint("ביום הראשון, מהשעה הזו")
        }
    }
}

/**
 * Where a saved plan stands and what can be done: pause it, take it up again, end it, open it again, put its start off by a
 * day (once). Each is done at once and closes the editor, as a delete does, so the editor never holds a stale state.
 */
@Composable
internal fun StatusRow(
    plan: StudyPlan,
    inIsrael: Boolean,
    onClose: () -> Unit,
) {
    val store = LocalAppGraph.current.studyPlanStore
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    fun andClose(action: suspend () -> Unit): () -> Unit =
        {
            scope.launch {
                action()
                onClose()
            }
        }
    Column(Modifier.fillMaxWidth()) {
        Text(
            when (plan.state) {
                PlanState.ACTIVE -> "מצב התוכנית: פעילה"
                PlanState.PAUSED -> "מצב התוכנית: מושהית"
                PlanState.FINISHED -> "מצב התוכנית: הסתיימה"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (plan.state) {
                PlanState.ACTIVE -> OutlinedButton(andClose { store.pause(plan.id, today) }) { Text("השהה") }
                PlanState.PAUSED -> OutlinedButton(andClose { store.resume(plan.id, today) }) { Text("חדש") }
                PlanState.FINISHED -> OutlinedButton(andClose { store.finish(plan.id, null) }) { Text("פתח מחדש") }
            }
            if (plan.state != PlanState.FINISHED) OutlinedButton(andClose { store.finish(plan.id, today) }) { Text("סיים תוכנית") }
            if (canPostpone(plan, LocalDateTime.now(), inIsrael)) {
                OutlinedButton(andClose { store.postpone(plan.id) }, modifier = Modifier.testTag("study-postpone")) {
                    Text("דחה התחלה ביום")
                }
            }
        }
    }
}
