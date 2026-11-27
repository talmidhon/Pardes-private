package io.github.kdroidfilter.seforimapp.features.home.widgets.study

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforimapp.core.study.PlanBookRef
import io.github.kdroidfilter.seforimapp.core.study.StudyPlan
import io.github.kdroidfilter.seforimapp.core.study.TrackingMode
import io.github.kdroidfilter.seforimlibrary.core.models.Book

// Pardes (the daily portion's upgrade): what the editor of a plan sets besides what it did (StudyPlanEditor.kt): the plan's
// name and description, which books of a category it studies, where it ends, how its days are reported, the minute it
// starts at, and a unit of the user's own. The rows that show them are StudyPlanSettings.kt's.

/** The editor's new fields, from [plan]'s as the user sets them. */
@Stable
internal class PlanExtras(
    plan: StudyPlan,
) {
    val nameField = TextFieldState(plan.name)
    val descriptionField = TextFieldState(plan.description)
    val timeField = TextFieldState(formatMinute(plan.startMinute))
    val customNameField = TextFieldState(chunkOf(plan.unitName)?.name.orEmpty())
    val customLinesField = TextFieldState(chunkOf(plan.unitName)?.lines?.toString().orEmpty())
    var tracking by mutableStateOf(plan.tracking)
    var end by mutableStateOf(plan.end)

    /** The ids of the books of a category's plan, null until the category's books are read. */
    var chosen by mutableStateOf<Set<Long>?>(null)

    /** The unit the user defines is the one studied by. */
    var custom by mutableStateOf(chunkOf(plan.unitName) != null)

    /** The unit the user defined by its fields: so many lines, with a name; null while they are not a unit. */
    fun chunk(): ChunkUnit? {
        val lines = customLinesField.text.toString().trim().toIntOrNull()?.takeIf { it in 1..MAX_CHUNK_LINES } ?: return null
        return ChunkUnit(lines, customNameField.text.toString().trim().ifEmpty { "יחידה" })
    }

    /** [plan] with what these fields hold: its [books] (a category's plan; none for a book's) and what the user typed. */
    fun applyTo(
        plan: StudyPlan,
        books: List<PlanBookRef>,
    ): StudyPlan =
        plan.copy(
            name = nameField.text.toString().trim(),
            description = descriptionField.text.toString().trim(),
            tracking = tracking,
            end = end,
            startMinute = parseMinute(timeField.text.toString()) ?: plan.startMinute,
            books = books,
        )
}

/** The most lines a unit of the user's can be: a part of a book is not the whole of it. */
internal const val MAX_CHUNK_LINES = 10_000

/** "06:30" for the minute 390 of the day. */
internal fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / MINUTES_IN_HOUR, minute % MINUTES_IN_HOUR)

/** The minute of the day [text] says ("6:30", "06:30", "18:05"); null for anything else, or a time that is not a time. */
internal fun parseMinute(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59 && parts[1].length == 2) hour * MINUTES_IN_HOUR + minute else null
}

/**
 * The books of a category's plan before the user changes any: its base books, or, when it has none, all of them. A
 * plan made when a category was studied whole or by its base books ([baseBooksOnly], and no [saved] books) keeps the books it
 * had; one with [saved] books keeps those (by id while the title is the same, else by title).
 */
internal fun initialBooks(
    books: List<Book>,
    saved: List<PlanBookRef>,
    legacy: Boolean,
    baseBooksOnly: Boolean,
): Set<Long> =
    when {
        saved.isNotEmpty() ->
            books.filter { book -> saved.any { it.id == book.id && it.title == book.title } || saved.any { it.title == book.title } }
        legacy && !baseBooksOnly -> books
        else -> books.filter { it.isBaseBook }.ifEmpty { books }
    }.mapTo(LinkedHashSet()) { it.id }

private const val MINUTES_IN_HOUR = 60
