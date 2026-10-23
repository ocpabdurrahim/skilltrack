package io.github.ocpabdurrahim.skilltrack.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.ocpabdurrahim.skilltrack.AppContainer
import io.github.ocpabdurrahim.skilltrack.data.Access
import io.github.ocpabdurrahim.skilltrack.data.Role
import io.github.ocpabdurrahim.skilltrack.data.Session
import io.github.ocpabdurrahim.skilltrack.data.ThemeMode
import io.github.ocpabdurrahim.skilltrack.domain.Grouping
import io.github.ocpabdurrahim.skilltrack.ui.auth.CenterPickerScreen
import io.github.ocpabdurrahim.skilltrack.ui.auth.NoAccessScreen
import io.github.ocpabdurrahim.skilltrack.ui.auth.OpeningScreen
import io.github.ocpabdurrahim.skilltrack.ui.auth.SignInScreen
import io.github.ocpabdurrahim.skilltrack.ui.batches.AttendanceScreen
import io.github.ocpabdurrahim.skilltrack.ui.batches.BatchDetailScreen
import io.github.ocpabdurrahim.skilltrack.ui.batches.BatchEditScreen
import io.github.ocpabdurrahim.skilltrack.ui.batches.BatchesScreen
import io.github.ocpabdurrahim.skilltrack.ui.batches.ExamScreen
import io.github.ocpabdurrahim.skilltrack.ui.fees.CollectFeeScreen
import io.github.ocpabdurrahim.skilltrack.ui.fees.FeesScreen
import io.github.ocpabdurrahim.skilltrack.ui.fees.ReceiptScreen
import io.github.ocpabdurrahim.skilltrack.ui.home.HomeScreen
import io.github.ocpabdurrahim.skilltrack.ui.lock.LockScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.CertificatesScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.CoursesScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.EnquiriesScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.EnquiryEditScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.ExpenseHeadsScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.ExpensesScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.InstructorDetailScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.InstructorEditScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.InstructorsScreen
import io.github.ocpabdurrahim.skilltrack.ui.manage.ReportsScreen
import io.github.ocpabdurrahim.skilltrack.ui.more.AppSettingsScreen
import io.github.ocpabdurrahim.skilltrack.ui.more.ApprovalsScreen
import io.github.ocpabdurrahim.skilltrack.ui.more.CenterSettingsScreen
import io.github.ocpabdurrahim.skilltrack.ui.more.MoreScreen
import io.github.ocpabdurrahim.skilltrack.ui.more.StaffScreen
import io.github.ocpabdurrahim.skilltrack.ui.students.AdmissionScreen
import io.github.ocpabdurrahim.skilltrack.ui.students.EnrollmentScreen
import io.github.ocpabdurrahim.skilltrack.ui.students.StudentDetailScreen
import io.github.ocpabdurrahim.skilltrack.ui.students.StudentEditScreen
import io.github.ocpabdurrahim.skilltrack.ui.students.StudentsScreen
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val STUDENTS = "students"
    const val BATCHES = "batches"
    const val FEES = "fees"
    const val MORE = "more"
    const val DUES = "dues"
    const val COURSES = "courses"
    const val INSTRUCTORS = "instructors"
    const val ENQUIRIES = "enquiries"
    const val EXPENSES = "expenses"
    const val HEADS = "expense-heads"
    const val REPORTS = "reports"
    const val CERTIFICATES = "certificates"
    const val STAFF = "staff"
    const val CENTER = "center-settings"
    const val APP_SETTINGS = "app-settings"
    const val APPROVALS = "approvals"

    // Ids are URL-safe (letters, digits, _ and -); dates are YYYY-MM-DD.
    fun student(id: String) = "student/$id"
    fun studentEdit(id: String? = null) = if (id == null) "student-edit" else "student-edit?id=$id"
    fun admission(studentId: String? = null, batchId: String? = null, enquiryId: String? = null) = withQuery(
        "admission", "studentId" to studentId, "batchId" to batchId, "enquiryId" to enquiryId,
    )
    fun enrollment(id: String) = "enrollment/$id"
    fun batch(id: String) = "batch/$id"
    fun batchEdit(id: String? = null) = if (id == null) "batch-edit" else "batch-edit?id=$id"
    fun attendance(batchId: String, date: String? = null) = if (date == null) "attendance/$batchId" else "attendance/$batchId?date=$date"
    fun exam(batchId: String, id: String? = null) = if (id == null) "exam/$batchId" else "exam/$batchId?id=$id"
    fun collect(enrollmentId: String? = null, studentId: String? = null) =
        withQuery("collect", "enrollmentId" to enrollmentId, "studentId" to studentId)
    fun receipt(id: String) = "receipt/$id"
    fun instructor(id: String) = "instructor/$id"
    fun instructorEdit(id: String? = null) = if (id == null) "instructor-edit" else "instructor-edit?id=$id"
    fun enquiryEdit(id: String? = null) = if (id == null) "enquiry-edit" else "enquiry-edit?id=$id"

    private fun withQuery(path: String, vararg params: Pair<String, String?>): String {
        val query = params.filter { it.second != null }.joinToString("&") { "${it.first}=${it.second}" }
        return if (query.isEmpty()) path else "$path?$query"
    }
}

