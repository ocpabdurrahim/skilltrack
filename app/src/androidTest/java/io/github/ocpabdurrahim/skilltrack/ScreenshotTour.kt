package io.github.ocpabdurrahim.skilltrack

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ocpabdurrahim.skilltrack.data.ThemeMode
import io.github.ocpabdurrahim.skilltrack.domain.Pin
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the main screens of a demo center and saves a screenshot of each. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule(order = 0)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val capture = FailureCapture(compose)

    @Before
    fun setUp() {
        assumeTrue("Needs a build pointed at the Firebase emulators", emulatorHost.isNotEmpty())
        resetApp()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Screenshots.take(name)
    }

    private fun tab(name: String) {
        compose.onNodeWithTag("nav_$name").performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
    }

    @Test
    fun tour() {
        compose.waitForTag("google_sign_in")
        shot("00_sign_in")

        runBlocking {
            signInFresh("demo")
            createCenter("Bright Future IT Institute")
            DemoData.load()
            eventually("the demo records are loaded") { data.students.size >= 24 && data.payments.isNotEmpty() }
        }
        compose.waitForTag("home_hero", 30_000)
        runCatching { compose.waitForTag("today_class", 5_000) }
        shot("01_home")

        tab("students")
        compose.waitForTag("student_row")
        shot("02_students")
        compose.onAllNodesWithTag("student_row").onFirst().performClick()
        compose.waitForTag("admission_row")
        shot("03_student")
        back()

        tab("batches")
        compose.waitForTag("batch_row")
        shot("04_batches")
        compose.onAllNodesWithTag("batch_row").onFirst().performClick()
        compose.waitForTag("batch_tab_0")
        shot("05_batch")
        compose.onNodeWithTag("batch_tab_1").performClick()
        shot("06_batch_attendance")
        compose.onNodeWithTag("batch_attendance").performClick()
        compose.waitForTag("mark_row")
        shot("07_mark_attendance")
        back()
        back()
        compose.onAllNodesWithTag("batch_row").fetchSemanticsNodes().size.let { n ->
            // The completed batch, with its results.
            if (n > 0) {
                compose.onNodeWithText("Completed").performClick()
                compose.waitForTag("batch_row")
                compose.onAllNodesWithTag("batch_row").onFirst().performClick()
                compose.waitForTag("batch_tab_3")
                compose.onNodeWithTag("batch_tab_3").performClick()
                shot("08_results")
                back()
            }
        }

        tab("fees")
        compose.waitForTag("receipt_row")
        shot("09_fees")
        compose.onAllNodesWithTag("receipt_row").onFirst().performClick()
        compose.waitForTag("receipt_card")
        shot("10_receipt")
        back()
        compose.onNodeWithTag("fees_tab_dues").performClick()
        compose.waitForTag("due_row")
        shot("11_dues")

        tab("more")
        compose.waitForTag("more_reports")
        shot("12_more")
        compose.onNodeWithTag("more_reports").performClick()
        compose.waitForTag("year_chart")
        shot("13_reports")
        back()

        compose.onNodeWithTag("more_enquiries").performClick()
        compose.waitForTag("enquiry_row")
        shot("14_enquiries")
        back()

        tab("home")
        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK) } }
        compose.waitForTag("home_hero")
        shot("15_home_dark")
        tab("students")
        compose.waitForTag("student_row")
        shot("16_students_dark")

        runBlocking { container.settings.update { it.copy(theme = ThemeMode.LIGHT, pinHash = Pin.hash("2468")) } }
        compose.runOnUiThread { container.appLock.unlocked = false }
        compose.waitForTag("lock_screen")
        shot("17_lock")
    }
}
