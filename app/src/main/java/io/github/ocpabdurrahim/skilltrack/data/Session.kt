package io.github.ocpabdurrahim.skilltrack.data

import android.app.Activity
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import io.github.ocpabdurrahim.skilltrack.system.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A center the signed-in person belongs to, for the picker. */
data class CenterRef(val id: String, val name: String, val role: Role)

sealed interface Session {
    data object Starting : Session
    data object SignedOut : Session

    /** Signed in, picking a center. [centers] is null while they are being looked up. */
    data class Choosing(val email: String, val name: String, val centers: List<CenterRef>?, val error: String? = null) : Session

    data class InCenter(val email: String, val centerId: String) : Session
}

/**
 * Who is signed in and which center is open. The open center's records come
 * from [CenterStore]; this only finds and creates centers.
 */
class SessionManager(
    private val settings: SettingsStore,
    private val store: CenterStore,
    private val scope: CoroutineScope,
) {
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    private val _state = MutableStateFlow<Session>(Session.Starting)
    val state: StateFlow<Session> = _state

    private var currentUid: String? = null

    fun start() {
        auth.addAuthStateListener { onUser(it.currentUser) }
    }

    val user: FirebaseUser? get() = auth.currentUser

    suspend fun signInWithGoogle(activity: Activity) {
        val token = GoogleSignIn.idToken(activity)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
    }

    fun signOut() {
        store.stop()
        auth.signOut()
    }

    private fun onUser(user: FirebaseUser?) {
        val uid = user?.uid
        if (uid == currentUid) return
        currentUid = uid
        if (user == null || uid == null) {
            store.stop()
            _state.value = Session.SignedOut
            return
        }
        val email = user.email?.lowercase().orEmpty()
        _state.value = Session.Choosing(email, user.displayName.orEmpty(), null)
        scope.launch {
            val last = settings.settings.first().lastCenter
            val centerId = last?.takeIf { it.startsWith("$uid:") }?.substringAfter(':')
            if (auth.currentUser?.uid != uid) return@launch
            // Straight back into the last center (works offline); the store finds out if access was removed.
            if (centerId != null) enter(centerId) else discover()
        }
    }

    /** Looks up every center the signed-in person is on the staff of. */
    fun discover() {
        val user = auth.currentUser ?: return
        val email = user.email?.lowercase().orEmpty()
        store.stop()
        _state.value = Session.Choosing(email, user.displayName.orEmpty(), null)
        scope.launch {
            val result = runCatching { findCenters(email) }
            if (auth.currentUser?.uid != user.uid) return@launch
            result.onSuccess { centers ->
                _state.value = Session.Choosing(email, user.displayName.orEmpty(), centers)
            }.onFailure { e ->
                Log.w(TAG, "Could not look up centers", e)
                _state.value = Session.Choosing(email, user.displayName.orEmpty(), emptyList(), e.message ?: "Could not reach the server")
            }
        }
    }

    private suspend fun findCenters(email: String): List<CenterRef> {
        val memberships = db.collectionGroup("members").whereEqualTo("email", email).get().await()
        return memberships.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            if (d["deleted"] == true || d["active"] == false) return@mapNotNull null
            val centerDoc = doc.reference.parent.parent ?: return@mapNotNull null
            val name = runCatching { centerDoc.get().await().getString("name") }.getOrNull()
            CenterRef(centerDoc.id, name?.ifBlank { null } ?: "Unnamed center", Member.from(doc.id, d).role)
        }.sortedBy { it.name.lowercase() }
    }

    fun enter(centerId: String) {
        val user = auth.currentUser ?: return
        val email = user.email?.lowercase().orEmpty()
        store.start(centerId, email)
        _state.value = Session.InCenter(email, centerId)
        scope.launch { settings.update { it.copy(lastCenter = "${user.uid}:$centerId") } }
    }

    /** Back to the center picker. */
    fun switchCenter() {
        scope.launch { settings.update { it.copy(lastCenter = null) } }
        discover()
    }

    /**
     * Creates a center with the signed-in person as its owner: the center and
     * the owner's staff record go up in one batch (firestore.rules requires it).
     */
    suspend fun createCenter(profile: Center) {
        val user = auth.currentUser ?: error("Not signed in")
        val email = user.email?.lowercase() ?: error("This Google account has no email address")
        val id = Ids.new()
        val now = System.currentTimeMillis()
        val center = profile.copy(id = id, ownerUid = user.uid, ownerEmail = email, createdAt = now)
        val owner = Member(
            id = email, email = email, name = user.displayName.orEmpty(), role = Role.OWNER, active = true,
            addedBy = email, createdAt = now,
        )
        val centerRef = db.collection("centers").document(id)
        val batch = db.batch()
        batch.set(centerRef, center.fields() + mapOf("updatedAt" to FieldValue.serverTimestamp()))
        batch.set(centerRef.collection("members").document(email), owner.fields() + CenterStore.live())
        val commit = batch.commit()
        // Wait for the server when online, so a refusal shows up here; offline it stays queued.
        withContext(Dispatchers.IO) { withTimeoutOrNull(15_000) { commit.await() } }
        enter(id)
    }

    private companion object {
        const val TAG = "SkillTrackSession"
    }
}
