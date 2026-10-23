package io.github.ocpabdurrahim.skilltrack.ui.students

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Gender
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Student
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.print.Printer
import io.github.ocpabdurrahim.skilltrack.print.Printouts
import io.github.ocpabdurrahim.skilltrack.system.Images
import io.github.ocpabdurrahim.skilltrack.system.Share
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
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.FigureRow
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.components.StudentAvatar
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.printouts
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import kotlinx.coroutines.launch

/** Colour for an admission's status chip. */
@Composable
fun statusColor(status: EnrollmentStatus) = when (status) {
    EnrollmentStatus.ACTIVE -> SkillTheme.colors.info
    EnrollmentStatus.COMPLETED -> SkillTheme.colors.success
    EnrollmentStatus.DROPPED -> MaterialTheme.colorScheme.outline
}

@Composable
fun StudentDetailScreen(id: String) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SkillTheme.colors
    val student = data.studentById[id]
    var confirmDelete by remember { mutableStateOf(false) }

    if (student == null) {
        Screen("Student", onBack = nav::back) { EmptyState("🔍", "Student not found", "They may have been deleted on another device.") }
        return
    }
    val admissions = data.enrollmentsByStudent[id].orEmpty()
    val payments = data.paymentsByStudent[id].orEmpty()
    val office = data.role.office

    fun printProfile() {
        val activity = context.findActivity() ?: return
        scope.launch {
            val photo = app.photos.dataUrl(id)
            val html = money.printouts(data.center).profile(
                student, photo,
                admissions.map { e ->
                    Printouts.ProfileAdmission(e, data.courseById[e.courseId], data.batchById[e.batchId], if (data.role.seesMoney) data.fee(e) else null)
                },
            )
            app.appLock.skipNextLock()
            Printer.print(activity, "Admission form ${student.regNo}", html)
        }
    }

    fun printCard() {
        val activity = context.findActivity() ?: return
        scope.launch {
            val e = admissions.firstOrNull { it.status == EnrollmentStatus.ACTIVE } ?: admissions.firstOrNull()
            val card = Printouts.Card(student, e?.let { data.courseById[it.courseId] }, e?.let { data.batchById[it.batchId] }, app.photos.dataUrl(id))
            app.appLock.skipNextLock()
            Printer.print(activity, "ID card ${student.regNo}", money.printouts(data.center).idCards(listOf(card)))
        }
    }

    ListScreen(
        title = student.name,
        subtitle = student.regNo,
        onBack = nav::back,
        actions = {
            if (office) IconButton(onClick = { nav.go(Routes.studentEdit(id)) }, modifier = Modifier.testTag("edit_student")) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
        },
    ) {
        item {
            SkillCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(id, student.name, size = 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(student.name, style = MaterialTheme.typography.titleLarge)
                        Text(listOf(student.regNo, student.phone).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (student.archived) StatusChip("Archived", MaterialTheme.colorScheme.outline)
                    }
                }
                if (student.phone.isNotBlank() || student.guardianPhone.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val phone = student.phone.ifBlank { student.guardianPhone }
                        OutlinedButton(onClick = { Share.call(context, phone) }) {
                            Icon(Icons.Filled.Call, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Call")
                        }
                        OutlinedButton(onClick = { Share.whatsApp(context, phone, "Assalamu alaikum ${student.name}, ") }) {
                            Icon(Icons.Filled.Chat, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("WhatsApp")
                        }
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (office) AssistChip(onClick = { nav.go(Routes.admission(studentId = id)) }, label = { Text("New admission") }, leadingIcon = { Icon(Icons.Filled.PersonAdd, null) }, modifier = Modifier.testTag("student_admit"))
                if (office && admissions.isNotEmpty()) AssistChip(onClick = { nav.go(Routes.collect(studentId = id)) }, label = { Text("Collect fee") }, leadingIcon = { Icon(Icons.Filled.Payments, null) }, modifier = Modifier.testTag("student_collect"))
                AssistChip(onClick = ::printCard, label = { Text("ID card") }, leadingIcon = { Icon(Icons.Filled.Badge, null) })
                AssistChip(onClick = ::printProfile, label = { Text("Admission form") }, leadingIcon = { Icon(Icons.Filled.Print, null) })
            }
        }

        item { SectionHeader("Admissions") }
        if (admissions.isEmpty()) item { SkillCard { Text("Not in any batch yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(admissions, key = { it.id }) { e ->
            val fee = data.fee(e)
            val att = data.attendanceStat(e)
            val result = data.result(e)
            SkillCard(onClick = { nav.go(Routes.enrollment(e.id)) }, modifier = Modifier.testTag("admission_row")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(data.courseName(e.courseId), style = MaterialTheme.typography.titleMedium)
                        Text("${data.batchName(e.batchId)} · Roll ${e.rollNo} · since ${Dates.full(e.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(e.status.label, statusColor(e.status))
                }
                Spacer(Modifier.height(10.dp))
                val figures = buildList {
                    if (data.role.seesMoney) {
                        add(Triple("Paid", money.format(fee.paid), colors.success))
                        add(Triple("Due", money.format(fee.due.coerceAtLeast(0)), if (fee.due > 0) colors.danger else colors.success))
                    }
                    add(Triple("Attendance", att.rate?.let { "%.0f%%".format(it * 100) } ?: "—", MaterialTheme.colorScheme.onSurface))
                    add(Triple("Grade", result?.grade?.letter ?: "—", MaterialTheme.colorScheme.onSurface))
                }
                FigureRow(*figures.toTypedArray())
            }
        }

        if (data.role.seesMoney && payments.isNotEmpty()) {
            item { SectionHeader("Payments") }
            items(payments, key = { "p" + it.id }) { p ->
                SkillCard(onClick = { nav.go(Routes.receipt(p.id)) }) {
                    ListRow(
                        title = p.receiptNo.ifBlank { "Receipt" },
                        subtitle = "${Dates.full(p.date)} · ${p.method.label} · ${data.batchName(data.enrollmentById[p.enrollmentId]?.batchId)}",
                        trailing = { AmountColumn(money.format(p.amount), null, colors.success) },
                    )
                }
            }
        }

        item { SectionHeader("Details") }
        item {
            SkillCard {
                KeyValue("Father's name", student.fatherName)
                KeyValue("Mother's name", student.motherName)
                KeyValue("Gender", if (student.gender == Gender.NONE) "" else student.gender.label)
                KeyValue("Date of birth", Dates.full(student.dob, "") + (Dates.age(student.dob)?.let { " ($it years)" } ?: ""))
                KeyValue("Guardian's phone", student.guardianPhone)
                KeyValue("Email", student.email)
                KeyValue("Address", student.address)
                KeyValue("Education", listOf(student.education, student.institution).filter { it.isNotBlank() }.joinToString(", "))
                KeyValue("NID / birth reg.", student.idNo)
                KeyValue("Joined", Dates.full(student.joinDate, ""))
                if (student.note.isNotBlank()) KeyValue("Note", student.note)
            }
        }
        if (office) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        app.store.put(Cols.students, student.copy(archived = !student.archived))
                        notify(if (student.archived) "${student.name} restored" else "${student.name} archived")
                    }) { Text(if (student.archived) "Restore" else "Archive") }
                    if (admissions.isEmpty() && payments.isEmpty()) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${student.name}?",
            text = "This removes the student record on every device. Students with admissions or payments can only be archived.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.students, id)
                app.photos.remove(id)
                confirmDelete = false
                notify("Student deleted")
                nav.back()
            },
        )
    }
}

