package io.github.ocpabdurrahim.skilltrack

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import io.github.ocpabdurrahim.skilltrack.data.Center
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.data.Settings
import io.github.ocpabdurrahim.skilltrack.ui.auth.emulatorSignIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

val container: AppContainer
    get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SkillApp).container

val emulatorHost: String get() = BuildConfig.FIREBASE_EMULATOR_HOST

val data: CenterData get() = container.store.data.value

/** Default device settings (no PIN) and nobody signed in. */
fun resetApp() {
    runBlocking { container.settings.update { Settings() } }
    container.session.signOut()
    Firebase.auth.signOut()
}

/** Waits up to [timeoutMs] for [check], failing with [what] and the store's state. */
suspend fun eventually(what: String, timeoutMs: Long = 30_000, check: suspend () -> Boolean) {
    try {
        withTimeout(timeoutMs) { while (!check()) delay(200) }
    } catch (e: Exception) {
        val d = data
        throw AssertionError("Timed out waiting until: $what (session=${container.session.state.value}, access=${d.access}, ready=${d.ready}, error=${d.error})", e)
    }
}

/** Signs in on the Auth emulator as a brand-new person and returns their email. */
suspend fun signInFresh(prefix: String): String {
    val email = "$prefix-${System.nanoTime()}@skilltrack.test"
    emulatorSignIn(email)
    return email
}

/**
 * The app owner's permission for whoever is signed in to create centers,
 * written straight into the emulator (as if approved on another device).
 */
fun approveSignedInUser() {
    val email = Firebase.auth.currentUser!!.email!!.lowercase()
    EmulatorRest().put("creators/$email", mapOf("email" to email, "name" to "", "approvedBy" to "test", "approvedAt" to 1L))
}

/** Creates a center owned by whoever is signed in (approving them first), and waits until it is open. */
suspend fun createCenter(name: String = "Test Center"): String {
    approveSignedInUser()
    container.session.createCenter(
        Center(id = "", name = name, tagline = "Computer Training Center", address = "House 12, Road 5, Mirpur, Dhaka", phone = "01711-000000"),
    )
    eventually("the new center is open") { data.ready && data.center != null && data.me != null }
    return data.centerId!!
}

fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 15_000) =
    waitUntil(timeoutMs) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 15_000) =
    waitUntil(timeoutMs) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitForGone(tag: String, timeoutMs: Long = 15_000) =
    waitUntil(timeoutMs) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.waitForTextOf(tag: String, expected: String, timeoutMs: Long = 15_000) =
    waitUntil(timeoutMs) { runCatching { onNodeWithTag(tag).assertTextEquals(expected) }.isSuccess }

fun ComposeTestRule.textOf(tag: String): String? =
    onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

/**
 * Saves a full-screen PNG into the app's files dir; CI pulls them out with
 * `run-as` after the run so they can be looked at.
 */
object Screenshots {
    fun take(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(900) // let fades finish
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(instrumentation.targetContext.filesDir, "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/**
 * On a failed test: a screenshot and the screen's semantics tree, saved next to
 * the tour's screenshots. Must sit inside the compose rule (a higher `order`).
 */
class FailureCapture(private val compose: ComposeTestRule) : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        val name = "FAILED_${description.testClass.simpleName}_${description.methodName}"
        runCatching { Screenshots.take(name) }
        runCatching {
            val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
            File(dir, "$name.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 60))
        }
    }
}

/**
 * The Firestore emulator's REST API, playing "another device" (the web app,
 * another staff member). "Bearer owner" bypasses the security rules.
 */
class EmulatorRest(private val host: String = emulatorHost, project: String = Firebase.app.options.projectId!!) {
    private val base = "http://$host:8080/v1/projects/$project/databases/(default)/documents"
    private val docPrefix = "projects/$project/databases/(default)/documents"

    fun get(path: String): Map<String, Any?>? {
        val conn = URL("$base/$path").openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer owner")
        return try {
            if (conn.responseCode == 404) return null
            val body = conn.inputStream.bufferedReader().readText()
            val fields = JSONObject(body).optJSONObject("fields") ?: return emptyMap()
            fields.keys().asSequence().associateWith { decode(fields.getJSONObject(it)) }
        } finally {
            conn.disconnect()
        }
    }

    /** Replaces (or merges into) the document at [path], stamping updatedAt with the server time. */
    fun put(path: String, fields: Map<String, Any?>, merge: Boolean = false) {
        val write = JSONObject()
            .put("update", JSONObject().put("name", "$docPrefix/$path").put("fields", encode(fields)))
            .put("updateTransforms", JSONArray().put(JSONObject().put("fieldPath", "updatedAt").put("setToServerValue", "REQUEST_TIME")))
        if (merge) write.put("updateMask", JSONObject().put("fieldPaths", JSONArray(fields.keys.toList())))
        val conn = URL("$base:commit").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer owner")
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(JSONObject().put("writes", JSONArray().put(write)).toString().toByteArray()) }
            check(conn.responseCode == 200) { "Emulator write failed: ${conn.responseCode} ${conn.errorStream?.bufferedReader()?.readText()}" }
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(fields: Map<String, Any?>): JSONObject {
        val out = JSONObject()
        for ((k, v) in fields) out.put(k, value(v))
        return out
    }

    private fun value(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long -> JSONObject().put("integerValue", v.toString())
        is Double -> JSONObject().put("doubleValue", v)
        is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map { value(it) })))
        is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", encode(v.entries.associate { it.key.toString() to it.value })))
        else -> error("Unsupported value $v")
    }

    private fun decode(v: JSONObject): Any? = when {
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("doubleValue") -> v.getDouble("doubleValue")
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("timestampValue") -> v.getString("timestampValue")
        else -> null
    }
}
