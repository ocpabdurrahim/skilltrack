package io.github.ocpabdurrahim.skilltrack.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.BatchStatus
import io.github.ocpabdurrahim.skilltrack.data.Col
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.EnquiryStatus
import io.github.ocpabdurrahim.skilltrack.data.Gender
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Installment
import io.github.ocpabdurrahim.skilltrack.data.PayMethod
import io.github.ocpabdurrahim.skilltrack.data.Payment
import io.github.ocpabdurrahim.skilltrack.data.Rec
import io.github.ocpabdurrahim.skilltrack.data.Student
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.Fees
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountInput
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PickerDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StudentAvatar
import io.github.ocpabdurrahim.skilltrack.ui.components.SwitchRow
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.LocalDate

/** Plans offered when admitting: everything at once or N monthly installments. */
val PLAN_CHOICES = listOf(1, 2, 3, 4, 6)

fun planLabel(n: Int) = if (n == 1) "Full" else "$n months"

/** One editable row of a payment plan. */
data class PlanRow(val due: String, val amount: String)

fun planRows(total: Long, count: Int, first: String): List<PlanRow> =
    Fees.plan(total, count, Dates.parse(first) ?: LocalDate.now()).map { PlanRow(it.due, Money.toInput(it.amount)) }

/** Editable installment rows; the caller checks they add up. */
@Composable
fun PlanEditor(rows: List<PlanRow>, onChange: (List<PlanRow>) -> Unit) {
    rows.forEachIndexed { i, row ->
        FieldPair(
            { m -> DateInput(row.due, { d -> onChange(rows.toMutableList().also { it[i] = row.copy(due = d) }) }, "Installment ${i + 1}", m) },
            { m -> AmountInput(row.amount, { a -> onChange(rows.toMutableList().also { it[i] = row.copy(amount = a) }) }, "Amount", m) },
        )
    }
}

fun List<PlanRow>.toInstallments(): List<Installment> = map { Installment(it.due, Money.parse(it.amount) ?: 0L) }

