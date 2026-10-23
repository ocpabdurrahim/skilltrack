package io.github.ocpabdurrahim.skilltrack.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.Enquiry
import io.github.ocpabdurrahim.skilltrack.data.EnquirySource
import io.github.ocpabdurrahim.skilltrack.data.EnquiryStatus
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.DateInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SearchField
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatTile
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.components.TileRow
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme

@Composable
fun EnquiriesScreen() {
    val data = centerData()
    val nav = LocalNav.current
    val context = LocalContext.current
    val colors = SkillTheme.colors
    var status by rememberSaveable { mutableStateOf(EnquiryStatus.OPEN) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(data, status, query) {
        val q = query.trim().lowercase()
        data.enquiries.filter { it.status == status && (q.isEmpty() || "${it.name} ${it.phone} ${it.note}".lowercase().contains(q)) }
            .let { list -> if (status == EnquiryStatus.OPEN) list.sortedWith(compareBy({ it.followUp.isEmpty() }, { it.followUp })) else list }
    }
    val total = data.enquiries.size
    val converted = data.enquiries.count { it.status == EnquiryStatus.CONVERTED }

    ListScreen(
        title = "Enquiries",
        onBack = nav::back,
        fab = {
            if (data.role.office) {
                ExtendedFloatingActionButton(
                    onClick = { nav.go(Routes.enquiryEdit()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New enquiry") },
                    modifier = Modifier.testTag("fab_enquiry"),
                )
            }
        },
    ) {
        item {
            TileRow {
                StatTile("Open", data.enquiries.count { it.status == EnquiryStatus.OPEN }.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("Converted", if (total > 0) "$converted (${converted * 100 / total}%)" else "0", colors.success, Modifier.weight(1f))
            }
        }
        item { SearchField(query, { query = it }, "Name, phone or note") }
        item { ChoiceChips(EnquiryStatus.entries, status, { status = it }, { it.label }) }
        if (shown.isEmpty()) item { EmptyState("📞", "No ${status.label.lowercase()} enquiries", "Log walk-ins and phone calls here, set a follow-up date, and turn them into admissions.") }
        items(shown, key = { it.id }) { q ->
            val due = q.status == EnquiryStatus.OPEN && q.followUp.isNotEmpty() && q.followUp <= data.today
            SkillCard(onClick = { nav.go(Routes.enquiryEdit(q.id)) }, modifier = Modifier.testTag("enquiry_row")) {
                ListRow(
                    title = q.name,
                    subtitle = listOfNotNull(
                        q.courseId?.let { data.courseName(it) },
                        q.source.label,
                        "came ${Dates.relative(q.date)}",
                        q.note.ifBlank { null },
                    ).joinToString(" · "),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (q.status == EnquiryStatus.OPEN && q.followUp.isNotEmpty()) {
                                StatusChip(Dates.relative(q.followUp), if (due) colors.danger else colors.info)
                            }
                            if (q.phone.isNotBlank()) IconButton(onClick = { Share.call(context, q.phone) }) { Icon(Icons.Filled.Call, contentDescription = "Call") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun EnquiryEditScreen(id: String?) {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val existing = id?.let { key -> data.enquiries.firstOrNull { it.id == key } }
    val newId = rememberSaveable { Ids.new() }
    val base = existing ?: Enquiry(id = newId, date = data.today, followUp = Dates.today().plusDays(2).toString())

    var name by rememberSaveable { mutableStateOf(base.name) }
    var phone by rememberSaveable { mutableStateOf(base.phone) }
    var courseId by rememberSaveable { mutableStateOf(base.courseId) }
    var source by rememberSaveable { mutableStateOf(base.source) }
    var date by rememberSaveable { mutableStateOf(base.date) }
    var followUp by rememberSaveable { mutableStateOf(base.followUp) }
    var status by rememberSaveable { mutableStateOf(base.status) }
    var note by rememberSaveable { mutableStateOf(base.note) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build() = base.copy(
        name = name.trim(), phone = phone.trim(), courseId = courseId, source = source, date = date,
        followUp = followUp, status = status, note = note.trim(),
    )

    Screen(title = if (existing == null) "New enquiry" else "Enquiry", onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NameInput(name, { name = it }, "Name *", tag = "enquiry_name")
            PhoneInput(phone, { phone = it }, "Phone", tag = "enquiry_phone")
            Dropdown(
                label = "Interested in",
                options = data.courses.filter { !it.archived },
                selected = courseId?.let { data.courseById[it] },
                onSelect = { c: Course? -> courseId = c?.id },
                optionLabel = { it.name },
                noneLabel = "Not sure yet",
            )
            FieldLabel("How they found you")
            ChoiceChips(EnquirySource.entries, source, { source = it }, { it.label })
            FieldPair(
                { m -> DateInput(date, { date = it }, "Came on", m) },
                { m -> DateInput(followUp, { followUp = it }, "Follow up on", m, clearable = true) },
            )
            if (existing != null) {
                FieldLabel("Status")
                ChoiceChips(EnquiryStatus.entries, status, { status = it }, { it.label })
            }
            TextInput(note, { note = it }, "Notes", singleLine = false, minLines = 3, maxLength = 2000, placeholder = "What they asked, what you told them, when to call…")
            if (phone.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Share.call(context, phone) }) { Text("Call") }
                    OutlinedButton(onClick = {
                        val course = courseId?.let { data.courseName(it) } ?: "our courses"
                        Share.whatsApp(context, phone, "Assalamu alaikum $name, thank you for your interest in $course at ${data.center?.name.orEmpty()}. ")
                    }) {
                        Icon(Icons.Filled.Chat, null)
                        Text("  WhatsApp")
                    }
                }
            }
            Button(
                enabled = name.isNotBlank() && data.role.office,
                onClick = {
                    val q = build()
                    app.store.put(Cols.enquiries, q)
                    notify("Enquiry saved")
                    nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_enquiry"),
            ) { Text("Save") }
            if (data.role.office && status != EnquiryStatus.CONVERTED) {
                OutlinedButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val q = build()
                        app.store.put(Cols.enquiries, q)
                        nav.replace(Routes.admission(enquiryId = q.id))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("convert_enquiry"),
                ) { Text("Admit as a student") }
            }
            if (existing?.studentId != null) {
                TextButton(onClick = { nav.go(Routes.student(existing.studentId)) }) { Text("Open the student") }
            }
            if (existing != null && data.role.office) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete enquiry", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete this enquiry?",
            text = "${existing.name} will be removed from the list. Mark it Lost instead to keep it for your conversion numbers.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                app.store.remove(Cols.enquiries, existing.id)
                confirmDelete = false
                nav.back()
            },
        )
    }
}
