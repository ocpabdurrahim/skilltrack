package io.github.ocpabdurrahim.skilltrack.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Certificate
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.FeeStatus
import io.github.ocpabdurrahim.skilltrack.domain.Fees
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.print.Printer
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.system.findActivity
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.MoneyFormat
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountInput
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.FigureRow
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.ProgressBar
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.printouts
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme

/** A polite fee reminder for WhatsApp or SMS. */
fun reminderText(data: CenterData, e: Enrollment, fee: FeeStatus, money: MoneyFormat): String {
    val student = data.studentById[e.studentId]
    val amount = if (fee.overdue > 0) fee.overdue else fee.nextAmount.takeIf { it > 0 } ?: fee.due
    val whenText = when {
        fee.overdue > 0 -> "is due"
        fee.nextDue != null -> "is due on ${Dates.full(fee.nextDue)}"
        else -> "is due"
    }
    val center = data.center
    return buildString {
        append("Dear ${student?.name ?: "student"}, your fee of ${money.format(amount)} for ${data.courseName(e.courseId)} (${data.batchName(e.batchId)}) $whenText. ")
        append("Paid so far: ${money.format(fee.paid)} of ${money.format(fee.payable)}. ")
        append("Thank you. — ${center?.name.orEmpty()}")
        if (!center?.phone.isNullOrBlank()) append(", ${center?.phone}")
    }
}

