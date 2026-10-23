package io.github.ocpabdurrahim.skilltrack.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.Member
import io.github.ocpabdurrahim.skilltrack.data.Role
import io.github.ocpabdurrahim.skilltrack.domain.Grouping
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.system.Images
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.Avatar
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.Dropdown
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldLabel
import io.github.ocpabdurrahim.skilltrack.ui.components.FieldPair
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.components.SwitchRow
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun StaffScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val colors = SkillTheme.colors
    var editing by remember { mutableStateOf<Member?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    val me = data.me

    ListScreen(
        title = "Staff",
        subtitle = "${data.members.count { it.active }} with access",
        onBack = nav::back,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { editing = Member(id = "", role = Role.OFFICE, addedBy = me?.email.orEmpty()) },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Add staff") },
                modifier = Modifier.testTag("fab_staff"),
            )
        },
    ) {
        item {
            SkillCard {
                Text(
                    "Staff sign in with their own Google account. Add the exact Gmail address they use; they will then see this center after signing in to SkillTrack on their phone or at ${webAppUrl().removePrefix("https://")}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Role.entries.forEach { r -> Text("• ${r.label}: ${r.blurb}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        items(data.members, key = { it.id }) { m ->
            val isOwner = m.role == Role.OWNER
            SkillCard(onClick = { if (!isOwner || m.id == me?.id) editing = m }, modifier = Modifier.testTag("staff_row")) {
                ListRow(
                    title = m.displayName + if (m.id == me?.id) " (you)" else "",
                    subtitle = m.email + (m.instructorId?.let { id -> data.instructorById[id]?.let { " · ${it.name}" } } ?: ""),
                    leading = { Avatar(m.displayName) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            StatusChip(m.role.label, if (isOwner) colors.warning else MaterialTheme.colorScheme.primary)
                            if (!m.active) StatusChip("No access", colors.danger)
                        }
                    },
                )
            }
        }
    }

    editing?.let { m ->
        MemberDialog(
            member = m,
            isNew = m.id.isEmpty(),
            instructors = data.instructors.filter { !it.archived },
            existingEmails = data.members.map { it.id }.toSet(),
            onDismiss = { editing = null },
            onRemove = if (m.id.isEmpty() || m.role == Role.OWNER) null else ({ removing = m; editing = null }),
            onSave = { saved ->
                app.store.saveMember(saved)
                editing = null
                notify("${saved.displayName} saved")
            },
        )
    }
    removing?.let { m ->
        ConfirmDialog(
            title = "Remove ${m.displayName}?",
            text = "${m.email} will lose access to this center straight away. Records they made stay.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { removing = null },
            onConfirm = {
                app.store.removeMember(m.id)
                removing = null
                notify("${m.displayName} removed")
            },
        )
    }
}

@Composable
private fun MemberDialog(
    member: Member,
    isNew: Boolean,
    instructors: List<Instructor>,
    existingEmails: Set<String>,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
    onSave: (Member) -> Unit,
) {
    val isOwner = member.role == Role.OWNER
    var email by remember { mutableStateOf(member.email) }
    var name by remember { mutableStateOf(member.name) }
    var role by remember { mutableStateOf(member.role) }
    var active by remember { mutableStateOf(member.active) }
    var instructorId by remember { mutableStateOf(member.instructorId) }
    val cleanEmail = email.trim().lowercase()
    val problem = when {
        isNew && !EMAIL.matches(cleanEmail) -> "Enter their Google email address."
        isNew && cleanEmail in existingEmails -> "That email is already on the staff list."
        else -> null
    }
    FormDialog(
        title = if (isNew) "Add staff" else if (isOwner) "Your owner record" else "Edit staff",
        onDismiss = onDismiss,
        saveEnabled = problem == null,
        onSave = {
            val id = if (isNew) cleanEmail else member.id
            onSave(
                member.copy(
                    id = id, email = id, name = name.trim(), role = if (isOwner) Role.OWNER else role,
                    active = isOwner || active, instructorId = instructorId,
                    createdAt = if (isNew) System.currentTimeMillis() else member.createdAt,
                ),
            )
        },
        onDelete = onRemove,
        deleteLabel = "Remove",
    ) {
        if (isNew) {
            TextInput(email, { email = it }, "Google email *", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None, tag = "staff_email")
        } else {
            Text(member.email, style = MaterialTheme.typography.bodyLarge)
        }
        NameInput(name, { name = it }, "Name", tag = "staff_name")
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!isOwner) {
            FieldLabel("Role")
            ChoiceChips(listOf(Role.ADMIN, Role.OFFICE, Role.TEACHER), role, { role = it }, { it.label }, tagPrefix = "role")
            Text(role.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (instructors.isNotEmpty()) {
                Dropdown(
                    label = "Is this instructor",
                    options = instructors,
                    selected = instructors.firstOrNull { it.id == instructorId },
                    onSelect = { i: Instructor? -> instructorId = i?.id },
                    optionLabel = { it.name },
                    noneLabel = "Not an instructor",
                )
            }
            if (!isNew) SwitchRow("Has access", active, { active = it }, subtitle = "Turn off to block them without deleting the record.")
        }
    }
}

