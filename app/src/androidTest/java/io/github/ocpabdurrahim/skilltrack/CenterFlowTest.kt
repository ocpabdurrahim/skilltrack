package io.github.ocpabdurrahim.skilltrack

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A new owner sets up a center and runs a first day, all through the screens. */
@RunWith(AndroidJUnit4::class)
class CenterFlowTest {
    @get:Rule(order = 0)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val capture = FailureCapture(compose)

    @Before
    fun setUp() {
        assumeTrue("Needs a build pointed at the Firebase emulators", emulatorHost.isNotEmpty())
        resetApp()
    }

    private fun tap(tag: String) {
        compose.waitForTag(tag)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun tapInList(tag: String) {
        compose.waitForTag(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun type(tag: String, text: String) {
        compose.waitForTag(tag)
        compose.onNodeWithTag(tag).performTextInput(text)
    }

    private fun back() {
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
    }

    @Test
    fun ownerSetsUpACenterAndAdmitsAStudent() {
        runBlocking { signInFresh("flow") }

        // Create the center.
        tapInList("create_center")
        type("center_name", "Bright Future IT Institute")
        tap("create_center_save")
        compose.waitForTag("home_hero", 30_000)

        // A course.
        tapInList("nav_more")
        tapInList("more_courses")
        tapInList("fab_course")
        type("course_name", "Graphic Design")
        type("course_code", "gd")
        type("course_fee", "12000")
        tapInList("dialog_save")
        compose.waitForTag("course_row")
        assertEquals(1_200_000L, data.courses.single().fee)
        back()

        // A batch on Saturdays and Mondays, starting today.
        tapInList("nav_batches")
        tapInList("fab_batch")
        type("batch_name", "gd-26-b01")
        tap("day_SAT")
        tap("day_MON")
        tap("save_batch")
        compose.waitForTag("batch_tab_0")
        assertEquals("GD-26-B01", data.batches.single().name)
        assertEquals(listOf("SAT", "MON"), data.batches.single().days)
        back()

        // An admission with a 3-month plan and the first installment paid on the spot.
        tapInList("nav_students")
        tapInList("fab_admission")
        type("admission_name", "Rahim Uddin")
        type("admission_phone", "01711000000")
        compose.waitUntil(10_000) { data.batches.isNotEmpty() }
        tap("plan_3 months")
        compose.waitForIdle()
        tap("save_admission")
        compose.waitForTextOf("receipt_amount", "৳4,000", 20_000)
        val e = data.enrollments.single()
        assertEquals(EnrollmentStatus.ACTIVE, e.status)
        assertEquals(3, e.installments.size)
        assertEquals("ST-${java.time.LocalDate.now().year}-0001", data.students.single().regNo)
        assertEquals("MR-${java.time.LocalDate.now().year}-0001", data.payments.single().receiptNo)
        assertEquals(800_000L, data.fee(e).due)
        back()

        // The rest shows up among the dues.
        tapInList("nav_fees")
        tapInList("fees_tab_dues")
        compose.onNodeWithText("All dues").performClick()
        compose.waitForText("৳8,000")

        // Attendance for today's class.
        tapInList("nav_batches")
        tapInList("batch_row")
        tapInList("batch_attendance")
        compose.waitForTag("mark_row")
        compose.waitForIdle()
        compose.onNodeWithTag("save_attendance").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("batch_tab_1")
        compose.onNodeWithTag("batch_tab_1").performClick()
        compose.waitForText("Present 1 of 1")
        runBlocking { eventually("the attendance was saved") { data.attendance.size == 1 } }
    }
}