@Composable
fun EnrollmentScreen(id: String) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    val e = data.enrollmentById[id]
    var editingFee by remember { mutableStateOf(false) }
    var editingStatus by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (e == null) {
        Screen("Admission", onBack = nav::back) { EmptyState("🔍", "Admission not found", "It may have been deleted on another device.") }
        return
    }
    val student = data.studentById[e.studentId]
    val batch = data.batchById[e.batchId]
    val course = data.courseById[e.courseId]
    val fee = data.fee(e)
    val payments = data.paymentsByEnrollment[id].orEmpty()
    val att = data.attendanceStat(e)
    val result = data.result(e)
    val exams = data.examsByBatch[e.batchId].orEmpty()
    val cert = data.certificatesByEnrollment[id]
    val seesMoney = data.role.seesMoney
    val office = data.role.office

    fun printMarkSheet() {
        val activity = context.findActivity() ?: return
        val s = student ?: return
        app.appLock.skipNextLock()
        Printer.print(activity, "Mark sheet ${s.regNo}", money.printouts(data.center).markSheet(s, e, course, batch, exams, result, att))
    }

    fun printCertificate(c: Certificate) {
        val activity = context.findActivity() ?: return
        app.appLock.skipNextLock()
        val instructor = batch?.instructorId?.let { data.instructorById[it] }
        Printer.print(activity, "Certificate ${c.certNo}", money.printouts(data.center).certificate(c, student, e, course, batch, instructor), landscape = true)
    }

    ListScreen(
        title = student?.name ?: "Admission",
        subtitle = "${course?.name ?: "—"} · ${batch?.name ?: "—"} · Roll ${e.rollNo}",
        onBack = nav::back,
    ) {
        item {
            SkillCard(onClick = student?.let { s -> { nav.go(Routes.student(s.id)) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(course?.name ?: "—", style = MaterialTheme.typography.titleMedium)
                        Text("Admitted ${Dates.full(e.date)}" + (e.completedOn.takeIf { it.isNotBlank() }?.let { " · completed ${Dates.full(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(e.status.label, statusColor(e.status))
                }
                if (office) TextButton(onClick = { editingStatus = true }, modifier = Modifier.testTag("change_status")) { Text("Change status") }
            }
        }

        if (seesMoney) {
            item {
                SkillCard(modifier = Modifier.testTag("fee_card")) {
                    Text("Fees", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FigureRow(
                        Triple("Payable", money.format(fee.payable), MaterialTheme.colorScheme.onSurface),
                        Triple("Paid", money.format(fee.paid), colors.success),
                        Triple("Due", money.format(fee.due.coerceAtLeast(0)), if (fee.due > 0) colors.danger else colors.success),
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(if (fee.payable > 0) fee.paid.toFloat() / fee.payable else 1f, colors.success)
                    Spacer(Modifier.height(8.dp))
                    when {
                        fee.settled -> Text(if (fee.due < 0) "Paid ${money.format(-fee.due)} in advance" else "Fully paid ✓", color = colors.success)
                        fee.overdue > 0 -> Text("Overdue: ${money.format(fee.overdue)}", color = colors.danger)
                        fee.nextDue != null -> Text("Next: ${money.format(fee.nextAmount)} on ${Dates.full(fee.nextDue)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.discount > 0) Text("Fee ${money.format(e.fee)} − discount ${money.format(e.discount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (office && !fee.settled) AssistChip(onClick = { nav.go(Routes.collect(enrollmentId = id)) }, label = { Text("Collect fee") }, leadingIcon = { Icon(Icons.Filled.Payments, null) }, modifier = Modifier.testTag("enrollment_collect"))
                        val phone = (student?.phone?.ifBlank { null } ?: student?.guardianPhone).orEmpty()
                        if (!fee.settled && phone.isNotBlank()) {
                            AssistChip(onClick = { Share.whatsApp(context, phone, reminderText(data, e, fee, money)) }, label = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.Filled.Chat, null) })
                            AssistChip(onClick = { Share.sms(context, phone, reminderText(data, e, fee, money)) }, label = { Text("SMS") }, leadingIcon = { Icon(Icons.Filled.Sms, null) })
                        }
                        if (office) AssistChip(onClick = { editingFee = true }, label = { Text("Change fee or plan") }, modifier = Modifier.testTag("edit_plan"))
                    }
                }
            }
            item { SectionHeader("Payment plan") }
            item {
                SkillCard {
                    var running = 0L
                    Fees.schedule(e).forEachIndexed { i, inst ->
                        running += inst.amount
                        val paidUp = fee.paid >= running
                        val overdue = !paidUp && inst.due.isNotBlank() && inst.due <= data.today
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${Dates.full(inst.due)}", modifier = Modifier.weight(1f))
                            Text(money.format(inst.amount))
                            Spacer(Modifier.height(1.dp))
                            StatusChip(
                                when { paidUp -> "Paid"; overdue -> "Overdue"; else -> "Upcoming" },
                                when { paidUp -> colors.success; overdue -> colors.danger; else -> MaterialTheme.colorScheme.outline },
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Payments") }
            if (payments.isEmpty()) item { SkillCard { Text("No payment yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(payments, key = { it.id }) { p ->
                SkillCard(onClick = { nav.go(Routes.receipt(p.id)) }) {
                    ListRow(
                        title = p.receiptNo.ifBlank { "Receipt" },
                        subtitle = "${Dates.full(p.date)} · ${p.method.label}" + (p.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        trailing = { AmountColumn(money.format(p.amount), null, colors.success) },
                    )
                }
            }
        }

        item { SectionHeader("Attendance and results") }
        item {
            SkillCard {
                KeyValue("Attendance", att.rate?.let { "%.0f%% (%d of %d classes)".format(it * 100, att.attended, att.counted) } ?: "Not marked yet")
                if (att.excused > 0) KeyValue("Excused", att.excused.toString())
                exams.forEach { ex ->
                    KeyValue(ex.name, ex.marks[e.id]?.let { "${Academics.marks(it)} / ${Academics.marks(ex.fullMarks)}" } ?: "—")
                }
                if (result != null) {
                    KeyValue("Result", "%.1f%% · %s (%.2f)".format(result.percent, result.grade.letter, result.grade.point) + (result.position?.let { " · position $it" } ?: ""))
                }
                if (exams.isNotEmpty()) TextButton(onClick = ::printMarkSheet) {
                    Icon(Icons.Filled.Print, null)
                    Text("  Print mark sheet")
                }
            }
        }

        item { SectionHeader("Certificate") }
        item {
            SkillCard {
                if (cert != null) {
                    KeyValue("Certificate No", cert.certNo)
                    KeyValue("Issued", Dates.full(cert.issueDate))
                    if (cert.grade.isNotBlank()) KeyValue("Grade", cert.grade)
                    TextButton(onClick = { printCertificate(cert) }, modifier = Modifier.testTag("print_certificate")) {
                        Icon(Icons.Filled.Print, null)
                        Text("  Print certificate")
                    }
                } else {
                    Text(
                        if (e.status == EnrollmentStatus.COMPLETED) "Ready for a certificate." else "Usually issued once the course is completed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (office) {
                        TextButton(
                            onClick = {
                                val c = Certificate(
                                    enrollmentId = e.id, studentId = e.studentId, certNo = data.nextCertNo(data.today),
                                    issueDate = data.today, grade = result?.grade?.letter.orEmpty(),
                                )
                                app.store.put(Cols.certificates, c)
                                notify("Certificate ${c.certNo} issued")
                                printCertificate(c)
                            },
                            modifier = Modifier.testTag("issue_certificate"),
                        ) {
                            Icon(Icons.Filled.WorkspacePremium, null)
                            Text("  Issue certificate")
                        }
                    }
                }
            }
        }

        if (e.note.isNotBlank()) item { SkillCard { KeyValue("Note", e.note) } }
        if (office && payments.isEmpty()) {
            item { TextButton(onClick = { confirmDelete = true }) { Text("Delete this admission", color = MaterialTheme.colorScheme.error) } }
        }
    }

    if (editingFee) FeeDialog(e, onDismiss = { editingFee = false }) { updated ->
        app.store.put(Cols.enrollments, updated)
        editingFee = false
        notify("Fee updated")
    }
    if (editingStatus) StatusDialog(e, data.today, onDismiss = { editingStatus = false }) { updated ->
        app.store.put(Cols.enrollments, updated)
        editingStatus = false
        notify("Marked ${updated.status.label.lowercase()}")
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this admission?",
            text = "${student?.name ?: "The student"} will be taken out of ${batch?.name ?: "the batch"}. Use \"Dropped\" instead to keep the history.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.enrollments, e.id)
                confirmDelete = false
                notify("Admission deleted")
                nav.back()
            },
        )
    }
}

@Composable
private fun FeeDialog(e: Enrollment, onDismiss: () -> Unit, onSave: (Enrollment) -> Unit) {
    val money = LocalMoney.current
    var fee by remember { mutableStateOf(Money.toInput(e.fee)) }
    var discount by remember { mutableStateOf(if (e.discount == 0L) "" else Money.toInput(e.discount)) }
    var count by remember { mutableIntStateOf(e.installments.size.coerceAtLeast(1)) }
    var rows by remember { mutableStateOf(e.installments.map { PlanRow(it.due, Money.toInput(it.amount)) }) }
    val payable = ((Money.parse(fee) ?: 0L) - (Money.parse(discount) ?: 0L)).coerceAtLeast(0)
    val total = rows.toInstallments().sumOf { it.amount }
    val ok = Money.parse(fee) != null && (count <= 1 || total == payable)
    FormDialog(
        title = "Fee and payment plan",
        onDismiss = onDismiss,
        saveEnabled = ok,
        onSave = {
            onSave(
                e.copy(
                    fee = Money.parse(fee) ?: e.fee, discount = Money.parse(discount) ?: 0L,
                    installments = if (count <= 1) emptyList() else rows.toInstallments(),
                ),
            )
        },
    ) {
        FieldPair(
            { m -> AmountInput(fee, { fee = it }, "Course fee", m) },
            { m -> AmountInput(discount, { discount = it }, "Discount", m) },
        )
        Text("Payable: ${money.format(payable)}")
        FieldLabel("Plan")
        ChoiceChips(PLAN_CHOICES, count, { n ->
            count = n
            rows = if (n <= 1) emptyList() else planRows(payable, n, e.installments.firstOrNull()?.due ?: e.date)
        }, ::planLabel)
        if (count > 1) {
            PlanEditor(rows) { rows = it }
            if (total != payable) Text("Installments total ${money.format(total)}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusDialog(e: Enrollment, today: String, onDismiss: () -> Unit, onSave: (Enrollment) -> Unit) {
    var status by remember { mutableStateOf(e.status) }
    var completedOn by remember { mutableStateOf(e.completedOn.ifBlank { today }) }
    FormDialog(
        title = "Admission status",
        onDismiss = onDismiss,
        onSave = { onSave(e.copy(status = status, completedOn = if (status == EnrollmentStatus.COMPLETED) completedOn else "")) },
    ) {
        ChoiceChips(EnrollmentStatus.entries, status, { status = it }, { it.label }, tagPrefix = "status")
        if (status == EnrollmentStatus.COMPLETED) DateInput(completedOn, { completedOn = it }, "Completed on")
        Text(
            when (status) {
                EnrollmentStatus.ACTIVE -> "Attending classes; counted in attendance and dues."
                EnrollmentStatus.COMPLETED -> "Finished the course; still counted in dues until paid."
                EnrollmentStatus.DROPPED -> "Left the course; no longer marked or counted in dues."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
