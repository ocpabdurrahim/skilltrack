package io.github.ocpabdurrahim.skilltrack.ui.batches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.BatchStatus
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.Weekday
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.print.Printer
import io.github.ocpabdurrahim.skilltrack.print.Printouts
import io.github.ocpabdurrahim.skilltrack.system.findActivity
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.NumberInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.components.StudentAvatar
import io.github.ocpabdurrahim.skilltrack.ui.components.SwitchRow
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.printouts
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import kotlinx.coroutines.launch

@Composable
fun batchStatusColor(status: BatchStatus): Color = when (status) {
    BatchStatus.RUNNING -> SkillTheme.colors.success
    BatchStatus.UPCOMING -> SkillTheme.colors.info
    BatchStatus.COMPLETED -> MaterialTheme.colorScheme.outline
    BatchStatus.CANCELLED -> SkillTheme.colors.danger
}

private enum class BatchFilter(val label: String) { CURRENT("Running & upcoming"), COMPLETED("Completed"), ALL("All") }

@Composable
fun BatchesScreen() {
    val data = centerData()
    val nav = LocalNav.current
    var filter by rememberSaveable { mutableStateOf(BatchFilter.CURRENT) }
    val mine = data.me?.instructorId
    val shown = remember(data, filter) {
        data.batches.filter { b ->
            val s = data.batchStatus(b)
            when (filter) {
                BatchFilter.CURRENT -> s == BatchStatus.RUNNING || s == BatchStatus.UPCOMING
                BatchFilter.COMPLETED -> s == BatchStatus.COMPLETED || s == BatchStatus.CANCELLED
                BatchFilter.ALL -> true
            }
        }.sortedWith(compareBy({ mine != null && it.instructorId != mine }, { data.batchStatus(it).ordinal }, { it.name }))
    }

    ListScreen(
        title = "Batches",
        subtitle = "${data.runningBatches.size} running",
        fab = {
            if (data.role.manages) {
                ExtendedFloatingActionButton(
                    onClick = { nav.go(Routes.batchEdit()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New batch") },
                    modifier = Modifier.testTag("fab_batch"),
                )
            }
        },
    ) {
        item { ChoiceChips(BatchFilter.entries, filter, { filter = it }, { it.label }) }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    "🗓️",
                    if (data.batches.isEmpty()) "No batches yet" else "No batches here",
                    if (data.courses.isEmpty()) "Add a course first (More → Courses), then create its batches." else "A batch is a group of students who take a course together, on set days and times.",
                )
            }
        }
        items(shown, key = { it.id }) { b -> BatchCard(b) { nav.go(Routes.batch(b.id)) } }
    }
}

