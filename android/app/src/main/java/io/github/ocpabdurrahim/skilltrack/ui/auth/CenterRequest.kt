package io.github.ocpabdurrahim.skilltrack.ui.auth

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.CenterRequest
import io.github.ocpabdurrahim.skilltrack.data.CreateAccess
import io.github.ocpabdurrahim.skilltrack.data.RequestStatus
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.Loading
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import kotlinx.coroutines.launch

/**
 * "Create a new center": the form for the app owner and approved people;
 * everyone else asks for permission here and waits for the answer, which
 * arrives by itself (docs/data-model.md, "Who may create a center").
 */
@Composable
fun CreateCenterScreen(email: String, name: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val access by remember(email) { app.approvals.access(email) }.collectAsState(initial = CreateAccess.Loading)
    when (val a = access) {
        CreateAccess.Loading -> Screen("New center", onBack = onBack) { padding ->
            Loading(Modifier.padding(padding), "Checking whether you can create a center…")
        }
        CreateAccess.Allowed -> CreateCenterForm(email, onBack)
        is CreateAccess.NotAllowed -> RequestScreen(email, name, a.request, onBack)
    }
}

@Composable
private fun RequestScreen(email: String, googleName: String, request: CenterRequest?, onBack: () -> Unit) {
    // Fill the form in again only when asked to (first time, or after a decline).
    var editing by rememberSaveable { mutableStateOf(false) }
    Screen(title = "New center", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                request == null || editing -> RequestForm(email, googleName, request) { editing = false }
                request.status == RequestStatus.PENDING -> {
                    SkillCard(modifier = Modifier.testTag("request_pending")) {
                        Text("Request sent", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "You asked on ${Dates.moment(request.createdAt)} to create \"${request.centerName}\". " +
                                "As soon as the SkillTrack owner approves it, this screen turns into the form to create your center. You can leave and come back.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Change my request") }
                }
                request.status == RequestStatus.DECLINED -> {
                    EmptyState("🙏", "Not approved", "Your request to create \"${request.centerName}\" was not approved. You can ask again with more details.")
                    Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().testTag("ask_again")) { Text("Ask again") }
                }
                // Approved, but the permission itself is not here yet (or was taken back).
                else -> EmptyState("⏳", "Almost there", "Your request was answered; if this doesn't change in a moment, ask the SkillTrack owner to check your permission.")
            }
        }
    }
}

@Composable
private fun RequestForm(email: String, googleName: String, previous: CenterRequest?, onSent: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(previous?.name ?: googleName) }
    var centerName by rememberSaveable { mutableStateOf(previous?.centerName.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(previous?.phone.orEmpty()) }
    var message by rememberSaveable { mutableStateOf(previous?.message.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Ask to create a center", style = MaterialTheme.typography.titleLarge)
    Text(
        "New centers on SkillTrack are approved by its owner. Tell them who you are; you'll be able to create your center here as soon as they approve.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NameInput(name, { name = it }, "Your name *", tag = "request_name")
    NameInput(centerName, { centerName = it }, "Center name *", tag = "request_center_name")
    PhoneInput(phone, { phone = it }, "Phone *", tag = "request_phone")
    TextInput(message, { message = it }, "Message (optional)", singleLine = false, minLines = 3, maxLength = 1000, placeholder = "Where is your center, how many students, how you heard of SkillTrack…")
    Text("Signed in as $email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    error?.let { Text(it, color = SkillTheme.colors.danger) }
    Button(
        enabled = !busy && name.isNotBlank() && centerName.isNotBlank() && phone.isNotBlank(),
        onClick = {
            busy = true
            error = null
            scope.launch {
                try {
                    app.approvals.ask(
                        CenterRequest(
                            email = email, name = name.trim(), centerName = centerName.trim(), phone = phone.trim(), message = message.trim(),
                        ),
                    )
                    onSent()
                } catch (e: Exception) {
                    error = e.message ?: "Could not send the request"
                }
                busy = false
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("send_request"),
    ) { Text(if (busy) "Sending…" else "Send request") }
}
