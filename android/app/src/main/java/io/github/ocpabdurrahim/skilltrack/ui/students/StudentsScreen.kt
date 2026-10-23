package io.github.ocpabdurrahim.skilltrack.ui.students

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Student
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.system.Csv
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.SearchField
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StudentAvatar
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme

private enum class StudentFilter(val label: String) { ACTIVE("Active"), ALL("All"), DUES("With dues"), ARCHIVED("Archived") }

/** Lower-case text a student can be found by: name, numbers, phones, parents. */
fun Student.searchText(): String = listOf(name, regNo, phone, guardianPhone, fatherName, email).joinToString(" ").lowercase()

@Composable
fun StudentsScreen() {
    val data = centerData()
    val nav = LocalNav.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StudentFilter.ACTIVE) }
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }

    val dueByStudent = remember(data) {
        data.countedEnrollments.groupBy { it.studentId }.mapValues { (_, list) -> list.sumOf { data.fee(it).due.coerceAtLeast(0) } }
    }
    val shown = remember(data, query, filter, courseId) {
        val q = query.trim().lowercase()
        data.students.filter { s ->
            val passes = when (filter) {
                StudentFilter.ACTIVE -> !s.archived && s.id in data.activeStudentIds
                StudentFilter.ALL -> !s.archived
                StudentFilter.DUES -> !s.archived && (dueByStudent[s.id] ?: 0) > 0
                StudentFilter.ARCHIVED -> s.archived
            }
            passes && (q.isEmpty() || s.searchText().contains(q)) &&
                (courseId == null || data.enrollmentsByStudent[s.id].orEmpty().any { it.courseId == courseId })
        }
    }

    ListScreen(
        title = "Students",
        subtitle = "${shown.size} shown · ${data.students.count { !it.archived }} in all",
        actions = {
            IconButton(onClick = { exportStudents(context, data) }) { Icon(Icons.Filled.FileDownload, contentDescription = "Export to CSV") }
        },
        fab = {
            if (data.role.office) {
                ExtendedFloatingActionButton(
                    onClick = { nav.go(Routes.admission()) },
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text("Admission") },
                    modifier = Modifier.testTag("fab_admission"),
                )
            }
        },
    ) {
        item { SearchField(query, { query = it }, "Name, ID or phone") }
        item { ChoiceChips(StudentFilter.entries.filter { data.role.seesMoney || it != StudentFilter.DUES }, filter, { filter = it }, { it.label }) }
        if (data.courses.size > 1) {
            item {
                Dropdown(
                    label = "Course",
                    options = data.courses.filter { !it.archived },
                    selected = data.courseById[courseId],
                    onSelect = { c: Course? -> courseId = c?.id },
                    optionLabel = { it.name },
                    noneLabel = "All courses",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    "🎓",
                    if (data.students.isEmpty()) "No students yet" else "Nobody matches",
                    if (data.students.isEmpty()) "Tap Admission to add your first student and put them in a batch." else "Try another search or filter.",
                )
            }
        }
        items(shown, key = { it.id }) { s ->
            val enrolled = data.enrollmentsByStudent[s.id].orEmpty()
            val current = enrolled.firstOrNull { it.status == EnrollmentStatus.ACTIVE } ?: enrolled.firstOrNull()
            val due = dueByStudent[s.id] ?: 0
            SkillCard(onClick = { nav.go(Routes.student(s.id)) }, modifier = Modifier.testTag("student_row")) {
                ListRow(
                    title = s.name,
                    subtitle = listOfNotNull(
                        s.regNo.ifBlank { null },
                        current?.let { data.batchName(it.batchId) },
                        s.phone.ifBlank { null },
                    ).joinToString(" · "),
                    leading = { StudentAvatar(s.id, s.name) },
                    trailing = {
                        if (data.role.seesMoney && due > 0) AmountColumn(money.format(due), "due", colors.danger)
                        else if (enrolled.isEmpty()) Text("No admission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        }
    }
}

private fun exportStudents(context: android.content.Context, data: CenterData) {
    val rows = data.students.map { s ->
        val e = data.enrollmentsByStudent[s.id].orEmpty()
        listOf(
            s.regNo, s.name, s.fatherName, s.motherName, s.gender.label.takeIf { s.gender.code.isNotEmpty() }, s.dob, s.phone,
            s.guardianPhone, s.email, s.address, s.education, s.institution, s.idNo, s.joinDate,
            e.joinToString("; ") { data.courseName(it.courseId) + " / " + data.batchName(it.batchId) + " (" + it.status.label + ")" },
            if (s.archived) "Archived" else "",
        )
    }
    Share.file(
        context,
        "students-${Dates.forFileName()}.csv",
        "text/csv",
        Csv.build(
            listOf("Reg No", "Name", "Father", "Mother", "Gender", "Date of birth", "Phone", "Guardian phone", "Email", "Address", "Education", "Institution", "NID/Birth reg", "Joined", "Admissions", "Archived"),
            rows,
        ),
    )
}
