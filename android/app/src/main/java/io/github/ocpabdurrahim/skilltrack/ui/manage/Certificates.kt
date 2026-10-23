package io.github.ocpabdurrahim.skilltrack.ui.manage

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import io.github.ocpabdurrahim.skilltrack.data.Certificate
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.print.Printer
import io.github.ocpabdurrahim.skilltrack.system.findActivity
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.SearchField
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.printouts
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme

private enum class CertTab(val label: String) { ISSUED("Issued"), READY("Ready to issue") }

/** The register of issued certificates, and completed students still waiting for one. */
@Composable
fun CertificatesScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    var tab by rememberSaveable { mutableStateOf(CertTab.ISSUED) }
    var query by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<Certificate?>(null) }

    fun print(c: Certificate) {
        val activity = context.findActivity() ?: return
        val e = data.enrollmentById[c.enrollmentId]
        val batch = e?.let { data.batchById[it.batchId] }
        app.appLock.skipNextLock()
        Printer.print(
            activity, "Certificate ${c.certNo}",
            money.printouts(data.center).certificate(
                c, data.studentById[c.studentId], e, e?.let { data.courseById[it.courseId] }, batch,
                batch?.instructorId?.let { data.instructorById[it] },
            ),
            landscape = true,
        )
    }

    val q = query.trim().lowercase()
    val issued = data.certificates.filter { c ->
        q.isEmpty() || c.certNo.lowercase().contains(q) || data.studentName(c.studentId).lowercase().contains(q)
    }
    val ready = data.enrollments.filter { e ->
        e.status == EnrollmentStatus.COMPLETED && data.certificatesByEnrollment[e.id] == null &&
            (q.isEmpty() || data.studentName(e.studentId).lowercase().contains(q))
    }

    ListScreen(title = "Certificates", subtitle = "${data.certificates.size} issued", onBack = nav::back) {
        item { ChoiceChips(CertTab.entries, tab, { tab = it }, { if (it == CertTab.READY) "${it.label} (${ready.size})" else it.label }) }
        item { SearchField(query, { query = it }, "Student or certificate no.") }
        if (tab == CertTab.ISSUED) {
            if (issued.isEmpty()) item { EmptyState("🏅", "No certificates yet", "Issue one from a completed student's admission page, or from \"Ready to issue\".") }
            items(issued, key = { it.id }) { c ->
                val e = data.enrollmentById[c.enrollmentId]
                SkillCard(onClick = { e?.let { nav.go(Routes.enrollment(it.id)) } }, modifier = Modifier.testTag("certificate_row")) {
                    ListRow(
                        title = data.studentName(c.studentId),
                        subtitle = listOfNotNull(c.certNo, e?.let { data.courseName(it.courseId) }, Dates.full(c.issueDate), c.grade.ifBlank { null }?.let { "Grade $it" }).joinToString(" · "),
                        trailing = {
                            Row {
                                IconButton(onClick = { print(c) }) { Icon(Icons.Filled.Print, contentDescription = "Print") }
                                if (data.role.manages) IconButton(onClick = { removing = c }) { Icon(Icons.Filled.Delete, contentDescription = "Cancel certificate") }
                            }
                        },
                    )
                }
            }
        } else {
            if (ready.isEmpty()) item { EmptyState("✅", "Nobody waiting", "Students appear here once their admission is marked Completed.") }
            items(ready, key = { "r" + it.id }) { e ->
                val result = data.result(e)
                SkillCard(modifier = Modifier.testTag("ready_row")) {
                    ListRow(
                        title = data.studentName(e.studentId),
                        subtitle = "${data.courseName(e.courseId)} · ${data.batchName(e.batchId)}" + (result?.let { " · ${it.grade.letter}" } ?: ""),
                        trailing = {
                            if (result?.failed == true) StatusChip("Failed", colors.danger)
                            else if (data.role.office) {
                                androidx.compose.material3.TextButton(onClick = {
                                    val c = Certificate(
                                        enrollmentId = e.id, studentId = e.studentId, certNo = data.nextCertNo(data.today),
                                        issueDate = data.today, grade = result?.grade?.letter.orEmpty(),
                                    )
                                    app.store.put(Cols.certificates, c)
                                    notify("Certificate ${c.certNo} issued")
                                    print(c)
                                }) { androidx.compose.material3.Text("Issue") }
                            }
                        },
                    )
                }
            }
        }
    }

    removing?.let { c ->
        ConfirmDialog(
            title = "Cancel certificate ${c.certNo}?",
            text = "It will be removed from the register, and the student can be issued a new one.",
            confirmLabel = "Cancel certificate",
            destructive = true,
            onDismiss = { removing = null },
            onConfirm = {
                app.store.remove(Cols.certificates, c.id)
                removing = null
            },
        )
    }
}
