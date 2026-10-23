package io.github.ocpabdurrahim.skilltrack.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.InstructorPayment
import io.github.ocpabdurrahim.skilltrack.data.PayMethod
import io.github.ocpabdurrahim.skilltrack.data.PayType
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.domain.Pay
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Avatar
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.FigureRow
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.SwitchRow
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.YearMonth

@Composable
fun InstructorsScreen() {
    val data = centerData()
    val nav = LocalNav.current
    val money = LocalMoney.current
    val colors = SkillTheme.colors
    ListScreen(
        title = "Instructors",
        onBack = nav::back,
        fab = {
            if (data.role.manages) {
                ExtendedFloatingActionButton(
                    onClick = { nav.go(Routes.instructorEdit()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New instructor") },
                    modifier = Modifier.testTag("fab_instructor"),
                )
            }
        },
    ) {
        if (data.instructors.isEmpty()) item { EmptyState("🧑‍🏫", "No instructors yet", "Add the people who teach your batches, with how they are paid.") }
        items(data.instructors, key = { it.id }) { i ->
            val pay = if (data.role.seesMoney) data.payStatus(i) else null
            SkillCard(onClick = { nav.go(Routes.instructor(i.id)) }, modifier = Modifier.testTag("instructor_row")) {
                ListRow(
                    title = i.name + if (i.archived) " (archived)" else "",
                    subtitle = listOfNotNull(i.subject.ifBlank { null }, "${data.batches.count { it.instructorId == i.id }} batches", i.payType.label).joinToString(" · "),
                    leading = { Avatar(i.name) },
                    trailing = {
                        val owed = pay?.owed
                        if (owed != null && owed != 0L) AmountColumn(money.format(owed), if (owed > 0) "owed" else "advance", if (owed > 0) colors.danger else colors.success)
                    },
                )
            }
        }
    }
}

@Composable
fun InstructorDetailScreen(id: String) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    val i = data.instructorById[id]
    var paying by remember { mutableStateOf<InstructorPayment?>(null) }
    var deleting by remember { mutableStateOf<InstructorPayment?>(null) }

    if (i == null) {
        Screen("Instructor", onBack = nav::back) { EmptyState("🔍", "Instructor not found", "They may have been deleted on another device.") }
        return
    }
    val batches = data.batches.filter { it.instructorId == id }
    val payments = data.instructorPaymentsBy[id].orEmpty()
    val pay = data.payStatus(i)

    ListScreen(
        title = i.name,
        subtitle = i.subject.ifBlank { null },
        onBack = nav::back,
        actions = {
            if (data.role.manages) IconButton(onClick = { nav.go(Routes.instructorEdit(id)) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
        },
    ) {
        item {
            SkillCard {
                KeyValue("Phone", i.phone)
                KeyValue("Email", i.email)
                KeyValue("Joined", Dates.full(i.joinDate, ""))
                if (i.leaveDate.isNotBlank()) KeyValue("Left", Dates.full(i.leaveDate))
                KeyValue("Pay", if (i.payType == PayType.NONE) i.payType.label else "${money.format(i.payRate)} · ${i.payType.label.lowercase()}")
                if (i.note.isNotBlank()) KeyValue("Note", i.note)
                if (i.phone.isNotBlank()) TextButton(onClick = { Share.call(context, i.phone) }) {
                    Icon(Icons.Filled.Call, null)
                    Text("  Call")
                }
            }
        }
        if (data.role.seesMoney) {
            item {
                SkillCard(modifier = Modifier.testTag("pay_card")) {
                    Text("Pay", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val earned = pay.earned
                    if (earned != null) {
                        val owed = pay.owed ?: 0L
                        FigureRow(
                            Triple("Earned (${Pay.unitLabel(i.payType, pay.units)})", money.format(earned), MaterialTheme.colorScheme.onSurface),
                            Triple("Paid", money.format(pay.paid), colors.success),
                            Triple(if (owed >= 0) "Owed" else "Advance", money.format(kotlin.math.abs(owed)), if (owed > 0) colors.danger else colors.success),
                        )
                    } else {
                        Text("Paid so far: ${money.format(pay.paid)}", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (data.role.office) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                paying = InstructorPayment(
                                    instructorId = id, date = data.today,
                                    amount = if (i.payType == PayType.MONTHLY) i.payRate else (pay.owed ?: 0L).coerceAtLeast(0),
                                    month = if (i.payType == PayType.MONTHLY) YearMonth.now().minusMonths(1).toString() else "",
                                )
                            },
                            modifier = Modifier.testTag("pay_instructor"),
                        ) { Text("Record a payment") }
                    }
                }
            }
        }
        item { SectionHeader("Batches") }
        if (batches.isEmpty()) item { SkillCard { Text("Not teaching any batch yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(batches, key = { "b" + it.id }) { b ->
            SkillCard(onClick = { nav.go(Routes.batch(b.id)) }) {
                ListRow(
                    title = b.name,
                    subtitle = "${data.courseName(b.courseId)} · ${data.batchStatus(b).label} · ${data.attendanceByBatch[b.id].orEmpty().size} classes held",
                )
            }
        }
        if (data.role.seesMoney && payments.isNotEmpty()) {
            item { SectionHeader("Payments") }
            items(payments, key = { "p" + it.id }) { p ->
                SkillCard(onClick = { if (data.role.office) paying = p }) {
                    ListRow(
                        title = Dates.full(p.date),
                        subtitle = listOfNotNull(
                            Dates.parseMonth(p.month)?.let { "for ${Dates.monthYear(it)}" },
                            p.batchId?.let { data.batchName(it) },
                            p.method.label,
                            p.note.ifBlank { null },
                        ).joinToString(" · "),
                        trailing = { AmountColumn(money.format(p.amount), null, colors.warning) },
                    )
                }
            }
        }
    }

    paying?.let { p ->
        InstructorPaymentDialog(
            payment = p,
            isNew = payments.none { it.id == p.id },
            batches = batches,
            onDismiss = { paying = null },
            onDelete = { deleting = p; paying = null },
            onSave = {
                app.store.put(Cols.instructorPayments, it)
                paying = null
                notify("Payment of ${money.format(it.amount)} recorded")
            },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete this payment?",
            text = "${money.format(p.amount)} on ${Dates.full(p.date)} will no longer count.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                app.store.remove(Cols.instructorPayments, p.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun InstructorPaymentDialog(
    payment: InstructorPayment,
    isNew: Boolean,
    batches: List<Batch>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (InstructorPayment) -> Unit,
) {
    var amount by remember { mutableStateOf(if (payment.amount == 0L) "" else Money.toInput(payment.amount)) }
    var date by remember { mutableStateOf(payment.date) }
    var month by remember { mutableStateOf(payment.month) }
    var batchId by remember { mutableStateOf(payment.batchId) }
    var method by remember { mutableStateOf(payment.method) }
    var note by remember { mutableStateOf(payment.note) }
    val months = remember { (0..12).map { YearMonth.now().minusMonths(it.toLong()).toString() } }
    FormDialog(
        title = if (isNew) "Pay instructor" else "Edit payment",
        onDismiss = onDismiss,
        saveEnabled = (Money.parse(amount) ?: 0L) > 0,
        onSave = { onSave(payment.copy(amount = Money.parse(amount) ?: 0L, date = date, month = month, batchId = batchId, method = method, note = note.trim())) },
        onDelete = if (isNew) null else onDelete,
    ) {
        AmountInput(amount, { amount = it }, "Amount *", tag = "instructor_pay_amount")
        DateInput(date, { date = it }, "Paid on")
        Dropdown(
            label = "Salary for month",
            options = months,
            selected = month.ifBlank { null },
            onSelect = { m: String? -> month = m.orEmpty() },
            optionLabel = { Dates.parseMonth(it)?.let(Dates::monthYear) ?: it },
            noneLabel = "Not a monthly salary",
        )
        if (batches.isNotEmpty()) {
            Dropdown(
                label = "For batch",
                options = batches,
                selected = batches.firstOrNull { it.id == batchId },
                onSelect = { b: Batch? -> batchId = b?.id },
                optionLabel = { it.name },
                noneLabel = "Any / not for one batch",
            )
        }
        ChoiceChips(PayMethod.entries, method, { method = it }, { it.label })
        TextInput(note, { note = it }, "Note")
    }
}

@Composable
fun InstructorEditScreen(id: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val existing = id?.let { data.instructorById[it] }
    val newId = rememberSaveable { Ids.new() }
    val base = existing ?: Instructor(id = newId, joinDate = data.today)

    var name by rememberSaveable { mutableStateOf(base.name) }
    var phone by rememberSaveable { mutableStateOf(base.phone) }
    var email by rememberSaveable { mutableStateOf(base.email) }
    var subject by rememberSaveable { mutableStateOf(base.subject) }
    var payType by rememberSaveable { mutableStateOf(base.payType) }
    var rate by rememberSaveable { mutableStateOf(if (base.payRate == 0L) "" else Money.toInput(base.payRate)) }
    var join by rememberSaveable { mutableStateOf(base.joinDate) }
    var leave by rememberSaveable { mutableStateOf(base.leaveDate) }
    var note by rememberSaveable { mutableStateOf(base.note) }
    var archived by rememberSaveable { mutableStateOf(base.archived) }

    Screen(title = if (existing == null) "New instructor" else "Edit instructor", onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NameInput(name, { name = it }, "Name *", tag = "instructor_name")
            TextInput(subject, { subject = it }, "Teaches", placeholder = "e.g. Graphic design, Office")
            FieldPair(
                { m -> PhoneInput(phone, { phone = it }, "Phone", m) },
                { m -> TextInput(email, { email = it.trim() }, "Email", m, keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None) },
            )
            FieldLabel("How they are paid")
            ChoiceChips(PayType.entries, payType, { payType = it }, { it.label })
            if (payType != PayType.NONE) {
                AmountInput(
                    rate, { rate = it },
                    when (payType) {
                        PayType.MONTHLY -> "Salary per month"
                        PayType.PER_BATCH -> "Pay per batch"
                        else -> "Pay per class"
                    },
                )
            }
            FieldPair(
                { m -> DateInput(join, { join = it }, "Joined", m) },
                { m -> DateInput(leave, { leave = it }, "Left", m, clearable = true) },
            )
            TextInput(note, { note = it }, "Note", singleLine = false)
            if (existing != null) SwitchRow("Archived", archived, { archived = it }, subtitle = "Hidden when choosing an instructor for a batch.")
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val i = base.copy(
                        name = name.trim(), phone = phone.trim(), email = email.trim(), subject = subject.trim(), payType = payType,
                        payRate = if (payType == PayType.NONE) 0L else Money.parse(rate) ?: 0L, joinDate = join, leaveDate = leave,
                        note = note.trim(), archived = archived,
                    )
                    app.store.put(Cols.instructors, i)
                    notify("${i.name} saved")
                    if (existing == null) nav.replace(Routes.instructor(i.id)) else nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_instructor"),
            ) { Text("Save") }
            if (existing == null) {
                OutlinedButton(onClick = nav::back, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