@Composable
fun AdmissionScreen(studentId: String?, batchId: String?, enquiryId: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val colors = SkillTheme.colors
    val enquiry = enquiryId?.let { id -> data.enquiries.firstOrNull { it.id == id } }

    // Ids fixed for this form, so a double tap cannot create two admissions.
    val newStudentId = rememberSaveable { Ids.new() }
    val enrollmentId = rememberSaveable { Ids.new() }
    val paymentId = rememberSaveable { Ids.new() }

    var newStudent by rememberSaveable { mutableStateOf(studentId == null) }
    var pickedStudentId by rememberSaveable { mutableStateOf(studentId) }
    var name by rememberSaveable { mutableStateOf(enquiry?.name.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(enquiry?.phone.orEmpty()) }
    var father by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf(Gender.NONE) }
    var picking by remember { mutableStateOf(false) }

    val openBatches = remember(data) {
        data.batches.filter { val s = data.batchStatus(it); s == BatchStatus.RUNNING || s == BatchStatus.UPCOMING }
            .sortedWith(compareBy({ enquiry?.courseId != null && it.courseId != enquiry.courseId }, { it.name }))
    }
    var chosenBatchId by rememberSaveable {
        mutableStateOf(batchId ?: openBatches.firstOrNull { it.courseId == enquiry?.courseId }?.id ?: openBatches.singleOrNull()?.id)
    }
    val batch = chosenBatchId?.let { data.batchById[it] }
    val course = batch?.let { data.courseById[it.courseId] }

    var date by rememberSaveable { mutableStateOf(data.today) }
    var fee by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("") }
    var planCount by rememberSaveable { mutableIntStateOf(1) }
    var plan by remember { mutableStateOf<List<PlanRow>>(emptyList()) }
    var collectNow by rememberSaveable { mutableStateOf(true) }
    var payAmount by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(PayMethod.CASH) }
    var note by rememberSaveable { mutableStateOf("") }

    // A new batch brings its course's fee.
    LaunchedEffect(chosenBatchId) {
        if (course != null) fee = Money.toInput(course.fee)
    }
    val feeValue = Money.parse(fee) ?: 0L
    val discountValue = Money.parse(discount) ?: 0L
    val payable = (feeValue - discountValue).coerceAtLeast(0)
    LaunchedEffect(payable, planCount, date) {
        plan = if (planCount <= 1) emptyList() else planRows(payable, planCount, date)
        payAmount = Money.toInput(if (planCount <= 1) payable else plan.firstOrNull()?.let { Money.parse(it.amount) } ?: payable)
    }

    val student: Student? = if (newStudent) null else pickedStudentId?.let { data.studentById[it] }
    val already = student != null && batch != null &&
        data.enrollmentsByStudent[student.id].orEmpty().any { it.batchId == batch.id && it.status != EnrollmentStatus.DROPPED }
    val seats = batch?.let { data.seatsTaken(it.id) } ?: 0
    val full = batch != null && batch.capacity > 0 && seats >= batch.capacity
    val planTotal = plan.toInstallments().sumOf { it.amount }
    val planOk = planCount <= 1 || planTotal == payable
    val payValue = Money.parse(payAmount) ?: 0L
    val problem = when {
        newStudent && name.isBlank() -> "Enter the student's name."
        !newStudent && student == null -> "Choose a student."
        batch == null -> if (openBatches.isEmpty()) "Create a batch first (Batches → +)." else "Choose a batch."
        already -> "${student?.name} is already in ${batch.name}."
        discountValue > feeValue -> "The discount is more than the fee."
        !planOk -> "The installments add up to ${money.format(planTotal)}, but the payable amount is ${money.format(payable)}."
        collectNow && payValue <= 0 -> "Enter the amount being paid now, or turn off \"Collect a payment now\"."
        else -> null
    }

    fun save() {
        val b = batch ?: return
        val writes = mutableListOf<Pair<Col<*>, Rec>>()
        val s = student ?: Student(
            id = newStudentId, regNo = data.nextRegNo(date), name = name.trim(), phone = phone.trim(),
            fatherName = father.trim(), gender = gender, joinDate = date,
        ).also { writes += Cols.students to it }
        val e = Enrollment(
            id = enrollmentId, studentId = s.id, batchId = b.id, courseId = b.courseId, date = date, fee = feeValue,
            discount = discountValue, installments = if (planCount <= 1) emptyList() else plan.toInstallments(),
            status = EnrollmentStatus.ACTIVE, rollNo = data.nextRollNo(b.id), note = note.trim(),
        )
        writes += Cols.enrollments to e
        val paid = collectNow && payValue > 0
        if (paid) {
            writes += Cols.payments to Payment(
                id = paymentId, enrollmentId = e.id, studentId = s.id, amount = payValue, date = date, method = method,
                receiptNo = data.nextReceiptNo(date), note = "Admission", receivedBy = data.me?.displayName.orEmpty(),
            )
        }
        if (enquiry != null) writes += Cols.enquiries to enquiry.copy(status = EnquiryStatus.CONVERTED, studentId = s.id)
        app.store.putMany(writes)
        notify("${s.name} admitted to ${b.name}")
        nav.replace(if (paid) Routes.receipt(paymentId) else Routes.enrollment(e.id))
    }

    Screen(title = "New admission", subtitle = enquiry?.let { "From enquiry: ${it.name}" }, onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldLabel("Student")
            if (studentId == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = newStudent, onClick = { newStudent = true }, shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.testTag("student_new")) { Text("New student") }
                    SegmentedButton(selected = !newStudent, onClick = { newStudent = false }, shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("student_existing")) { Text("Existing") }
                }
            }
            if (newStudent) {
                NameInput(name, { name = it }, "Full name *", tag = "admission_name")
                FieldPair(
                    { m -> PhoneInput(phone, { phone = it }, "Phone", m, tag = "admission_phone") },
                    { m -> NameInput(father, { father = it }, "Father's name", m) },
                )
                ChoiceChips(Gender.entries.filter { it != Gender.NONE }, gender.takeIf { it != Gender.NONE }, { gender = if (gender == it) Gender.NONE else it }, { it.label })
                Text("More details (photo, address, education…) can be added from the student's page later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SkillCard(onClick = if (studentId == null) ({ picking = true }) else null, modifier = Modifier.testTag("pick_student")) {
                    if (student == null) {
                        Text("Tap to choose a student", color = MaterialTheme.colorScheme.primary)
                    } else {
                        ListRow(title = student.name, subtitle = listOf(student.regNo, student.phone).filter { it.isNotBlank() }.joinToString(" · "), leading = { StudentAvatar(student.id, student.name) })
                    }
                }
            }

            FieldLabel("Batch")
            Dropdown(
                label = "Batch *",
                options = openBatches,
                selected = batch,
                onSelect = { b: Batch? -> chosenBatchId = b?.id },
                optionLabel = { "${it.name} · ${data.courseName(it.courseId)}" },
                tag = "admission_batch",
            )
            if (batch != null) {
                Text(
                    listOfNotNull(
                        batch.weekdays.joinToString(" ") { it.short }.ifBlank { null },
                        batch.time.ifBlank { null },
                        "starts ${Dates.full(batch.startDate)}",
                        if (batch.capacity > 0) "$seats of ${batch.capacity} seats taken" else "$seats admitted",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (full) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (full) Text("This batch is full; you can still admit, but check the room.", style = MaterialTheme.typography.bodySmall, color = colors.warning)
            }
            DateInput(date, { date = it }, "Admission date")

            FieldLabel("Fee")
            FieldPair(
                { m -> AmountInput(fee, { fee = it }, "Course fee", m, tag = "admission_fee") },
                { m -> AmountInput(discount, { discount = it }, "Discount", m, tag = "admission_discount") },
            )
            Text("Payable: ${money.format(payable)}", style = MaterialTheme.typography.titleMedium)
            FieldLabel("Payment plan")
            ChoiceChips(PLAN_CHOICES, planCount, { planCount = it }, ::planLabel, tagPrefix = "plan")
            if (planCount > 1) {
                PlanEditor(plan) { plan = it }
                if (!planOk) Text("Installments total ${money.format(planTotal)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SwitchRow("Collect a payment now", collectNow, { collectNow = it }, subtitle = "Makes a money receipt straight away")
            if (collectNow) {
                AmountInput(payAmount, { payAmount = it }, "Paying now", tag = "admission_pay")
                ChoiceChips(PayMethod.entries, method, { method = it }, { it.label })
            }
            TextInput(note, { note = it }, "Note", singleLine = false)

            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("admission_problem")) }
            Button(
                enabled = problem == null,
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_admission"),
            ) { Text(if (collectNow && payValue > 0) "Admit and make receipt" else "Admit") }
            if (openBatches.isEmpty() && data.role.manages) {
                OutlinedButton(onClick = { nav.go(Routes.batchEdit()) }, modifier = Modifier.fillMaxWidth()) { Text("Create a batch") }
            }
        }
    }

    if (picking) {
        PickerDialog(
            title = "Choose a student",
            items = data.students.filter { !it.archived },
            onPick = { pickedStudentId = it.id; picking = false },
            onDismiss = { picking = false },
            label = { it.name },
            subtitle = { listOf(it.regNo, it.phone).filter { s -> s.isNotBlank() }.joinToString(" · ") },
            searchText = { it.searchText() },
            key = { it.id },
        )
    }
}
