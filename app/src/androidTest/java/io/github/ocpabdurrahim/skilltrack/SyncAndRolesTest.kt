package io.github.ocpabdurrahim.skilltrack

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ocpabdurrahim.skilltrack.data.Access
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.Member
import io.github.ocpabdurrahim.skilltrack.data.Payment
import io.github.ocpabdurrahim.skilltrack.data.Role
import io.github.ocpabdurrahim.skilltrack.data.Session
import io.github.ocpabdurrahim.skilltrack.data.Student
import io.github.ocpabdurrahim.skilltrack.ui.auth.emulatorSignIn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone against the local Firebase emulators. "Another device" is played
 * by the emulator's REST API, so its changes reach the app the way the web
 * app's or another staff member's would.
 */
@RunWith(AndroidJUnit4::class)
class SyncAndRolesTest {
    @get:Rule(order = 0)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val capture = FailureCapture(compose)

    @Before
    fun setUp() {
        assumeTrue("Needs a build pointed at the Firebase emulators", emulatorHost.isNotEmpty())
        resetApp()
    }

    @After
    fun tearDown() {
        if (emulatorHost.isNotEmpty()) resetApp()
    }

    @Test
    fun changesFlowBothWays() = runBlocking {
        signInFresh("owner")
        val c = createCenter("Sync Center")
        val rest = EmulatorRest()

        // The starter expense heads were written for the new center.
        eventually("starter expense heads are in the cloud") { rest.get("centers/$c/expenseHeads/head_rent")?.get("name") == "Rent" }

        // Made on another device -> on this phone.
        rest.put("centers/$c/courses/webCourse1", mapOf("name" to "Web Design", "code" to "WD", "fee" to 1_500_000L, "duration" to "4 months", "description" to "", "archived" to false, "deleted" to false))
        eventually("the web's course is on the phone") { data.courseById["webCourse1"]?.fee == 1_500_000L }

        // Deleted on another device -> gone here.
        rest.put("centers/$c/courses/webCourse1", mapOf("deleted" to true), merge = true)
        eventually("the web's delete reached the phone") { data.courseById["webCourse1"] == null }

        // Made on this phone -> in the cloud, with a server timestamp.
        val course = Course(name = "Office Application", code = "MSO", fee = 600_000)
        container.store.put(Cols.courses, course)
        eventually("the phone's course is in the cloud") { rest.get("centers/$c/courses/${course.id}")?.get("fee") == 600_000L }
        assertTrue(rest.get("centers/$c/courses/${course.id}")!!["updatedAt"] is String)

        // Deleted here -> a tombstone in the cloud.
        container.store.remove(Cols.courses, course.id)
        eventually("the phone's delete is in the cloud") { rest.get("centers/$c/courses/${course.id}")?.get("deleted") == true }

        // The center's settings reach the phone too.
        rest.put("centers/$c", mapOf("currencySymbol" to "$", "grouping" to "INTERNATIONAL"), merge = true)
        eventually("the currency change is on the phone") { data.center?.currencySymbol == "$" }
    }

    @Test
    fun aTeacherSeesNoMoneyAndCannotWriteIt() = runBlocking {
        val owner = signInFresh("owner")
        val c = createCenter("Roles Center")
        val rest = EmulatorRest()
        val teacher = "teacher-${System.nanoTime()}@skilltrack.test"
        container.store.saveMember(Member(id = teacher, name = "Tania Teacher", role = Role.TEACHER, addedBy = owner, createdAt = System.currentTimeMillis()))
        val student = Student(name = "Rafi Ahmed", regNo = "ST-2026-0001")
        val enrollment = Enrollment(studentId = student.id, batchId = "b1", courseId = "c1", date = "2026-10-01", fee = 500_000)
        val payment = Payment(enrollmentId = enrollment.id, studentId = student.id, amount = 100_000, date = "2026-10-01", receiptNo = "MR-2026-0001")
        container.store.putMany(listOf(Cols.students to student, Cols.enrollments to enrollment, Cols.payments to payment))
        eventually("the owner's writes are in the cloud") {
            rest.get("centers/$c/members/$teacher")?.get("role") == "TEACHER" && rest.get("centers/$c/payments/${payment.id}") != null
        }

        // The teacher signs in and finds the center.
        container.session.signOut()
        emulatorSignIn(teacher)
        eventually("the teacher's center list is ready") {
            (container.session.state.value as? Session.Choosing)?.centers?.any { it.id == c } == true
        }
        container.session.enter(c)
        eventually("the center is open for the teacher") { data.ready && data.me?.role == Role.TEACHER && data.synced }
        assertEquals("Rafi Ahmed", data.studentById[student.id]?.name)
        assertTrue("A teacher must not receive payments", data.payments.isEmpty())

        // Writing money is refused by the server, and nothing lands.
        val sneaky = Payment(enrollmentId = enrollment.id, studentId = student.id, amount = 999_00, date = "2026-10-02", receiptNo = "MR-2026-0999")
        container.store.put(Cols.payments, sneaky)
        eventually("the server turned the payment down") { data.error?.contains("permission", ignoreCase = true) == true }
        assertNull(rest.get("centers/$c/payments/${sneaky.id}"))
        // Attendance is theirs to write.
        val day = io.github.ocpabdurrahim.skilltrack.data.Attendance(
            id = "b1_20261001", batchId = "b1", date = "2026-10-01",
            marks = mapOf(enrollment.id to io.github.ocpabdurrahim.skilltrack.data.Mark.PRESENT), takenBy = "Tania",
        )
        container.store.put(Cols.attendance, day)
        eventually("the teacher's attendance is in the cloud") { rest.get("centers/$c/attendance/b1_20261001") != null }
    }

    @Test
    fun removedStaffLoseAccess() = runBlocking {
        val owner = signInFresh("owner")
        val c = createCenter("Access Center")
        val rest = EmulatorRest()
        val office = "office-${System.nanoTime()}@skilltrack.test"
        container.store.saveMember(Member(id = office, name = "Omar Office", role = Role.OFFICE, addedBy = owner))
        eventually("the office member is in the cloud") { rest.get("centers/$c/members/$office") != null }

        container.session.signOut()
        emulatorSignIn(office)
        eventually("the office user's center list is ready") {
            (container.session.state.value as? Session.Choosing)?.centers?.any { it.id == c } == true
        }
        container.session.enter(c)
        eventually("the center is open for the office user") { data.ready && data.access == Access.OK && data.me?.role == Role.OFFICE }

        // The owner removes them on another device.
        rest.put("centers/$c/members/$office", mapOf("deleted" to true), merge = true)
        eventually("access is gone") { data.access == Access.NONE }
        compose.waitForTag("choose_center")
    }
}
