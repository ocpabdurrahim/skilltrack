package io.github.ocpabdurrahim.skilltrack.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.data.EnquiryStatus
import io.github.ocpabdurrahim.skilltrack.data.Role
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.AmountColumn
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.HeroCard
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatTile
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.components.StudentAvatar
import io.github.ocpabdurrahim.skilltrack.ui.components.TileRow
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.LocalDate
import java.time.YearMonth

/** How the phone's copy stands against the server, in a few words. */
@Composable
fun SyncState(data: CenterData) {
    val colors = SkillTheme.colors
    val (icon, text, color) = when {
        data.error != null -> Triple(Icons.Filled.ErrorOutline, "Sync problem", MaterialTheme.colorScheme.error)
        data.pending > 0 -> Triple(Icons.Filled.CloudOff, "${data.pending} change${if (data.pending == 1) "" else "s"} waiting to sync", colors.warning)
        !data.synced -> Triple(Icons.Filled.CloudSync, "Connecting…", colors.warning)
        else -> Triple(Icons.Filled.CloudDone, "Synced", colors.success)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("sync_state")) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun HomeScreen() {
    val data = centerData()
    val nav = LocalNav.current
    val money = LocalMoney.current
    val colors = SkillTheme.colors
    val context = LocalContext.current
    val role = data.role
    val today = data.today
    val todayDate = remember(today) { LocalDate.parse(today) }
    val month = remember(today) { YearMonth.from(todayDate) }

    val classes = remember(data) {
        val mine = data.me?.instructorId
        data.runningBatches
            .filter { Academics.isClassDay(it, todayDate) }
            .sortedWith(compareBy({ mine != null && it.instructorId != mine }, { it.time }, { it.name }))
    }
    val followUps = remember(data) {
        data.enquiries.filter { it.status == EnquiryStatus.OPEN && it.followUp.isNotEmpty() && it.followUp <= today }
            .sortedBy { it.followUp }
    }
    val overdue = remember(data) {
        data.countedEnrollments.map { it to data.fee(it) }.filter { it.second.overdue > 0 }
            .sortedByDescending { it.second.overdue }
    }

    ListScreen(
        title = data.center?.name ?: "SkillTrack",
        subtitle = "Hello, ${data.me?.name?.ifBlank { null } ?: data.me?.email ?: ""} · ${role.label}",
    ) {
        item { SyncState(data) }
        if (!data.synced && data.students.isEmpty() && data.courses.isEmpty()) {
            item {
                SkillCard {
                    Text("Downloading your center's records…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            HeroCard(Modifier.testTag("home_hero")) {
                if (role.seesMoney) {
                    Text("Collected in ${Dates.month(month)}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        money.format(data.income(month)),
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.testTag("month_collection"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Today ${money.format(data.collectedOn(today))} · Overdue ${money.format(data.totalOverdue)}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("Today, ${Dates.relative(today)}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (classes.isEmpty()) "No classes" else "${classes.size} class${if (classes.size == 1) "" else "es"}",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        "${data.runningBatches.size} running batches · ${data.activeStudentIds.size} active students",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item { QuickActions(role) }

        if (data.courses.isEmpty() && role.manages) {
            item {
                SkillCard {
                    Text("Get started", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Add your courses with their fees.\n2. Create a batch for each course (days, time, instructor).\n3. Admit students into batches and collect fees.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { nav.go(Routes.COURSES) }, modifier = Modifier.testTag("start_courses")) { Text("Add a course") }
                }
            }
        }

        item {
            TileRow {
                StatTile("Active students", data.activeStudentIds.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f), onClick = { nav.tab(Routes.STUDENTS) }, tag = "tile_students")
                StatTile("Running batches", data.runningBatches.size.toString(), colors.info, Modifier.weight(1f), onClick = { nav.tab(Routes.BATCHES) })
            }
        }
        if (role.seesMoney) {
            item {
                TileRow {
                    StatTile("Overdue fees", money.format(data.totalOverdue), colors.danger, Modifier.weight(1f), onClick = { nav.go(Routes.DUES) }, tag = "tile_overdue")
                    StatTile("Expenses this month", money.format(data.expenseTotal(month) + data.instructorPayTotal(month)), colors.warning, Modifier.weight(1f), onClick = { nav.go(Routes.EXPENSES) })
                }
            }
            item {
                val profit = data.profit(month)
                TileRow {
                    StatTile("Profit this month", money.format(profit), if (profit >= 0) colors.success else colors.danger, Modifier.weight(1f), onClick = { nav.go(Routes.REPORTS) })
                    StatTile("Open enquiries", data.enquiries.count { it.status == EnquiryStatus.OPEN }.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f), onClick = { nav.go(Routes.ENQUIRIES) })
                }
            }
        }

        item { SectionHeader("Today's classes") }
        if (classes.isEmpty()) {
            item {
                SkillCard { Text("No running batch has a class today.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            items(classes, key = { "class_" + it.id }) { b ->
                val record = data.attendanceByBatch[b.id].orEmpty().firstOrNull { it.date == today }
                val roll = Academics.rollFor(data.enrollmentsByBatch[b.id].orEmpty(), today)
                SkillCard(onClick = { nav.go(Routes.attendance(b.id, today)) }, modifier = Modifier.testTag("today_class")) {
                    ListRow(
                        title = b.name,
                        subtitle = listOf(data.courseName(b.courseId), b.time, b.room).filter { it.isNotBlank() }.joinToString(" · "),
                        trailing = {
                            if (record != null) {
                                val present = roll.count { record.marks[it.id]?.let { m -> m.code == "P" || m.code == "L" } == true }
                                StatusChip("✓ $present/${roll.size}", colors.success)
                            } else {
                                StatusChip("Mark", colors.warning)
                            }
                        },
                    )
                }
            }
        }

        if (role.office && followUps.isNotEmpty()) {
            item { SectionHeader("Follow-ups due", action = "All", onAction = { nav.go(Routes.ENQUIRIES) }) }
            items(followUps.take(5), key = { "fu_" + it.id }) { q ->
                SkillCard(onClick = { nav.go(Routes.enquiryEdit(q.id)) }) {
                    ListRow(
                        title = q.name,
                        subtitle = listOf(data.courseName(q.courseId).takeIf { q.courseId != null }, "Follow up ${Dates.relative(q.followUp)}").filterNotNull().joinToString(" · "),
                        trailing = {
                            if (q.phone.isNotBlank()) IconButton(onClick = { Share.call(context, q.phone) }) { Icon(Icons.Filled.Call, contentDescription = "Call") }
                        },
                    )
                }
            }
        }

        if (role.seesMoney && overdue.isNotEmpty()) {
            item { SectionHeader("Overdue fees", action = "All dues", onAction = { nav.go(Routes.DUES) }) }
            items(overdue.take(5), key = { "od_" + it.first.id }) { (e, fee) ->
                val student = data.studentById[e.studentId]
                SkillCard(onClick = { nav.go(Routes.enrollment(e.id)) }) {
                    ListRow(
                        title = student?.name ?: "Unknown student",
                        subtitle = data.batchName(e.batchId) + (fee.nextDue?.let { " · due since ${Dates.short(it)}" } ?: ""),
                        leading = { StudentAvatar(e.studentId, student?.name ?: "?", size = 36.dp) },
                        trailing = { AmountColumn(money.format(fee.overdue), "of ${money.format(fee.due)}", colors.danger) },
                    )
                }
            }
        }

        if (role.seesMoney) {
            item { SectionHeader("Recent payments", action = "All", onAction = { nav.tab(Routes.FEES) }) }
            if (data.payments.isEmpty()) {
                item { EmptyState("🧾", "No payments yet", "Collected fees appear here with their receipts.") }
            } else {
                items(data.payments.take(6), key = { "pay_" + it.id }) { p ->
                    SkillCard(onClick = { nav.go(Routes.receipt(p.id)) }) {
                        ListRow(
                            title = data.studentName(p.studentId),
                            subtitle = "${p.receiptNo} · ${p.method.label} · ${Dates.relative(p.date)}",
                            trailing = { AmountColumn(money.format(p.amount), null, colors.success) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(role: Role) {
    val nav = LocalNav.current
    data class Action(val label: String, val icon: ImageVector, val tag: String, val go: () -> Unit)

    val actions = buildList {
        if (role.office) add(Action("New admission", Icons.Filled.PersonAdd, "qa_admission") { nav.go(Routes.admission()) })
        if (role.office) add(Action("Collect fee", Icons.Filled.Payments, "qa_collect") { nav.go(Routes.collect()) })
        if (role.academic) add(Action("Attendance", Icons.Filled.EventAvailable, "qa_attendance") { nav.tab(Routes.BATCHES) })
        if (role.office) add(Action("Enquiry", Icons.Filled.SupportAgent, "qa_enquiry") { nav.go(Routes.enquiryEdit()) })
        if (role.office) add(Action("Expense", Icons.AutoMirrored.Filled.ReceiptLong, "qa_expense") { nav.go(Routes.EXPENSES) })
        if (role.manages) add(Action("Reports", Icons.Filled.CheckCircle, "qa_reports") { nav.go(Routes.REPORTS) })
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { a ->
            AssistChip(
                onClick = a.go,
                label = { Text(a.label, fontWeight = FontWeight.Medium) },
                leadingIcon = { Icon(a.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag(a.tag),
            )
        }
    }
}
