package io.github.ocpabdurrahim.skilltrack.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Expense
import io.github.ocpabdurrahim.skilltrack.data.ExpenseHead
import io.github.ocpabdurrahim.skilltrack.data.PayMethod
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.system.Csv
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountInput
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.MonthSelector
import io.github.ocpabdurrahim.skilltrack.ui.components.ProgressBar
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatTile
import io.github.ocpabdurrahim.skilltrack.ui.components.SwitchRow
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.components.TileRow
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.YearMonth

@Composable
fun ExpensesScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val ym = YearMonth.parse(month)
    val list = remember(data, month) { data.expenses.filter { Dates.inMonth(it.date, ym) } }
    val instructorPay = data.instructorPayTotal(ym)
    val byHead = remember(list) { list.groupBy { it.headId }.mapValues { (_, l) -> l.sumOf { it.amount } }.entries.sortedByDescending { it.value } }
    val total = list.sumOf { it.amount }

    ListScreen(
        title = "Expenses",
        onBack = nav::back,
        actions = {
            IconButton(onClick = { nav.go(Routes.HEADS) }) { Icon(Icons.Filled.Category, contentDescription = "Expense heads") }
            IconButton(onClick = {
                val csv = Csv.build(
                    listOf("Date", "Head", "Amount", "Method", "Paid to", "Note"),
                    list.map { e -> listOf(e.date, data.headById[e.headId]?.name, Money.toInput(e.amount), e.method.label, e.paidTo, e.note) },
                )
                Share.file(context, "expenses-$month.csv", "text/csv", csv)
            }) { Icon(Icons.Filled.FileDownload, contentDescription = "Export to CSV") }
        },
        fab = {
            if (data.role.office) {
                ExtendedFloatingActionButton(
                    onClick = { editing = Expense(date = data.today, headId = data.expenseHeads.firstOrNull { !it.archived }?.id.orEmpty()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add expense") },
                    modifier = Modifier.testTag("fab_expense"),
                )
            }
        },
    ) {
        item { MonthSelector(ym, { month = it.toString() }) }
        item {
            TileRow {
                StatTile("Expenses", money.format(total), colors.danger, Modifier.weight(1f), tag = "expense_total")
                StatTile("Instructor pay", money.format(instructorPay), colors.warning, Modifier.weight(1f), onClick = { nav.go(Routes.INSTRUCTORS) })
            }
        }
        if (byHead.isNotEmpty()) {
            item {
                SkillCard {
                    Text("By head", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val max = byHead.first().value.coerceAtLeast(1)
                    byHead.forEach { (headId, amount) ->
                        val head = data.headById[headId]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(head?.emoji ?: "📦", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(head?.name ?: "Other", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(money.format(amount), style = MaterialTheme.typography.bodyMedium)
                                }
                                ProgressBar(amount.toFloat() / max, colors.danger, height = 6.dp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        item { SectionHeader("Entries") }
        if (list.isEmpty()) item { EmptyState("💸", "No expenses in ${Dates.month(ym)}", "Record rent, bills, internet, salaries and other spending to see your real profit.") }
        items(list, key = { it.id }) { e ->
            val head = data.headById[e.headId]
            SkillCard(onClick = { if (data.role.office) editing = e }, modifier = Modifier.testTag("expense_row")) {
                ListRow(
                    title = head?.name ?: "Other",
                    subtitle = listOfNotNull(Dates.relative(e.date), e.paidTo.ifBlank { null }, e.note.ifBlank { null }, e.method.label).joinToString(" · "),
                    leading = { Text(head?.emoji ?: "📦", fontSize = 24.sp) },
                    trailing = { AmountColumn(money.format(e.amount), null, colors.danger) },
                )
            }
        }
    }

    editing?.let { e ->
        val isNew = data.expenses.none { it.id == e.id }
        var amount by remember(e.id) { mutableStateOf(if (e.amount == 0L) "" else Money.toInput(e.amount)) }
        var headId by remember(e.id) { mutableStateOf(e.headId) }
        var date by remember(e.id) { mutableStateOf(e.date) }
        var method by remember(e.id) { mutableStateOf(e.method) }
        var paidTo by remember(e.id) { mutableStateOf(e.paidTo) }
        var note by remember(e.id) { mutableStateOf(e.note) }
        FormDialog(
            title = if (isNew) "Add expense" else "Edit expense",
            onDismiss = { editing = null },
            saveEnabled = (Money.parse(amount) ?: 0L) > 0 && headId.isNotBlank(),
            onSave = {
                app.store.put(Cols.expenses, e.copy(amount = Money.parse(amount) ?: 0L, headId = headId, date = date, method = method, paidTo = paidTo.trim(), note = note.trim()))
                editing = null
                notify("Expense saved")
            },
            onDelete = if (isNew) null else ({
                app.store.remove(Cols.expenses, e.id)
                editing = null
                notify("Expense deleted")
            }),
        ) {
            AmountInput(amount, { amount = it }, "Amount *", tag = "expense_amount")
            FieldLabel("Head")
            ChoiceChips(data.expenseHeads.filter { !it.archived || it.id == headId }, data.headById[headId], { headId = it.id }, { "${it.emoji} ${it.name}" }, tagPrefix = "head")
            DateInput(date, { date = it }, "Date")
            ChoiceChips(PayMethod.entries, method, { method = it }, { it.label })
            TextInput(paidTo, { paidTo = it }, "Paid to", placeholder = "e.g. landlord, DESCO, ISP")
            TextInput(note, { note = it }, "Note")
        }
    }
}

@Composable
fun ExpenseHeadsScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    var editing by remember { mutableStateOf<ExpenseHead?>(null) }
    ListScreen(
        title = "Expense heads",
        onBack = nav::back,
        fab = {
            if (data.role.office) {
                ExtendedFloatingActionButton(
                    onClick = { editing = ExpenseHead() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New head") },
                )
            }
        },
    ) {
        items(data.expenseHeads, key = { it.id }) { h ->
            val used = data.expenses.count { it.headId == h.id }
            SkillCard(onClick = { if (data.role.office) editing = h }) {
                ListRow(
                    title = h.name + if (h.archived) " (archived)" else "",
                    subtitle = "$used entries",
                    leading = { Text(h.emoji, fontSize = 24.sp) },
                )
            }
        }
    }
    editing?.let { h ->
        val isNew = data.expenseHeads.none { it.id == h.id }
        val used = data.expenses.any { it.headId == h.id }
        var name by remember(h.id) { mutableStateOf(h.name) }
        var emoji by remember(h.id) { mutableStateOf(h.emoji) }
        var archived by remember(h.id) { mutableStateOf(h.archived) }
        FormDialog(
            title = if (isNew) "New expense head" else "Edit expense head",
            onDismiss = { editing = null },
            saveEnabled = name.isNotBlank(),
            onSave = {
                app.store.put(Cols.expenseHeads, h.copy(name = name.trim(), emoji = emoji.trim().ifBlank { "📦" }, archived = archived))
                editing = null
            },
            onDelete = if (isNew || used) null else ({
                app.store.remove(Cols.expenseHeads, h.id)
                editing = null
            }),
        ) {
            TextInput(name, { name = it }, "Name *")
            TextInput(emoji, { if (it.length <= 8) emoji = it }, "Emoji")
            ChoiceChips(listOf("🏠", "💡", "🌐", "👥", "📣", "📝", "💻", "🔧", "🚕", "☕", "🧹", "📦"), emoji, { emoji = it }, { it })
            if (!isNew) SwitchRow("Archived", archived, { archived = it }, subtitle = if (used) "Heads with entries are archived, not deleted." else null)
        }
    }
}