@Composable
private fun BatchCard(b: Batch, onClick: () -> Unit) {
    val data = centerData()
    val status = data.batchStatus(b)
    val seats = data.seatsTaken(b.id)
    SkillCard(onClick = onClick, modifier = Modifier.testTag("batch_row")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.titleMedium)
                Text(data.courseName(b.courseId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(status.label, batchStatusColor(status))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(
                b.weekdays.joinToString(" ") { it.short }.ifBlank { null },
                b.time.ifBlank { null },
                b.instructorId?.let { data.instructorById[it]?.name },
                if (b.capacity > 0) "$seats/${b.capacity} seats" else "$seats students",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BatchDetailScreen(id: String) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SkillTheme.colors
    val batch = data.batchById[id]
    var tab by rememberSaveable { mutableIntStateOf(0) }

    if (batch == null) {
        Screen("Batch", onBack = nav::back) { EmptyState("🔍", "Batch not found", "It may have been deleted on another device.") }
        return
    }
    val course = data.courseById[batch.courseId]
    val roll = data.roll(id)
    val sessions = data.attendanceByBatch[id].orEmpty()
    val exams = data.examsByBatch[id].orEmpty()
    val results = data.results(id)
    val status = data.batchStatus(batch)

    fun printCards() {
        val activity = context.findActivity() ?: return
        scope.launch {
            val cards = roll.filter { it.status != EnrollmentStatus.DROPPED }.mapNotNull { e ->
                val s = data.studentById[e.studentId] ?: return@mapNotNull null
                Printouts.Card(s, course, batch, app.photos.dataUrl(s.id))
            }
            app.appLock.skipNextLock()
            Printer.print(activity, "ID cards ${batch.name}", money.printouts(data.center).idCards(cards))
        }
    }

    fun printResults() {
        val activity = context.findActivity() ?: return
        val rows = roll.filter { it.status != EnrollmentStatus.DROPPED }.map { Printouts.ResultRow(it, data.studentById[it.studentId], results[it.id]) }
        app.appLock.skipNextLock()
        Printer.print(activity, "Result sheet ${batch.name}", money.printouts(data.center).resultSheet(batch, course, exams, rows), landscape = exams.size > 4)
    }

    ListScreen(
        title = batch.name,
        subtitle = course?.name,
        onBack = nav::back,
        actions = {
            if (data.role.manages) IconButton(onClick = { nav.go(Routes.batchEdit(id)) }, modifier = Modifier.testTag("edit_batch")) { Icon(Icons.Filled.Edit, contentDescription = "Edit batch") }
        },
    ) {
        item {
            SkillCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Dates.full(batch.startDate)} – ${Dates.full(batch.endDate, "open")}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    StatusChip(status.label, batchStatusColor(status))
                }
                Spacer(Modifier.height(6.dp))
                KeyValue("Class days", batch.weekdays.joinToString(", ") { it.short })
                KeyValue("Time", batch.time)
                KeyValue("Room", batch.room)
                KeyValue("Instructor", batch.instructorId?.let { data.instructorById[it]?.name }.orEmpty())
                KeyValue("Students", if (batch.capacity > 0) "${data.seatsTaken(id)} of ${batch.capacity}" else data.seatsTaken(id).toString())
                if (batch.note.isNotBlank()) KeyValue("Note", batch.note)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.role.academic) AssistChip(onClick = { nav.go(Routes.attendance(id, data.today)) }, label = { Text("Mark attendance") }, leadingIcon = { Icon(Icons.Filled.EventAvailable, null) }, modifier = Modifier.testTag("batch_attendance"))
                    if (data.role.office) AssistChip(onClick = { nav.go(Routes.admission(batchId = id)) }, label = { Text("Admit student") }, leadingIcon = { Icon(Icons.Filled.PersonAdd, null) })
                    AssistChip(onClick = ::printCards, label = { Text("ID cards") }, leadingIcon = { Icon(Icons.Filled.Badge, null) })
                }
            }
        }
        item {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                listOf("Students", "Attendance", "Exams", "Results").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }, modifier = Modifier.testTag("batch_tab_$i"))
                }
            }
        }
        when (tab) {
            0 -> {
                if (roll.isEmpty()) item { EmptyState("👥", "No students yet", "Admit students into this batch from here or from Students.") }
                items(roll, key = { it.id }) { e ->
                    val s = data.studentById[e.studentId]
                    val att = data.attendanceStat(e)
                    SkillCard(onClick = { nav.go(Routes.enrollment(e.id)) }) {
                        ListRow(
                            title = "${e.rollNo}. ${s?.name ?: "Unknown"}",
                            subtitle = listOfNotNull(
                                e.status.label.takeIf { e.status != EnrollmentStatus.ACTIVE },
                                att.rate?.let { "Attendance %.0f%%".format(it * 100) },
                                results[e.id]?.let { "Grade ${it.grade.letter}" },
                            ).joinToString(" · "),
                            leading = { StudentAvatar(e.studentId, s?.name ?: "?", size = 36.dp) },
                            trailing = {
                                if (data.role.seesMoney) {
                                    val fee = data.fee(e)
                                    if (fee.due > 0) AmountColumn(money.format(fee.due), if (fee.overdue > 0) "overdue" else "due", if (fee.overdue > 0) colors.danger else colors.warning)
                                    else Text("Paid", color = colors.success, style = MaterialTheme.typography.labelMedium)
                                }
                            },
                        )
                    }
                }
            }
            1 -> {
                item {
                    val active = roll.filter { it.status != EnrollmentStatus.DROPPED }
                    val stats = active.map { data.attendanceStat(it) }
                    val counted = stats.sumOf { it.counted }
                    val attended = stats.sumOf { it.attended }
                    SkillCard {
                        Text("${sessions.size} classes marked", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (counted > 0) "Average attendance %.0f%%".format(attended * 100f / counted) else "Nothing marked yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val low = active.mapNotNull { e -> data.attendanceStat(e).rate?.let { e to it } }.filter { it.second < 0.75f }.sortedBy { it.second }
                        if (low.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Below 75%", style = MaterialTheme.typography.labelLarge, color = colors.danger)
                            low.forEach { (e, rate) ->
                                Text("${e.rollNo}. ${data.studentName(e.studentId)} — %.0f%%".format(rate * 100), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                items(sessions, key = { it.id }) { a ->
                    val marked = a.marks.size
                    val present = a.marks.values.count { it.code == "P" || it.code == "L" }
                    SkillCard(onClick = { nav.go(Routes.attendance(id, a.date)) }) {
                        ListRow(
                            title = Dates.relative(a.date),
                            subtitle = "Present $present of $marked" + (a.takenBy.takeIf { it.isNotBlank() }?.let { " · by $it" } ?: ""),
                            trailing = { StatusChip("%.0f%%".format(if (marked > 0) present * 100f / marked else 0f), colors.info) },
                        )
                    }
                }
            }
            2 -> {
                if (data.role.academic) {
                    item {
                        Button(onClick = { nav.go(Routes.exam(id)) }, modifier = Modifier.testTag("add_exam")) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add exam")
                        }
                    }
                }
                if (exams.isEmpty()) item { EmptyState("📝", "No exams yet", "Add each exam (e.g. Written, Practical) with its full and pass marks, then enter the marks.") }
                items(exams, key = { it.id }) { ex ->
                    SkillCard(onClick = { nav.go(Routes.exam(id, ex.id)) }) {
                        ListRow(
                            title = ex.name,
                            subtitle = "${Dates.full(ex.date)} · full ${Academics.marks(ex.fullMarks)}, pass ${Academics.marks(ex.passMarks)} · ${ex.marks.size} marked",
                        )
                    }
                }
            }
            else -> {
                item {
                    TextButton(onClick = ::printResults, enabled = exams.isNotEmpty(), modifier = Modifier.testTag("print_results")) {
                        Icon(Icons.Filled.Print, null)
                        Text("  Print result sheet")
                    }
                }
                val ranked = roll.filter { it.status != EnrollmentStatus.DROPPED }
                    .sortedWith(compareBy({ results[it.id]?.position ?: Int.MAX_VALUE }, { it.rollNo }))
                if (exams.isEmpty()) item { EmptyState("🏅", "No results yet", "Results appear once exams have marks.") }
                else items(ranked, key = { "r" + it.id }) { e ->
                    val r = results[e.id]
                    SkillCard(onClick = { nav.go(Routes.enrollment(e.id)) }) {
                        ListRow(
                            title = "${e.rollNo}. ${data.studentName(e.studentId)}",
                            subtitle = r?.let { "%s / %s · %.1f%%".format(Academics.marks(it.obtained), Academics.marks(it.full), it.percent) } ?: "No marks yet",
                            trailing = {
                                if (r != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(r.grade.letter, fontWeight = FontWeight.Bold, color = if (r.failed) colors.danger else colors.success)
                                        Text(r.position?.let { "#$it" } ?: "—", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "MSO-26-B03": the course code, the year and the next batch number for that course. */
private fun suggestName(course: Course?, existing: List<Batch>, startDate: String): String {
    val code = course?.code?.ifBlank { null } ?: course?.name?.split(' ')?.filter { it.isNotBlank() }?.joinToString("") { it.take(1) }?.uppercase() ?: "B"
    val year = (Dates.parse(startDate)?.year ?: java.time.LocalDate.now().year) % 100
    val n = existing.count { it.courseId == course?.id } + 1
    return "$code-%02d-B%02d".format(year, n)
}

@Composable
fun BatchEditScreen(id: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val existing = id?.let { data.batchById[it] }
    val newId = rememberSaveable { Ids.new() }
    val base = existing ?: Batch(id = newId, startDate = data.today)

    var courseId by rememberSaveable { mutableStateOf(base.courseId.ifBlank { data.courses.firstOrNull { !it.archived }?.id.orEmpty() }) }
    var name by rememberSaveable { mutableStateOf(base.name) }
    var instructorId by rememberSaveable { mutableStateOf(base.instructorId) }
    var start by rememberSaveable { mutableStateOf(base.startDate) }
    var end by rememberSaveable { mutableStateOf(base.endDate) }
    var days by rememberSaveable { mutableStateOf(base.days.joinToString(",")) }
    var time by rememberSaveable { mutableStateOf(base.time) }
    var room by rememberSaveable { mutableStateOf(base.room) }
    var capacity by rememberSaveable { mutableStateOf(if (base.capacity > 0) base.capacity.toString() else "") }
    var cancelled by rememberSaveable { mutableStateOf(base.cancelled) }
    var note by rememberSaveable { mutableStateOf(base.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dayList = days.split(',').filter { it.isNotBlank() }
    val course = data.courseById[courseId]
    val hasStudents = existing != null && data.enrollmentsByBatch[existing.id].orEmpty().isNotEmpty()
    val problem = when {
        course == null -> if (data.courses.isEmpty()) "Add a course first (More → Courses)." else "Choose a course."
        name.isBlank() -> "Give the batch a name."
        start.isBlank() -> "Choose the start date."
        end.isNotBlank() && end < start -> "The end date is before the start."
        else -> null
    }

    Screen(title = if (existing == null) "New batch" else "Edit batch", onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Dropdown(
                label = "Course *",
                options = data.courses.filter { !it.archived || it.id == courseId },
                selected = course,
                onSelect = { c: Course? ->
                    courseId = c?.id.orEmpty()
                    if (existing == null) name = suggestName(c, data.batches, start)
                },
                optionLabel = { it.name },
                tag = "batch_course",
            )
            TextInput(name, { name = it.uppercase() }, "Batch name *", placeholder = suggestName(course, data.batches, start), tag = "batch_name")
            if (name.isBlank() && course != null) TextButton(onClick = { name = suggestName(course, data.batches, start) }) { Text("Use ${suggestName(course, data.batches, start)}") }
            Dropdown(
                label = "Instructor",
                options = data.instructors.filter { !it.archived || it.id == instructorId },
                selected = instructorId?.let { data.instructorById[it] },
                onSelect = { i: Instructor? -> instructorId = i?.id },
                optionLabel = { it.name },
                noneLabel = "Not set",
            )
            FieldPair(
                { m -> DateInput(start, { start = it }, "Starts *", m) },
                { m -> DateInput(end, { end = it }, "Ends", m, clearable = true) },
            )
            FieldLabel("Class days")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Weekday.entries.forEach { d ->
                    val on = d.code in dayList
                    FilterChip(
                        selected = on,
                        onClick = { days = (if (on) dayList - d.code else dayList + d.code).joinToString(",") },
                        label = { Text(d.short) },
                        modifier = Modifier.testTag("day_${d.code}"),
                    )
                }
            }
            TextButton(onClick = { days = "SAT,MON,WED" }) { Text("Sat · Mon · Wed") }
            TextButton(onClick = { days = "SUN,TUE,THU" }) { Text("Sun · Tue · Thu") }
            FieldPair(
                { m -> TextInput(time, { time = it }, "Time", m, placeholder = "4:00 PM - 6:00 PM") },
                { m -> TextInput(room, { room = it }, "Room / lab", m) },
            )
            NumberInput(capacity, { capacity = it }, "Seats (blank = no limit)")
            TextInput(note, { note = it }, "Note", singleLine = false)
            if (existing != null) SwitchRow("Cancelled", cancelled, { cancelled = it }, subtitle = "The batch will not run; it stays in the records.")
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = problem == null,
                onClick = {
                    val b = base.copy(
                        name = name.trim(), courseId = courseId, instructorId = instructorId, startDate = start, endDate = end,
                        days = Weekday.entries.map { it.code }.filter { it in dayList }, time = time.trim(), room = room.trim(),
                        capacity = capacity.toIntOrNull() ?: 0, cancelled = cancelled, note = note.trim(),
                    )
                    app.store.put(Cols.batches, b)
                    notify("Batch ${b.name} saved")
                    if (existing == null) nav.replace(Routes.batch(b.id)) else nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_batch"),
            ) { Text("Save") }
            if (existing != null && !hasStudents) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete batch", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete ${existing.name}?",
            text = "It has no students. Its attendance and exams, if any, stay hidden with it.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.batches, existing.id)
                confirmDelete = false
                nav.tab(Routes.BATCHES)
            },
        )
    }
}
