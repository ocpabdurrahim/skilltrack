package io.github.ocpabdurrahim.skilltrack.ui.more

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Firebase
import com.google.firebase.app
import io.github.ocpabdurrahim.skilltrack.BuildConfig
import io.github.ocpabdurrahim.skilltrack.data.ThemeMode
import io.github.ocpabdurrahim.skilltrack.domain.Pin
import io.github.ocpabdurrahim.skilltrack.system.Biometrics
import io.github.ocpabdurrahim.skilltrack.ui.LocalApp
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.LocalNotify
import io.github.ocpabdurrahim.skilltrack.ui.Routes
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.ChoiceChips
import io.github.ocpabdurrahim.skilltrack.ui.components.ConfirmDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.PinSetupDialog
import io.github.ocpabdurrahim.skilltrack.ui.components.Screen
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.update.UpdateState
import io.github.ocpabdurrahim.skilltrack.update.UpdateWorker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    tag: String? = null,
    tint: Color = Color.Unspecified,
) {
    var modifier: Modifier = Modifier
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    if (tag != null) modifier = modifier.testTag(tag)
    ListItem(
        headlineContent = { Text(title, color = tint) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier,
    )
}

/** The web app's address: Firebase Hosting's default for this project. */
fun webAppUrl(): String = "https://${Firebase.app.options.projectId}.web.app"

@Composable
fun MoreScreen() {
    val data = centerData()
    val app = LocalApp.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val role = data.role
    var confirmSignOut by remember { mutableStateOf(false) }

    Screen(title = "More", subtitle = "${data.center?.name.orEmpty()} · ${data.me?.email.orEmpty()} (${role.label})") { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionHeader("Center", Modifier.padding(horizontal = 16.dp)) }
            item { SettingRow(Icons.Filled.MenuBook, "Courses", "What you teach and the fees", onClick = { nav.go(Routes.COURSES) }, tag = "more_courses") }
            item { SettingRow(Icons.Filled.Person, "Instructors", if (role.seesMoney) "Who teaches, and their pay" else "Who teaches", onClick = { nav.go(Routes.INSTRUCTORS) }, tag = "more_instructors") }
            if (role.office) item { SettingRow(Icons.Filled.SupportAgent, "Enquiries", "Walk-ins and calls to follow up", onClick = { nav.go(Routes.ENQUIRIES) }, tag = "more_enquiries") }
            item { SettingRow(Icons.Filled.WorkspacePremium, "Certificates", "Issued certificates and who is ready", onClick = { nav.go(Routes.CERTIFICATES) }, tag = "more_certificates") }
            if (role.seesMoney) {
                item { SettingRow(Icons.Filled.AccountBalanceWallet, "Dues", "Who still owes fees, with reminders", onClick = { nav.go(Routes.DUES) }, tag = "more_dues") }
                item { SettingRow(Icons.AutoMirrored.Filled.ReceiptLong, "Expenses", "Rent, bills, salaries and other spending", onClick = { nav.go(Routes.EXPENSES) }, tag = "more_expenses") }
                item { SettingRow(Icons.Filled.BarChart, "Reports", "Income, spending and profit by month", onClick = { nav.go(Routes.REPORTS) }, tag = "more_reports") }
            }
            if (role.manages) {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SectionHeader("Administration", Modifier.padding(horizontal = 16.dp)) }
                item { SettingRow(Icons.Filled.Groups, "Staff", "Who can use this center, and what they can do", onClick = { nav.go(Routes.STAFF) }, tag = "more_staff") }
                item { SettingRow(Icons.Filled.Business, "Center settings", "Name, logo, address, currency, numbers", onClick = { nav.go(Routes.CENTER) }, tag = "more_center") }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("You", Modifier.padding(horizontal = 16.dp)) }
            item { SettingRow(Icons.Filled.PhoneAndroid, "This phone", "Theme, app lock and updates", onClick = { nav.go(Routes.APP_SETTINGS) }, tag = "more_app") }
            item {
                SettingRow(Icons.Filled.Language, "Open the web app", webAppUrl().removePrefix("https://"), onClick = {
                    app.appLock.skipNextLock()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webAppUrl()))) }
                })
            }
            item { SettingRow(Icons.Filled.SwapHoriz, "Switch center", "Open another center you belong to, or create one", onClick = { app.session.switchCenter() }, tag = "more_switch") }
            item { SettingRow(Icons.AutoMirrored.Filled.Logout, "Sign out", data.me?.email, onClick = { confirmSignOut = true }, tag = "more_sign_out") }
            item {
                SettingRow(
                    Icons.Filled.Info,
                    "SkillTrack ${BuildConfig.VERSION_NAME}",
                    "Works offline; changes sync with your staff and the web app when online.",
                )
            }
        }
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            text = "The center's records stay safe in the cloud. Changes not yet synced from this phone are sent the next time you sign in here.",
            confirmLabel = "Sign out",
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                app.session.signOut()
            },
        )
    }
}

