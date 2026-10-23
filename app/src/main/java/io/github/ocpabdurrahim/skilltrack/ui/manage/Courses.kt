package io.github.ocpabdurrahim.skilltrack.ui.manage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountInput
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.SwitchRow
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput

@Composable
fun CoursesScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    var editing by remember { mutableStateOf<Course?>(null) }
    val canEdit = data.role.manages

    ListScreen(
        title = "Courses",
        onBack = nav::back,
        fab = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = { editing = Course() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New course") },
                    modifier = Modifier.testTag("fab_course"),
                )
            }
        },
    ) {
        if (data.courses.isEmpty()) {
            item { EmptyState("📚", "No courses yet", "Add what you teach, e.g. \"Computer Office Application\", \"Graphic Design\", \"Web Development\", with the course fee.") }
        }
        val active = data.courses.filter { !it.archived }
        val archived = data.courses.filter { it.archived }
        items(active, key = { it.id }) { c -> CourseRow(c, money.format(c.fee)) { if (canEdit) editing = c } }
        if (archived.isNotEmpty()) {
            item { SectionHeader("Archived") }
            items(archived, key = { it.id }) { c -> CourseRow(c, money.format(c.fee)) { if (canEdit) editing = c } }
        }
    }

    editing?.let { course ->
        CourseDialog(
            course = course,
            isNew = data.courseById[course.id] == null,
            onDismiss = { editing = null },
            onSave = {
                app.store.put(Cols.courses, it)
                editing = null
                notify("${it.name} saved")
            },
        )
    }
}

@Composable
private fun CourseRow(c: Course, fee: String, onClick: () -> Unit) {
    val data = centerData()
    val batches = data.batches.count { it.courseId == c.id }
    val students = data.enrollments.count { it.courseId == c.id }
    SkillCard(onClick = onClick, modifier = Modifier.testTag("course_row")) {
        ListRow(
            title = c.name,
            subtitle = listOfNotNull(c.code.ifBlank { null }, c.duration.ifBlank { null }, "$batches batches", "$students admissions").joinToString(" · "),
            trailing = { AmountColumn(fee, "fee", MaterialTheme.colorScheme.primary) },
        )
    }
}

@Composable
private fun CourseDialog(course: Course, isNew: Boolean, onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember { mutableStateOf(course.name) }
    var code by remember { mutableStateOf(course.code) }
    var fee by remember { mutableStateOf(if (course.fee == 0L) "" else Money.toInput(course.fee)) }
    var duration by remember { mutableStateOf(course.duration) }
    var description by remember { mutableStateOf(course.description) }
    var archived by remember { mutableStateOf(course.archived) }
    FormDialog(
        title = if (isNew) "New course" else "Edit course",
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank() && Money.parse(fee) != null,
        onSave = {
            onSave(
                course.copy(
                    name = name.trim(), code = code.trim().uppercase(), fee = Money.parse(fee) ?: 0L,
                    duration = duration.trim(), description = description.trim(), archived = archived,
                ),
            )
        },
    ) {
        TextInput(name, { name = it }, "Course name *", capitalization = KeyboardCapitalization.Words, tag = "course_name")
        FieldPair(
            { m -> TextInput(code, { code = it.uppercase() }, "Short code", m, placeholder = "MSO", maxLength = 8, tag = "course_code") },
            { m -> TextInput(duration, { duration = it }, "Duration", m, placeholder = "3 months") },
        )
        AmountInput(fee, { fee = it }, "Course fee *", tag = "course_fee")
        TextInput(description, { description = it }, "What it covers", singleLine = false, minLines = 2, maxLength = 1000)
        if (!isNew) SwitchRow("Archived", archived, { archived = it }, subtitle = "Hidden from new batches; history stays.")
    }
}
