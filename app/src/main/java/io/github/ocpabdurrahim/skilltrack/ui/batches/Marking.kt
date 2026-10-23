package io.github.ocpabdurrahim.skilltrack.ui.batches

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Attendance
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Exam
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Mark
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.NumberInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.LocalDate

@Composable
private fun markColor(mark: Mark): Color = when (mark) {
    Mark.PRESENT -> SkillTheme.colors.success
    Mark.ABSENT -> SkillTheme.colors.danger
    Mark.LATE -> SkillTheme.colors.warning
    Mark.EXCUSED -> SkillTheme.colors.info
}

@Composable
fun AttendanceScreen(batchId: String, initialDate: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val batch = data.batchById[batchId]
    var date by rememberSaveable { mutableStateOf(initialDate ?: data.today) }
    val marks = remember { mutableStateMapOf<String, Mark>() }
    var loadedFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (batch == null) {
        Screen("Attendance", onBack = nav::back) { EmptyState("🔍", "Batch not found", "It may have been deleted on another device.") }
        return
    }
    val recordId = Ids.attendance(batchId, date)
    val record = data.attendanceByBatch[batchId].orEmpty().firstOrNull { it.id == recordId }
    val roll = Academics.rollFor(data.enrollmentsByBatch[batchId].orEmpty(), date)
    val day = Dates.parse(date) ?: LocalDate.now()
    val warning = Academics.dayWarning(batch, day)
    val future = date > data.today

    // Start from what was saved for this day, or everyone present for a new day.
    LaunchedEffect(date, record?.id) {
        if (loadedFor == date) return@LaunchedEffect
        marks.clear()
        roll.forEach { e -> marks[e.id] = record?.marks?.get(e.id) ?: Mark.PRESENT }
        loadedFor = date
    }

    val counts = Mark.entries.associateWith { m -> roll.count { marks[it.id] == m } }

    Screen(
        title = "Attendance",
        subtitle = "${batch.name} · ${data.courseName(batch.courseId)}",
        onBack = nav::back,
        actions = {
            if (record != null && data.role.manages) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete this day's attendance") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DateInput(date, { date = it }, "Class day", tag = "attendance_date")
                warning?.let { Text("⚠ $it Marking it anyway counts as an extra class.", color = SkillTheme.colors.warning, style = MaterialTheme.typography.bodySmall) }
                if (future) Text("This day is in the future.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(
                    Mark.entries.joinToString("  ·  ") { "${it.label} ${counts[it] ?: 0}" } +
                        (record?.takenBy?.takeIf { it.isNotBlank() }?.let { "\nSaved by $it" } ?: if (record == null) "\nNot saved yet" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("attendance_counts"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { roll.forEach { marks[it.id] = Mark.PRESENT } }) { Text("All present") }
                    OutlinedButton(onClick = { roll.forEach { marks[it.id] = Mark.ABSENT } }) { Text("All absent") }
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (roll.isEmpty()) item { EmptyState("👥", "Nobody to mark", "No active student was admitted to this batch by this day.") }
                items(roll, key = { it.id }) { e ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.testTag("mark_row")) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                "${e.rollNo}. ${data.studentName(e.studentId)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Mark.entries.forEach { m ->
                                    val selected = marks[e.id] == m
                                    FilterChip(
                                        selected = selected,
                                        onClick = { marks[e.id] = m },
                                        label = { Text(m.code, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = markColor(m).copy(alpha = 0.22f),
                                            selectedLabelColor = markColor(m),
                                        ),
                                        modifier = Modifier.testTag("mark_${m.code}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                enabled = roll.isNotEmpty() && data.role.academic,
                onClick = {
                    Log.i("SkillTrack", "Saving attendance for $batchId on $date")
                    val saved = Attendance(
                        id = recordId, batchId = batchId, date = date,
                        marks = roll.associate { it.id to (marks[it.id] ?: Mark.PRESENT) },
                        takenBy = data.me?.displayName.orEmpty(), note = record?.note.orEmpty(),
                    )
                    app.store.put(Cols.attendance, saved)
                    notify("Attendance saved for ${Dates.relative(date)}")
                    nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
                    .testTag("save_attendance"),
            ) { Text(if (record == null) "Save attendance" else "Update attendance") }
        }
    }
    if (confirmDelete && record != null) {
        ConfirmDialog(
            title = "Delete this day's attendance?",
            text = "The class on ${Dates.full(date)} will no longer count for anyone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.attendance, record.id)
                confirmDelete = false
                nav.back()
            },
        )
    }
}

@Composable
fun ExamScreen(batchId: String, examId: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val batch = data.batchById[batchId]
    val existing = examId?.let { id -> data.exams.firstOrNull { it.id == id } }
    val newId = rememberSaveable { Ids.new() }
    val base = existing ?: Exam(id = newId, batchId = batchId, date = data.today)

    var name by rememberSaveable { mutableStateOf(base.name) }
    var date by rememberSaveable { mutableStateOf(base.date) }
    var full by rememberSaveable { mutableStateOf(Academics.marks(base.fullMarks)) }
    var pass by rememberSaveable { mutableStateOf(Academics.marks(base.passMarks)) }
    val entries = remember { mutableStateMapOf<String, String>().apply { base.marks.forEach { (k, v) -> put(k, Academics.marks(v)) } } }
    var confirmDelete by remember { mutableStateOf(false) }

    if (batch == null) {
        Screen("Exam", onBack = nav::back) { EmptyState("🔍", "Batch not found", "It may have been deleted on another device.") }
        return
    }
    val roll = Academics.rollFor(data.enrollmentsByBatch[batchId].orEmpty(), "9999-12-31")
    val fullValue = full.toDoubleOrNull() ?: 0.0
    val passValue = pass.toDoubleOrNull() ?: 0.0
    val tooHigh = entries.filterValues { (it.toDoubleOrNull() ?: 0.0) > fullValue }.keys
    val problem = when {
        name.isBlank() -> "Give the exam a name."
        fullValue <= 0 -> "Enter the full marks."
        passValue > fullValue -> "The pass mark is more than the full marks."
        tooHigh.isNotEmpty() -> "${tooHigh.size} mark${if (tooHigh.size == 1) " is" else "s are"} above the full marks."
        else -> null
    }
    val canEdit = data.role.academic

    Screen(title = if (existing == null) "New exam" else name.ifBlank { "Exam" }, subtitle = batch.name, onBack = nav::back, actions = {
        if (existing != null && canEdit) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete exam") }
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { TextInput(name, { name = it }, "Exam name *", placeholder = "e.g. Final - Practical", tag = "exam_name") }
                item { DateInput(date, { date = it }, "Date") }
                item {
                    FieldPair(
                        { m -> NumberInput(full, { full = it }, "Full marks", m, decimals = 1, tag = "exam_full") },
                        { m -> NumberInput(pass, { pass = it }, "Pass marks", m, decimals = 1, tag = "exam_pass") },
                    )
                }
                item { Text("Marks (leave blank if not taken yet)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                if (roll.isEmpty()) item { Text("No students in this batch yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(roll, key = { it.id }) { e ->
                    val value = entries[e.id].orEmpty()
                    val v = value.toDoubleOrNull()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.rollNo}. ${data.studentName(e.studentId)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (v != null) {
                                Text(
                                    if (v < passValue) "Below pass" else "%.0f%%".format(if (fullValue > 0) v * 100 / fullValue else 0.0),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (v < passValue || v > fullValue) SkillTheme.colors.danger else SkillTheme.colors.success,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        NumberInput(
                            value,
                            { entries[e.id] = it },
                            "Marks",
                            Modifier.width(110.dp),
                            decimals = 1,
                            tag = "marks_input",
                            isError = v != null && v > fullValue,
                        )
                    }
                }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Button(
                enabled = problem == null && canEdit,
                onClick = {
                    val exam = base.copy(
                        name = name.trim(), date = date, fullMarks = fullValue, passMarks = passValue,
                        marks = entries.mapNotNull { (k, v) -> v.toDoubleOrNull()?.let { k to it } }.toMap(),
                    )
                    app.store.put(Cols.exams, exam)
                    notify("${exam.name} saved")
                    nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
                    .testTag("save_exam"),
            ) { Text("Save exam and marks") }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete ${existing.name}?",
            text = "Its marks are deleted too, and results are worked out without it.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.exams, existing.id)
                confirmDelete = false
                nav.back()
            },
        )
    }
}