private fun updateTexts(state: UpdateState, current: String): Pair<String, String> = when (state) {
    UpdateState.Idle -> "Check for updates" to "You have SkillTrack $current"
    UpdateState.Checking -> "Checking for updates…" to "You have SkillTrack $current"
    is UpdateState.UpToDate -> "Check for updates" to "SkillTrack $current is the latest"
    is UpdateState.Downloading -> "Downloading SkillTrack ${state.release.version}…" to "${(state.progress * 100).roundToInt()}%"
    is UpdateState.Ready -> "Install SkillTrack ${state.release.version}" to "Downloaded. It installs by itself when you leave the app, or tap to install now."
    is UpdateState.Installing -> "Installing SkillTrack ${state.release.version}…" to "SkillTrack closes and reopens updated"
    is UpdateState.Failed -> "Check for updates" to "Last attempt failed: ${state.message}"
}

/** Settings that belong to this phone only: theme, app lock and updates. */
@Composable
fun AppSettingsScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val notify = LocalNotify.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settingsState.collectAsStateWithLifecycle()
    val s = settings ?: return
    val updateState by app.updater.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(app.updater.canInstall()) }
    var settingPin by remember { mutableStateOf(false) }
    val biometricAvailable = remember { Biometrics.available(context) }
    // Re-read when coming back from Android's "Install unknown apps" screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = app.updater.canInstall() }

    fun update(transform: (io.github.ocpabdurrahim.skilltrack.data.Settings) -> io.github.ocpabdurrahim.skilltrack.data.Settings) {
        scope.launch { app.settings.update(transform) }
    }

    Screen(title = "This phone", onBack = nav::back) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item { SectionHeader("Look", Modifier.padding(horizontal = 16.dp)) }
            item {
                SettingRow(Icons.Filled.DarkMode, "Theme", s.theme.label)
            }
            item {
                ChoiceChips(ThemeMode.entries, s.theme, { t -> update { it.copy(theme = t) } }, { it.label }, Modifier.padding(horizontal = 16.dp))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingRow(
                        Icons.Filled.Palette, "Wallpaper colours", "Use your wallpaper's colours instead of indigo",
                        onClick = { update { it.copy(dynamicColor = !s.dynamicColor) } },
                        trailing = { Switch(checked = s.dynamicColor, onCheckedChange = { on -> update { it.copy(dynamicColor = on) } }) },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("App lock", Modifier.padding(horizontal = 16.dp)) }
            item {
                SettingRow(
                    Icons.Filled.Lock, "PIN lock", if (s.pinHash == null) "Off" else "On: asks for your PIN when SkillTrack opens",
                    onClick = { if (s.pinHash == null) settingPin = true else update { it.copy(pinHash = null, biometric = false) } },
                    trailing = { Switch(checked = s.pinHash != null, onCheckedChange = { on -> if (on) settingPin = true else update { it.copy(pinHash = null, biometric = false) } }) },
                    tag = "pin_lock",
                )
            }
            if (s.pinHash != null && biometricAvailable) {
                item {
                    SettingRow(
                        Icons.Filled.Fingerprint, "Fingerprint or face", "Unlock without typing the PIN",
                        onClick = { update { it.copy(biometric = !s.biometric) } },
                        trailing = { Switch(checked = s.biometric, onCheckedChange = { on -> update { it.copy(biometric = on) } }) },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("App updates", Modifier.padding(horizontal = 16.dp)) }
            item {
                SettingRow(
                    Icons.Filled.SystemUpdate, "Automatic updates", "Download and install new versions from GitHub by itself",
                    onClick = { update { it.copy(autoUpdate = !s.autoUpdate) } },
                    trailing = {
                        Switch(checked = s.autoUpdate, onCheckedChange = { on ->
                            update { it.copy(autoUpdate = on) }
                            if (on) UpdateWorker.schedule(context) else UpdateWorker.cancel(context)
                        })
                    },
                    tag = "auto_update",
                )
            }
            item {
                val (title, subtitle) = updateTexts(updateState, app.updater.currentVersion)
                SettingRow(
                    Icons.Filled.Refresh, title, subtitle,
                    onClick = {
                        if (updateState is UpdateState.Ready) {
                            app.updater.installNow()
                        } else {
                            scope.launch {
                                app.updater.checkAndDownload(userAsked = true)
                                if (app.updater.state.value is UpdateState.UpToDate) notify("SkillTrack ${app.updater.currentVersion} is the latest version")
                            }
                        }
                    },
                    tag = "check_updates",
                )
            }
            if (!canInstall) {
                item {
                    SettingRow(
                        Icons.Filled.Security, "Allow SkillTrack to install updates", "A one-time Android switch: turn on “Allow from this source”",
                        onClick = {
                            app.appLock.skipNextLock()
                            runCatching {
                                context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                            }
                        },
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingRow(Icons.Filled.Badge, "SkillTrack ${BuildConfig.VERSION_NAME}", "Training center manager · ${webAppUrl().removePrefix("https://")}") }
        }
    }
    if (settingPin) {
        PinSetupDialog(onDismiss = { settingPin = false }) { pin ->
            settingPin = false
            app.appLock.unlocked = true
            update { it.copy(pinHash = Pin.hash(pin)) }
            notify("App lock is on")
        }
    }
}
