package io.github.ocpabdurrahim.skilltrack.ui.fees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.PayMethod
import io.github.ocpabdurrahim.skilltrack.data.Payment
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.FeeStatus
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.domain.Words
import io.github.ocpabdurrahim.skilltrack.print.Printer
import io.github.ocpabdurrahim.skilltrack.print.Printouts
import io.github.ocpabdurrahim.skilltrack.system.Csv
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
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.HeroCard
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.MonthSelector
import io.github.ocpabdurrahim.skilltrack.ui.components.PickerDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SearchField
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatTile
import io.github.ocpabdurrahim.skilltrack.ui.components.StudentAvatar
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.components.TileRow
import io.github.ocpabdurrahim.skilltrack.ui.printouts
import io.github.ocpabdurrahim.skilltrack.ui.students.reminderText
import io.github.ocpabdurrahim.skilltrack.ui.students.searchText
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.YearMonth

@Composable
fun FeesScreen(showDues: Boolean, standalone: Boolean) {
    val data = centerData()
    val nav = LocalNav.current
    var tab by rememberSaveable { mutableIntStateOf(if (showDues) 1 else 0) }
    ListScreen(
        title = if (tab == 0) "Fees" else "Dues",
        onBack = if (standalone) nav::back else null,
        fab = {
            if (data.role.office) {
                ExtendedFloatingActionButton(
                    onClick = { nav.go(Routes.collect()) },
                    icon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                    text = { Text("Collect fee") },
                    modifier = Modifier.testTag("fab_collect"),
                )
            }
        },
    ) {
        item {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Receipts") }, modifier = Modifier.testTag("fees_tab_receipts"))
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Dues") }, modifier = Modifier.testTag("fees_tab_dues"))
            }
        }
        if (tab == 0) receipts(data) else dues(data)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.receipts(data: CenterData) {
    item { ReceiptsHeader(data) }
}

/** The receipts tab: one month at a time, with filters and day totals. */
@Composable
private fun ReceiptsHeader(data: CenterData) {
    val nav = LocalNav.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var method by rememberSaveable { mutableStateOf<PayMethod?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val ym = YearMonth.parse(month)
    val shown = remember(data, month, method, query) {
        val q = query.trim().lowercase()
        data.payments.filter { p ->
            Dates.inMonth(p.date, ym) && (method == null || p.method == method) &&
                (q.isEmpty() || p.receiptNo.lowercase().contains(q) || data.studentById[p.studentId]?.searchText()?.contains(q) == true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthSelector(ym, { month = it.toString() })
        TileRow {
            StatTile("Collected", money.format(shown.sumOf { it.amount }), colors.success, Modifier.weight(1f), tag = "month_total")
            StatTile("Receipts", shown.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        SearchField(query, { query = it }, "Receipt no. or student")
        ChoiceChips(listOf<PayMethod?>(null) + PayMethod.entries, method, { method = it }, { it?.label ?: "All" })
        if (shown.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PayMethod.entries.mapNotNull { m -> shown.filter { it.method == m }.sumOf { it.amount }.takeIf { it > 0 }?.let { "${m.label} ${money.format(it)}" } }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { exportPayments(context, data, shown, ym, money) }) { Icon(Icons.Filled.FileDownload, contentDescription = "Export to CSV") }
            }
        }
        if (shown.isEmpty()) EmptyState("🧾", "No receipts", "Payments collected in ${Dates.month(ym)} appear here.")
        shown.groupBy { it.date }.forEach { (day, list) ->
            SectionHeader("${Dates.relative(day)} · ${money.format(list.sumOf { it.amount })}")
            list.forEach { p ->
                SkillCard(onClick = { nav.go(Routes.receipt(p.id)) }, modifier = Modifier.testTag("receipt_row")) {
                    ListRow(
                        title = data.studentName(p.studentId),
                        subtitle = "${p.receiptNo} · ${p.method.label} · ${data.batchName(data.enrollmentById[p.enrollmentId]?.batchId)}",
                        leading = { StudentAvatar(p.studentId, data.studentName(p.studentId), size = 36.dp) },
                        trailing = { AmountColumn(money.format(p.amount), null, colors.success) },
                    )
                }
            }
        }
    }
}

private fun exportPayments(context: android.content.Context, data: CenterData, list: List<Payment>, month: YearMonth, money: MoneyFormat) {
    val rows = list.map { p ->
        val e = data.enrollmentById[p.enrollmentId]
        listOf(
            p.receiptNo, p.date, data.studentById[p.studentId]?.regNo, data.studentName(p.studentId), data.courseName(e?.courseId),
            data.batchName(e?.batchId), Money.toInput(p.amount), p.method.label, p.receivedBy, p.note,
        )
    }
    Share.file(
        context, "receipts-$month.csv", "text/csv",
        Csv.build(listOf("Receipt No", "Date", "Reg No", "Student", "Course", "Batch", "Amount (${money.symbol})", "Method", "Received by", "Note"), rows),
    )
}

private enum class DueFilter(val label: String) { OVERDUE("Overdue"), ALL("All dues") }

private fun androidx.compose.foundation.lazy.LazyListScope.dues(data: CenterData) {
    item { DuesList(data) }
}

@Composable
private fun DuesList(data: CenterData) {
    val nav = LocalNav.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val app = LocalApp.current
    val colors = SkillTheme.colors
    var filter by rememberSaveable { mutableStateOf(DueFilter.OVERDUE) }
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = remember(data, filter, courseId) {
        data.countedEnrollments
            .filter { courseId == null || it.courseId == courseId }
            .map { it to data.fee(it) }
            .filter { (_, fee) -> if (filter == DueFilter.OVERDUE) fee.overdue > 0 else fee.due > 0 }
            .sortedWith(compareByDescending<Pair<Enrollment, FeeStatus>> { it.second.overdue }.thenByDescending { it.second.due })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TileRow {
            StatTile("Total due", money.format(rows.sumOf { it.second.due }), colors.warning, Modifier.weight(1f), tag = "dues_total")
            StatTile("Overdue", money.format(rows.sumOf { it.second.overdue }), colors.danger, Modifier.weight(1f))
        }
        ChoiceChips(DueFilter.entries, filter, { filter = it }, { it.label })
        if (data.courses.size > 1) {
            Dropdown(
                label = "Course",
                options = data.courses,
                selected = data.courseById[courseId],
                onSelect = { c: Course? -> courseId = c?.id },
                optionLabel = { it.name },
                noneLabel = "All courses",
            )
        }
        if (rows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val activity = context.findActivity() ?: return@OutlinedButton
                    val list = rows.map { (e, fee) -> Printouts.DueRow(data.studentById[e.studentId], data.courseById[e.courseId], data.batchById[e.batchId], fee) }
                    app.appLock.skipNextLock()
                    Printer.print(activity, "Dues list", money.printouts(data.center).duesList(if (filter == DueFilter.OVERDUE) "Overdue fees" else "All dues", list))
                }) {
                    Icon(Icons.Filled.Print, null)
                    Text("  Print")
                }
                OutlinedButton(onClick = {
                    val csv = Csv.build(
                        listOf("Reg No", "Student", "Phone", "Course", "Batch", "Payable", "Paid", "Due", "Overdue", "Next due"),
                        rows.map { (e, fee) ->
                            val s = data.studentById[e.studentId]
                            listOf(s?.regNo, s?.name, s?.phone, data.courseName(e.courseId), data.batchName(e.batchId), Money.toInput(fee.payable), Money.toInput(fee.paid), Money.toInput(fee.due), Money.toInput(fee.overdue), fee.nextDue)
                        },
                    )
                    Share.file(context, "dues-${Dates.forFileName()}.csv", "text/csv", csv)
                }) {
                    Icon(Icons.Filled.FileDownload, null)
                    Text("  CSV")
                }
            }
        }
        if (rows.isEmpty()) EmptyState("🎉", if (filter == DueFilter.OVERDUE) "Nothing overdue" else "No dues", "Everyone is up to date with their payment plan.")
        rows.forEach { (e, fee) ->
            val s = data.studentById[e.studentId]
            SkillCard(onClick = { nav.go(Routes.enrollment(e.id)) }, modifier = Modifier.testTag("due_row")) {
                ListRow(
                    title = s?.name ?: "Unknown student",
                    subtitle = "${data.batchName(e.batchId)} · " + (if (fee.overdue > 0) "overdue ${money.format(fee.overdue)}" else fee.nextDue?.let { "next ${Dates.short(it)}" } ?: "due"),
                    leading = { StudentAvatar(e.studentId, s?.name ?: "?", size = 36.dp) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AmountColumn(money.format(fee.due), "due", if (fee.overdue > 0) colors.danger else colors.warning)
                            val phone = s?.phone?.ifBlank { null } ?: s?.guardianPhone
                            if (!phone.isNullOrBlank()) {
                                IconButton(onClick = { Share.whatsApp(context, phone, reminderText(data, e, fee, money)) }) {
                                    Icon(Icons.Filled.Chat, contentDescription = "Send a reminder on WhatsApp")
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

// ---- Collecting a fee -------------------------------------------------------

@Composable
fun CollectFeeScreen(enrollmentId: String?, studentId: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val colors = SkillTheme.colors
    val paymentId = rememberSaveable { Ids.new() }

    var pickedEnrollment by rememberSaveable { mutableStateOf(enrollmentId) }
    var pickedStudent by rememberSaveable { mutableStateOf(studentId ?: enrollmentId?.let { data.enrollmentById[it]?.studentId }) }
    var picking by remember { mutableStateOf(pickedStudent == null) }
    val student = pickedStudent?.let { data.studentById[it] }
    val options = student?.let { s -> data.enrollmentsByStudent[s.id].orEmpty().filter { it.status != EnrollmentStatus.DROPPED } }.orEmpty()
    val enrollment = pickedEnrollment?.let { data.enrollmentById[it] }?.takeIf { it.studentId == pickedStudent }
        ?: options.maxByOrNull { data.fee(it).due }
    val fee = enrollment?.let { data.fee(it) }
    val suggested = fee?.let { if (it.overdue > 0) it.overdue else if (it.nextAmount > 0) it.nextAmount else it.due.coerceAtLeast(0) } ?: 0L

    var amount by rememberSaveable(enrollment?.id) { mutableStateOf(if (suggested > 0) Money.toInput(suggested) else "") }
    var date by rememberSaveable { mutableStateOf(data.today) }
    var method by rememberSaveable { mutableStateOf(PayMethod.CASH) }
    var note by rememberSaveable { mutableStateOf("") }
    val value = Money.parse(amount) ?: 0L
    val (major, minor) = Words.names(data.center?.currencyCode ?: "BDT")

    Screen(title = "Collect fee", onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkillCard(onClick = { picking = true }, modifier = Modifier.testTag("collect_student")) {
                if (student == null) Text("Tap to choose a student", color = MaterialTheme.colorScheme.primary)
                else ListRow(title = student.name, subtitle = listOf(student.regNo, student.phone).filter { it.isNotBlank() }.joinToString(" · "), leading = { StudentAvatar(student.id, student.name) })
            }
            if (student != null && options.isEmpty()) Text("${student.name} has no admission to collect for.", color = MaterialTheme.colorScheme.error)
            if (options.size > 1) {
                FieldLabel("For")
                ChoiceChips(options, enrollment, { pickedEnrollment = it.id }, { "${data.courseName(it.courseId)} (${data.batchName(it.batchId)})" })
            }
            if (enrollment != null && fee != null) {
                SkillCard {
                    KeyValue("Course", "${data.courseName(enrollment.courseId)} · ${data.batchName(enrollment.batchId)}")
                    KeyValue("Payable", money.format(fee.payable))
                    KeyValue("Paid so far", money.format(fee.paid))
                    KeyValue("Due", money.format(fee.due.coerceAtLeast(0)), if (fee.due > 0) colors.danger else colors.success)
                    if (fee.overdue > 0) KeyValue("Overdue now", money.format(fee.overdue), colors.danger)
                    fee.nextDue?.let { KeyValue("Next installment", "${money.format(fee.nextAmount)} on ${Dates.full(it)}") }
                }
                AmountInput(amount, { amount = it }, "Amount received *", tag = "collect_amount", supporting = if (value > 0) Words.amount(value, major, minor) else null)
                if (value > fee.due && fee.due >= 0) Text("That is ${money.format(value - fee.due)} more than the due amount; it will show as paid in advance.", color = colors.warning, style = MaterialTheme.typography.bodySmall)
                DateInput(date, { date = it }, "Date")
                FieldLabel("Paid by")
                ChoiceChips(PayMethod.entries, method, { method = it }, { it.label }, tagPrefix = "method")
                TextInput(note, { note = it }, "Note", placeholder = "e.g. October installment, bKash TrxID")
                Text("After this: due ${money.format((fee.due - value).coerceAtLeast(0))}", style = MaterialTheme.typography.titleSmall)
                Button(
                    enabled = value > 0 && data.role.office,
                    onClick = {
                        val p = Payment(
                            id = paymentId, enrollmentId = enrollment.id, studentId = enrollment.studentId, amount = value,
                            date = date, method = method, receiptNo = data.nextReceiptNo(date), note = note.trim(),
                            receivedBy = data.me?.displayName.orEmpty(),
                        )
                        app.store.put(Cols.payments, p)
                        notify("${money.format(value)} received · ${p.receiptNo}")
                        nav.replace(Routes.receipt(p.id))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_payment"),
                ) { Text("Save and make receipt") }
            }
        }
    }
    if (picking) {
        val withAdmissions = remember(data) { data.students.filter { s -> data.enrollmentsByStudent[s.id].orEmpty().any { it.status != EnrollmentStatus.DROPPED } } }
        PickerDialog(
            title = "Collect from",
            items = withAdmissions,
            onPick = { s ->
                pickedStudent = s.id
                pickedEnrollment = null
                picking = false
            },
            onDismiss = { picking = false },
            label = { it.name },
            subtitle = { s ->
                val due = data.enrollmentsByStudent[s.id].orEmpty().filter { it.status != EnrollmentStatus.DROPPED }.sumOf { data.fee(it).due.coerceAtLeast(0) }
                listOf(s.regNo, if (due > 0) "due ${money.format(due)}" else "paid up").filter { it.isNotBlank() }.joinToString(" · ")
            },
            searchText = { it.searchText() },
            key = { it.id },
        )
    }
}

// ---- One receipt ------------------------------------------------------------

@Composable
fun ReceiptScreen(id: String) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    val p = data.paymentById[id]
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (p == null) {
        Screen("Receipt", onBack = nav::back) { EmptyState("🔍", "Receipt not found", "It may have been deleted on another device.") }
        return
    }
    val e = data.enrollmentById[p.enrollmentId]
    val student = data.studentById[p.studentId]
    val fee = e?.let { data.fee(it) }
    val (major, minor) = Words.names(data.center?.currencyCode ?: "BDT")

    fun print() {
        val activity = context.findActivity() ?: return
        app.appLock.skipNextLock()
        Printer.print(
            activity, "Receipt ${p.receiptNo}",
            money.printouts(data.center).receipt(p, student, e, e?.let { data.courseById[it.courseId] }, e?.let { data.batchById[it.batchId] }, fee),
        )
    }

    fun shareText(): String = buildString {
        append("Money receipt ${p.receiptNo} — ${data.center?.name.orEmpty()}\n")
        append("Received ${money.format(p.amount)} (${Words.amount(p.amount, major, minor)}) from ${student?.name.orEmpty()}")
        if (e != null) append(" for ${data.courseName(e.courseId)} (${data.batchName(e.batchId)})")
        append(" on ${Dates.full(p.date)} by ${p.method.label}.\n")
        if (fee != null) append("Paid so far ${money.format(fee.paid)} of ${money.format(fee.payable)}; due ${money.format(fee.due.coerceAtLeast(0))}.\n")
        append("Thank you.")
    }

    ListScreen(
        title = "Receipt ${p.receiptNo}",
        onBack = nav::back,
        actions = {
            if (data.role.manages) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit receipt") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete receipt") }
            }
        },
    ) {
        item {
            HeroCard(Modifier.testTag("receipt_card")) {
                Text(data.center?.name.orEmpty(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(money.format(p.amount), color = Color.White, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("receipt_amount"))
                Text(Words.amount(p.amount, major, minor), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SkillCard {
                KeyValue("Receipt No", p.receiptNo)
                KeyValue("Date", Dates.full(p.date))
                KeyValue("Student", listOfNotNull(student?.name, student?.regNo).joinToString(" · "))
                if (e != null) KeyValue("Course", "${data.courseName(e.courseId)} · ${data.batchName(e.batchId)} · Roll ${e.rollNo}")
                KeyValue("Paid by", p.method.label)
                if (p.note.isNotBlank()) KeyValue("Note", p.note)
                KeyValue("Received by", p.receivedBy)
                if (fee != null) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    KeyValue("Paid so far", money.format(fee.paid), colors.success)
                    KeyValue("Still due", money.format(fee.due.coerceAtLeast(0)), if (fee.due > 0) colors.danger else colors.success)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = ::print, modifier = Modifier.weight(1f).testTag("print_receipt")) {
                    Icon(Icons.Filled.Print, null)
                    Spacer(Modifier.padding(3.dp))
                    Text("Print / PDF")
                }
                OutlinedButton(
                    onClick = {
                        val phone = student?.phone?.ifBlank { null } ?: student?.guardianPhone
                        if (!phone.isNullOrBlank()) Share.whatsApp(context, phone, shareText()) else Share.text(context, shareText())
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, null)
                    Spacer(Modifier.padding(3.dp))
                    Text("Send")
                }
            }
        }
        if (e != null) item { OutlinedButton(onClick = { nav.go(Routes.enrollment(e.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Open admission") } }
    }

    if (editing) {
        var amount by remember { mutableStateOf(Money.toInput(p.amount)) }
        var date by remember { mutableStateOf(p.date) }
        var method by remember { mutableStateOf(p.method) }
        var note by remember { mutableStateOf(p.note) }
        FormDialog(
            title = "Edit receipt",
            onDismiss = { editing = false },
            saveEnabled = (Money.parse(amount) ?: 0L) > 0,
            onSave = {
                app.store.put(Cols.payments, p.copy(amount = Money.parse(amount) ?: p.amount, date = date, method = method, note = note.trim()))
                editing = false
                notify("Receipt updated")
            },
        ) {
            AmountInput(amount, { amount = it }, "Amount")
            DateInput(date, { date = it }, "Date")
            ChoiceChips(PayMethod.entries, method, { method = it }, { it.label })
            TextInput(note, { note = it }, "Note")
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete receipt ${p.receiptNo}?",
            text = "${money.format(p.amount)} will no longer count as paid. Do this only for a receipt made by mistake.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.payments, p.id)
                confirmDelete = false
                notify("Receipt deleted")
                nav.back()
            },
        )
    }
}