@Composable
fun StudentEditScreen(id: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val existing = id?.let { data.studentById[it] }
    // Kept across recompositions, so a photo chosen before saving belongs to this student.
    val newId = rememberSaveable { Ids.new() }
    val base = existing ?: Student(id = newId, joinDate = data.today)

    var name by rememberSaveable { mutableStateOf(base.name) }
    var father by rememberSaveable { mutableStateOf(base.fatherName) }
    var mother by rememberSaveable { mutableStateOf(base.motherName) }
    var gender by rememberSaveable { mutableStateOf(base.gender) }
    var dob by rememberSaveable { mutableStateOf(base.dob) }
    var phone by rememberSaveable { mutableStateOf(base.phone) }
    var guardianPhone by rememberSaveable { mutableStateOf(base.guardianPhone) }
    var email by rememberSaveable { mutableStateOf(base.email) }
    var address by rememberSaveable { mutableStateOf(base.address) }
    var education by rememberSaveable { mutableStateOf(base.education) }
    var institution by rememberSaveable { mutableStateOf(base.institution) }
    var idNo by rememberSaveable { mutableStateOf(base.idNo) }
    var joinDate by rememberSaveable { mutableStateOf(base.joinDate) }
    var note by rememberSaveable { mutableStateOf(base.note) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { Images.studentPhoto(context, uri) }
                .onSuccess { app.photos.save(base.id, it); photoError = null }
                .onFailure { photoError = it.message }
        }
    }

    fun build(): Student = base.copy(
        name = name.trim(), fatherName = father.trim(), motherName = mother.trim(), gender = gender, dob = dob,
        phone = phone.trim(), guardianPhone = guardianPhone.trim(), email = email.trim(), address = address.trim(),
        education = education.trim(), institution = institution.trim(), idNo = idNo.trim(), joinDate = joinDate,
        note = note.trim(), regNo = base.regNo.ifBlank { data.nextRegNo(joinDate.ifBlank { data.today }) },
    )

    Screen(title = if (existing == null) "New student" else "Edit student", onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudentAvatar(base.id, name.ifBlank { "?" }, size = 64.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    TextButton(onClick = {
                        app.appLock.skipNextLock()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choose photo") }
                    photoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(base.regNo.ifBlank { "ID is given on save" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            NameInput(name, { name = it }, "Full name *", tag = "student_name")
            FieldPair(
                { m -> NameInput(father, { father = it }, "Father's name", m) },
                { m -> NameInput(mother, { mother = it }, "Mother's name", m) },
            )
            FieldLabel("Gender")
            ChoiceChips(Gender.entries.filter { it != Gender.NONE }, gender.takeIf { it != Gender.NONE }, { gender = if (gender == it) Gender.NONE else it }, { it.label })
            FieldPair(
                { m -> PhoneInput(phone, { phone = it }, "Phone", m, tag = "student_phone") },
                { m -> PhoneInput(guardianPhone, { guardianPhone = it }, "Guardian's phone", m) },
            )
            FieldPair(
                { m -> DateInput(dob, { dob = it }, "Date of birth", m, clearable = true) },
                { m -> DateInput(joinDate, { joinDate = it }, "Joined", m) },
            )
            TextInput(email, { email = it.trim() }, "Email", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)
            TextInput(address, { address = it }, "Address", singleLine = false, minLines = 2)
            FieldPair(
                { m -> TextInput(education, { education = it }, "Education", m, placeholder = "e.g. HSC") },
                { m -> TextInput(institution, { institution = it }, "Institution", m) },
            )
            TextInput(idNo, { idNo = it }, "NID / birth registration no.", keyboardType = KeyboardType.Number)
            TextInput(note, { note = it }, "Note", singleLine = false, minLines = 2, maxLength = 1000)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val student = build()
                    app.store.put(Cols.students, student)
                    notify("${student.name} saved")
                    if (existing == null) nav.replace(Routes.student(student.id)) else nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_student"),
            ) { Text("Save") }
            if (existing == null) {
                OutlinedButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val student = build()
                        app.store.put(Cols.students, student)
                        nav.replace(Routes.admission(studentId = student.id))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save and admit to a batch") }
            }
        }
    }
}