@Composable
fun CenterSettingsScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val center = data.center ?: return

    var name by rememberSaveable { mutableStateOf(center.name) }
    var tagline by rememberSaveable { mutableStateOf(center.tagline) }
    var address by rememberSaveable { mutableStateOf(center.address) }
    var phone by rememberSaveable { mutableStateOf(center.phone) }
    var email by rememberSaveable { mutableStateOf(center.email) }
    var website by rememberSaveable { mutableStateOf(center.website) }
    var signatory by rememberSaveable { mutableStateOf(center.signatory) }
    // Not saveable: a logo can be up to 100 KB, too big for saved state.
    var logo by remember { mutableStateOf(center.logo) }
    var currencyCode by rememberSaveable { mutableStateOf(center.currencyCode) }
    var currencySymbol by rememberSaveable { mutableStateOf(center.currencySymbol) }
    var grouping by rememberSaveable { mutableStateOf(center.grouping) }
    var studentPrefix by rememberSaveable { mutableStateOf(center.studentPrefix) }
    var receiptPrefix by rememberSaveable { mutableStateOf(center.receiptPrefix) }
    var certificatePrefix by rememberSaveable { mutableStateOf(center.certificatePrefix) }
    var logoError by remember { mutableStateOf<String?>(null) }
    val logoBitmap = remember(logo) { if (logo.isBlank()) null else Images.decode(logo)?.asImageBitmap() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { Images.logo(context, uri) }
                .onSuccess { logo = it; logoError = null }
                .onFailure { logoError = it.message }
        }
    }

    Screen(title = "Center settings", onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logoBitmap != null) Image(logoBitmap, contentDescription = "Logo", modifier = Modifier.size(64.dp))
                else Avatar(name.ifBlank { "?" }, size = 64.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    TextButton(onClick = {
                        app.appLock.skipNextLock()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (logo.isBlank()) "Add logo" else "Change logo") }
                    if (logo.isNotBlank()) TextButton(onClick = { logo = "" }) { Text("Remove logo", color = MaterialTheme.colorScheme.error) }
                    logoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text("Shown on receipts, certificates and ID cards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NameInput(name, { name = it }, "Center name *", tag = "center_name_edit")
            TextInput(tagline, { tagline = it }, "Tagline")
            TextInput(address, { address = it }, "Address", singleLine = false, minLines = 2)
            FieldPair(
                { m -> PhoneInput(phone, { phone = it }, "Phone", m) },
                { m -> TextInput(email, { email = it.trim() }, "Email", m, keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None) },
            )
            TextInput(website, { website = it.trim() }, "Website or Facebook page", capitalization = KeyboardCapitalization.None)
            TextInput(signatory, { signatory = it }, "Signature title on certificates", placeholder = "Director")

            FieldLabel("Money")
            FieldPair(
                { m -> TextInput(currencyCode, { currencyCode = it.uppercase().take(3) }, "Currency code", m, maxLength = 3) },
                { m -> TextInput(currencySymbol, { currencySymbol = it }, "Symbol", m, maxLength = 4) },
            )
            ChoiceChips(Grouping.entries, grouping, { grouping = it }, { Money.format(123456700, currencySymbol, it) })

            FieldLabel("Number prefixes")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(studentPrefix, { studentPrefix = it.uppercase() }, "Student", Modifier.weight(1f), maxLength = 6)
                TextInput(receiptPrefix, { receiptPrefix = it.uppercase() }, "Receipt", Modifier.weight(1f), maxLength = 6)
                TextInput(certificatePrefix, { certificatePrefix = it.uppercase() }, "Certificate", Modifier.weight(1f), maxLength = 6)
            }
            Text(
                "Numbers look like $studentPrefix-2026-0001. Changing a prefix only affects new numbers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = name.isNotBlank() && data.role.manages,
                onClick = {
                    app.store.saveCenter(
                        center.copy(
                            name = name.trim(), tagline = tagline.trim(), address = address.trim(), phone = phone.trim(),
                            email = email.trim(), website = website.trim(), signatory = signatory.trim(), logo = logo,
                            currencyCode = currencyCode.ifBlank { "BDT" }, currencySymbol = currencySymbol.ifBlank { "৳" },
                            grouping = grouping, studentPrefix = studentPrefix.ifBlank { "ST" }, receiptPrefix = receiptPrefix.ifBlank { "MR" },
                            certificatePrefix = certificatePrefix.ifBlank { "CERT" },
                        ),
                    )
                    notify("Center settings saved")
                    nav.back()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_center"),
            ) { Text("Save") }
        }
    }
}