/** Moving between screens, available to every screen. */
class Navigator(private val nav: NavHostController) {
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }
    fun back() {
        nav.navigateUp()
    }

    /** Replaces the current screen, e.g. a finished form with what it created. */
    fun replace(route: String) {
        nav.navigateUp()
        nav.navigate(route) { launchSingleTop = true }
    }

    /** Bottom-tab navigation: one copy of each tab, and each tab keeps its scroll state. */
    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("No navigator") }

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private fun tabsFor(role: Role): List<Tab> = listOfNotNull(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.STUDENTS, "Students", Icons.Outlined.People, Icons.Filled.People),
    Tab(Routes.BATCHES, "Batches", Icons.Outlined.Class, Icons.Filled.Class),
    if (role.seesMoney) Tab(Routes.FEES, "Fees", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong) else null,
    Tab(Routes.MORE, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

@Composable
fun SkillRoot(container: AppContainer) {
    val settings by container.settingsState.collectAsStateWithLifecycle()
    // Settings load in a few milliseconds; until then the window background shows.
    val s = settings ?: return
    val dark = when (s.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        // Status-bar icons must follow the app's theme, not the system's.
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
        )
    }
    val session by container.session.state.collectAsStateWithLifecycle()
    val data by container.store.data.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = remember {
        { message ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }
    val money = MoneyFormat(data.center?.currencySymbol ?: "৳", data.center?.grouping ?: Grouping.SOUTH_ASIAN)

    SkillTheme(dark = dark, dynamicColor = s.dynamicColor) {
        CompositionLocalProvider(LocalApp provides container, LocalMoney provides money, LocalNotify provides notify, LocalSnackbar provides snackbar) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    // Exposes test tags as view ids, so the CI smoke test can find buttons.
                    .semantics { testTagsAsResourceId = true },
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize()) {
                    when (val st = session) {
                        Session.Starting -> OpeningScreen(null)
                        Session.SignedOut -> SignInScreen()
                        is Session.Choosing -> CenterPickerScreen(st)
                        is Session.InCenter -> when {
                            data.centerId != st.centerId -> OpeningScreen(null)
                            data.access == Access.NONE -> NoAccessScreen(st.email)
                            !data.ready || data.center == null || data.me == null -> OpeningScreen("Opening your center…")
                            // A fresh NavHost per center and role, so nothing from another center lingers.
                            else -> key(st.centerId, data.role) { MainNav(data.role) }
                        }
                    }
                    // Drawn over the app rather than instead of it, so the screen you
                    // were on is still there after unlocking.
                    val pinHash = s.pinHash
                    if (pinHash != null && !container.appLock.unlocked) {
                        LockScreen(pinHash = pinHash, biometric = s.biometric, onUnlock = { container.appLock.unlocked = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNav(role: Role) {
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = tabsFor(role)

    CompositionLocalProvider(LocalNav provides navigator) {
        Scaffold(
            // Each screen handles its own insets; the outer scaffold only adds the bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (tabs.any { it.route == route }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigator.tab(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}"),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.HOME) { HomeScreen() }
                composable(Routes.STUDENTS) { StudentsScreen() }
                composable(Routes.BATCHES) { BatchesScreen() }
                composable(Routes.FEES) { FeesScreen(showDues = false, standalone = false) }
                composable(Routes.DUES) { FeesScreen(showDues = true, standalone = true) }
                composable(Routes.MORE) { MoreScreen() }
                composable(Routes.COURSES) { CoursesScreen() }
                composable(Routes.INSTRUCTORS) { InstructorsScreen() }
                composable(Routes.ENQUIRIES) { EnquiriesScreen() }
                composable(Routes.EXPENSES) { ExpensesScreen() }
                composable(Routes.HEADS) { ExpenseHeadsScreen() }
                composable(Routes.REPORTS) { ReportsScreen() }
                composable(Routes.CERTIFICATES) { CertificatesScreen() }
                composable(Routes.STAFF) { StaffScreen() }
                composable(Routes.CENTER) { CenterSettingsScreen() }
                composable(Routes.APP_SETTINGS) { AppSettingsScreen() }
                composable(Routes.APPROVALS) { ApprovalsScreen(onBack = { navigator.back() }) }

                withId("student/{id}") { StudentDetailScreen(it.arg("id")!!) }
                withOptional("student-edit?id={id}", "id") { StudentEditScreen(it.arg("id")) }
                withOptional("admission?studentId={studentId}&batchId={batchId}&enquiryId={enquiryId}", "studentId", "batchId", "enquiryId") {
                    AdmissionScreen(it.arg("studentId"), it.arg("batchId"), it.arg("enquiryId"))
                }
                withId("enrollment/{id}") { EnrollmentScreen(it.arg("id")!!) }
                withId("batch/{id}") { BatchDetailScreen(it.arg("id")!!) }
                withOptional("batch-edit?id={id}", "id") { BatchEditScreen(it.arg("id")) }
                withOptional("attendance/{batchId}?date={date}", "date") { AttendanceScreen(it.arg("batchId")!!, it.arg("date")) }
                withOptional("exam/{batchId}?id={id}", "id") { ExamScreen(it.arg("batchId")!!, it.arg("id")) }
                withOptional("collect?enrollmentId={enrollmentId}&studentId={studentId}", "enrollmentId", "studentId") {
                    CollectFeeScreen(it.arg("enrollmentId"), it.arg("studentId"))
                }
                withId("receipt/{id}") { ReceiptScreen(it.arg("id")!!) }
                withId("instructor/{id}") { InstructorDetailScreen(it.arg("id")!!) }
                withOptional("instructor-edit?id={id}", "id") { InstructorEditScreen(it.arg("id")) }
                withOptional("enquiry-edit?id={id}", "id") { EnquiryEditScreen(it.arg("id")) }
            }
        }
    }
}

private fun NavBackStackEntry.arg(name: String): String? = arguments?.getString(name)?.takeIf { it.isNotEmpty() }

/** A route whose path has required string arguments. */
private fun NavGraphBuilder.withId(route: String, content: @Composable (NavBackStackEntry) -> Unit) =
    composable(route) { content(it) }

/** A route with optional query arguments ([optional]) that default to null. */
private fun NavGraphBuilder.withOptional(route: String, vararg optional: String, content: @Composable (NavBackStackEntry) -> Unit) =
    composable(
        route,
        arguments = optional.map { name ->
            navArgument(name) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        },
    ) { content(it) }
