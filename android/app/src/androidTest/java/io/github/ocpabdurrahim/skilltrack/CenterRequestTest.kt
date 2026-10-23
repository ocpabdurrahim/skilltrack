package io.github.ocpabdurrahim.skilltrack

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ocpabdurrahim.skilltrack.data.AppOwner
import io.github.ocpabdurrahim.skilltrack.data.Center
import io.github.ocpabdurrahim.skilltrack.ui.auth.emulatorSignIn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Only the app owner, and people they approve, may create centers
 * (docs/data-model.md, "Who may create a center"). Everyone else asks first.
 */
@RunWith(AndroidJUnit4::class)
class CenterRequestTest {
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

    @Test
    fun strangersAskAndTheAppOwnerApproves() {
        val rest = EmulatorRest()
        val asker = runBlocking { signInFresh("asker") }

        // Without permission the server refuses a new center.
        val refused = runBlocking { runCatching { container.session.createCenter(Center(id = "", name = "Sneaky Center")) } }
        assertTrue("A center was created without permission", refused.isFailure)

        // "Create a new center" leads to the request form instead.
        tapInList("create_center")
        type("request_center_name", "Karim IT Center")
        type("request_phone", "01711223344")
        tap("send_request")
        compose.waitForTag("request_pending", 20_000)
        runBlocking { eventually("the request is in the cloud") { rest.get("centerRequests/$asker")?.get("status") == "PENDING" } }

        // The app owner signs in, finds the request and approves it.
        container.session.signOut()
        compose.waitForTag("google_sign_in")
        runBlocking { emulatorSignIn(AppOwner.EMAILS.first()) }
        tapInList("open_approvals")
        compose.waitForTag("request_row", 20_000)
        tapInList("approve_request")
        runBlocking {
            eventually("the approval is in the cloud") {
                rest.get("creators/$asker") != null && rest.get("centerRequests/$asker")?.get("status") == "APPROVED"
            }
        }
        assertEquals(AppOwner.EMAILS.first(), rest.get("creators/$asker")?.get("approvedBy"))

        // Back as the person who asked: now the form is there, and the center gets made.
        container.session.signOut()
        compose.waitForTag("google_sign_in")
        runBlocking { emulatorSignIn(asker) }
        tapInList("create_center")
        type("center_name", "Karim IT Center")
        tap("create_center_save")
        compose.waitForTag("home_hero", 30_000)
        assertEquals("Karim IT Center", data.center?.name)
    }
}
