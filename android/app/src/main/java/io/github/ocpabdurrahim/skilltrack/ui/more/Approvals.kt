package io.github.ocpabdurrahim.skilltrack.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import io.github.ocpabdurrahim.skilltrack.data.CenterRequest
import io.github.ocpabdurrahim.skilltrack.data.Creator
import io.github.ocpabdurrahim.skilltrack.data.RequestStatus
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.system.Share
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.FormDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatusChip
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import kotlinx.coroutines.launch

/** A small count of waiting center requests on top of [content]; only the app owner can see them. */
@Composable
fun PendingBadge(content: @Composable () -> Unit) {
    val app = LocalApp.current
    val requests by remember { app.approvals.requests() }.collectAsState(initial = emptyList())
    val waiting = requests.count { it.status == RequestStatus.PENDING }
    BadgedBox(badge = { if (waiting > 0) Badge { Text(waiting.toString()) } }) { content() }
}

private enum class ApprovalTab(val label: String) { REQUESTS("Requests"), APPROVED("Approved") }

/**
 * For the SkillTrack owner only: who asked to create a center, and who may.
 * The server refuses this data to everyone else.
 */
@Composable
fun ApprovalsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SkillTheme.colors
    val me = Firebase.auth.currentUser?.email?.lowercase().orEmpty()
    val requests by remember { app.approvals.requests() }.collectAsState(initial = null)
    val creators by remember { app.approvals.creators() }.collectAsState(initial = null)
    var tab by rememberSaveable { mutableStateOf(ApprovalTab.REQUESTS) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Creator?>(null) }

    fun act(done: String, action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }
                .onSuccess { notify(done) }
                .onFailure { notify("Didn't work: ${it.message}") }
        }
    }

    val waiting = requests.orEmpty().filter { it.status == RequestStatus.PENDING }
    val answered = requests.orEmpty().filter { it.status != RequestStatus.PENDING }

    ListScreen(
        title = "Center requests",
        subtitle = "Who may create a center on SkillTrack",
        onBack = onBack,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Approve an email") },
            )
        },
    ) {
        item {
            ChoiceChips(
                ApprovalTab.entries, tab, { tab = it },
                { if (it == ApprovalTab.REQUESTS) "${it.label} (${waiting.size})" else "${it.label} (${creators.orEmpty().size})" },
            )
        }
        if (tab == ApprovalTab.REQUESTS) {
            if (requests == null) item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else if (waiting.isEmpty()) item { EmptyState("📭", "No waiting requests", "When someone asks to create a center, it shows up here.") }
            items(waiting, key = { "w" + it.email }) { r ->
                RequestCard(
                    r,
                    onCall = { Share.call(context, r.phone) },
                    onWhatsApp = { Share.whatsApp(context, r.phone, "Assalamu alaikum ${r.name}, about your SkillTrack center \"${r.centerName}\": ") },
                    onApprove = { act("${r.email} can now create a center") { app.approvals.approve(r.email, r.name, me, hasRequest = true) } },
                    onDecline = { act("Declined ${r.email}") { app.approvals.decline(r.email, me) } },
                )
            }
            if (answered.isNotEmpty()) {
                item { Text("Answered", style = MaterialTheme.typography.titleMedium) }
                items(answered, key = { "a" + it.email }) { r ->
                    SkillCard {
                        ListRow(
                            title = "${r.name} · ${r.centerName}",
                            subtitle = "${r.email} · asked ${Dates.moment(r.createdAt)}",
                            trailing = { StatusChip(r.status.label, if (r.status == RequestStatus.APPROVED) colors.success else colors.danger) },
                        )
                        if (r.status == RequestStatus.DECLINED) {
                            TextButton(onClick = { act("${r.email} can now create a center") { app.approvals.approve(r.email, r.name, me, hasRequest = true) } }) {
                                Text("Approve after all")
                            }
                        }
                    }
                }
            }
        } else {
            if (creators == null) item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else if (creators.orEmpty().isEmpty()) item { EmptyState("🔐", "Nobody approved yet", "Only you can create centers. Approve a request, or an email directly with the button below.") }
            items(creators.orEmpty(), key = { "c" + it.email }) { c ->
                SkillCard(modifier = Modifier.testTag("creator_row")) {
                    ListRow(
                        title = c.name.ifBlank { c.email },
                        subtitle = listOfNotNull(c.email.takeIf { c.name.isNotBlank() }, "approved ${Dates.moment(c.approvedAt)}").joinToString(" · "),
                        trailing = { TextButton(onClick = { removing = c }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
                    )
                }
            }
        }
    }

    if (adding) {
        var email by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        val clean = email.trim().lowercase()
        FormDialog(
            title = "Approve an email",
            onDismiss = { adding = false },
            saveLabel = "Approve",
            saveEnabled = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(clean),
            onSave = {
                adding = false
                val hasRequest = requests.orEmpty().any { it.email == clean }
                act("$clean can now create a center") { app.approvals.approve(clean, name, me, hasRequest) }
            },
        ) {
            Text("They can create centers after signing in with this Google account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextInput(email, { email = it }, "Google email *", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None, tag = "approve_email")
            NameInput(name, { name = it }, "Name (for your list)")
        }
    }
    removing?.let { c ->
        ConfirmDialog(
            title = "Remove ${c.email}?",
            text = "They won't be able to create more centers. Centers they already made keep working.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                act("${c.email} removed") { app.approvals.remove(c.email) }
            },
        )
    }
}

@Composable
private fun RequestCard(
    r: CenterRequest,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    SkillCard(modifier = Modifier.testTag("request_row")) {
        Text(r.centerName, style = MaterialTheme.typography.titleMedium)
        KeyValue("Name", r.name)
        KeyValue("Google email", r.email)
        KeyValue("Phone", r.phone)
        KeyValue("Asked", Dates.moment(r.createdAt))
        if (r.message.isNotBlank()) KeyValue("Message", r.message)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (r.phone.isNotBlank()) {
                IconButton(onClick = onCall) { Icon(Icons.Filled.Call, contentDescription = "Call") }
                IconButton(onClick = onWhatsApp) { Icon(Icons.Filled.Chat, contentDescription = "WhatsApp") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove, modifier = Modifier.testTag("approve_request")) { Text("Approve") }
            OutlinedButton(onClick = onDecline, modifier = Modifier.testTag("decline_request")) { Text("Decline") }
        }
    }
}
