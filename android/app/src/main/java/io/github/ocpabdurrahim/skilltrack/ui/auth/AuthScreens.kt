package io.github.ocpabdurrahim.skilltrack.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import io.github.ocpabdurrahim.skilltrack.BuildConfig
import io.github.ocpabdurrahim.skilltrack.data.AppOwner
import io.github.ocpabdurrahim.skilltrack.data.Center
import io.github.ocpabdurrahim.skilltrack.data.CenterRef
import io.github.ocpabdurrahim.skilltrack.data.Session
import io.github.ocpabdurrahim.skilltrack.system.GoogleSignIn
import io.github.ocpabdurrahim.skilltrack.system.findActivity
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.components.Avatar
import io.github.ocpabdurrahim.skilltrack.ui.components.EmptyState
import io.github.ocpabdurrahim.skilltrack.ui.components.ListRow
import io.github.ocpabdurrahim.skilltrack.ui.components.NameInput
import io.github.ocpabdurrahim.skilltrack.ui.components.PhoneInput
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.TextInput
import io.github.ocpabdurrahim.skilltrack.ui.more.ApprovalsScreen
import io.github.ocpabdurrahim.skilltrack.ui.more.PendingBadge
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** The app's mark: a graduation cap on the indigo gradient. */
@Composable
fun AppMark(size: Dp = 72.dp) {
    val colors = SkillTheme.colors
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Shown while signing in or opening a center. */
@Composable
fun OpeningScreen(text: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppMark()
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(Modifier.size(28.dp))
        if (text != null) {
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            // Opening for the first time while offline would wait forever; offer a way out.
            val session = LocalApp.current.session
            TextButton(onClick = { session.switchCenter() }) { Text("Choose another center") }
        }
    }
}

@Composable
fun SignInScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var testEmail by rememberSaveable { mutableStateOf("owner@skilltrack.test") }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        AppMark(88.dp)
        Spacer(Modifier.height(20.dp))
        Text("SkillTrack", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Run your training center from your phone: admissions, fees and receipts, attendance, exams, certificates, enquiries and expenses. Your staff and the web app see the same records.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            enabled = !busy,
            onClick = {
                val activity = context.findActivity() ?: return@Button
                busy = true
                error = null
                scope.launch {
                    try {
                        app.session.signInWithGoogle(activity)
                    } catch (e: GoogleSignIn.Cancelled) {
                        // The person closed the account picker.
                    } catch (e: Exception) {
                        error = e.message ?: "Sign-in failed"
                    }
                    busy = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .height(52.dp)
                .testTag("google_sign_in"),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Continue with Google")
        }
        if (BuildConfig.FIREBASE_EMULATOR_HOST.isNotEmpty()) {
            // Test builds talk to the Firebase emulator, which accepts an unsigned Google token.
            Spacer(Modifier.height(24.dp))
            TextInput(testEmail, { testEmail = it.trim().lowercase() }, "Test account email (emulator)", tag = "test_email")
            OutlinedButton(
                onClick = {
                    scope.launch {
                        error = runCatching { emulatorSignIn(testEmail) }.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_sign_in"),
            ) { Text("Sign in as test user") }
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Owners create their center after signing in. Staff: ask your center's owner to add your Google email under Staff first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

/** Signs in on the Auth emulator with a made-up, verified Google account. */
suspend fun emulatorSignIn(email: String, name: String = email.substringBefore('@').replaceFirstChar { it.uppercase() }) {
    val token = JSONObject()
        .put("sub", "test-" + email.hashCode().toUInt().toString(16))
        .put("email", email)
        .put("email_verified", true)
        .put("name", name)
        .toString()
    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
}

@Composable
fun CenterPickerScreen(state: Session.Choosing) {
    val app = LocalApp.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    val appOwner = AppOwner.isAppOwner(state.email)
    if (creating) {
        CreateCenterScreen(email = state.email, name = state.name, onBack = { creating = false })
        return
    }
    if (reviewing) {
        ApprovalsScreen(onBack = { reviewing = false })
        return
    }
    Screen(
        title = "Your centers",
        subtitle = state.email,
        actions = {
            if (appOwner) {
                IconButton(onClick = { reviewing = true }, modifier = Modifier.testTag("open_approvals")) {
                    PendingBadge { Icon(Icons.Filled.HowToReg, contentDescription = "Center requests") }
                }
            }
            IconButton(onClick = { app.session.discover() }) { Icon(Icons.Filled.Refresh, contentDescription = "Look again") }
            TextButton(onClick = { app.session.signOut() }) { Text("Sign out") }
        },
    ) { padding ->
        val centers = state.centers
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                centers == null -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                centers.isEmpty() -> item {
                    EmptyState(
                        "🏫",
                        "No center yet",
                        state.error?.let { "Couldn't check: $it" }
                            ?: "If your center already uses SkillTrack, ask its owner to add ${state.email} under Staff, then tap ↻. To start your own center, tap the button below.",
                    )
                }
                else -> {
                    item { SectionHeader("Open a center") }
                    items(centers, key = { it.id }) { c -> CenterRow(c) { app.session.enter(c.id) } }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { creating = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_center"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Create a new center")
                }
            }
        }
    }
}

@Composable
private fun CenterRow(c: CenterRef, onClick: () -> Unit) {
    SkillCard(onClick = onClick, modifier = Modifier.testTag("center_row")) {
        ListRow(
            title = c.name,
            subtitle = "You are: ${c.role.label}",
            leading = { Avatar(c.name) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

/** The form itself; [CreateCenterScreen] shows it only to people allowed to create centers. */
@Composable
internal fun CreateCenterForm(email: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var tagline by rememberSaveable { mutableStateOf("Computer Training Center") }
    var address by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var mail by rememberSaveable { mutableStateOf(email) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Screen(title = "New center", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "You will be this center's owner. You can add your staff afterwards, under More → Staff.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NameInput(name, { name = it }, "Center name *", tag = "center_name")
            TextInput(tagline, { tagline = it }, "Tagline")
            TextInput(address, { address = it }, "Address", singleLine = false, minLines = 2)
            PhoneInput(phone, { phone = it }, "Phone")
            TextInput(mail, { mail = it.trim() }, "Email", capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None)
            Text(
                "Money is shown in Taka (৳) with 12,34,567 grouping; you can change that and add a logo later in Center settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            app.session.createCenter(
                                Center(id = "", name = name.trim(), tagline = tagline.trim(), address = address.trim(), phone = phone.trim(), email = mail.trim()),
                            )
                        } catch (e: Exception) {
                            error = e.message ?: "Could not create the center"
                            busy = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_center_save"),
            ) { Text(if (busy) "Creating…" else "Create center") }
        }
    }
}

@Composable
fun NoAccessScreen(email: String) {
    val app = LocalApp.current
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState("🔒", "No access to this center", "$email is not (or no longer) on this center's staff. Ask the owner to add you again, or open another center.")
        Button(onClick = { app.session.switchCenter() }, modifier = Modifier.testTag("choose_center")) { Text("Choose a center") }
        TextButton(onClick = { app.session.signOut() }) { Text("Sign out") }
    }
}
