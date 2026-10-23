package io.github.ocpabdurrahim.skilltrack.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Who may create a center (docs/data-model.md, "Who may create a center"):
 * the app owner, and people the app owner approved.
 */
object AppOwner {
    /** Must match isAppAdmin() in firebase/firestore.rules and APP_OWNERS in the web app. */
    val EMAILS = setOf("ocpabdurrahim@gmail.com")

    fun isAppOwner(email: String?): Boolean = email != null && email.lowercase() in EMAILS
}

enum class RequestStatus(val label: String) { PENDING("Waiting"), APPROVED("Approved"), DECLINED("Declined") }

/** Someone asking for permission to create a center; one per email. */
data class CenterRequest(
    val email: String,
    val name: String = "",
    val centerName: String = "",
    val phone: String = "",
    val message: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val answeredBy: String = "",
) {
    fun fields(): Map<String, Any?> = mapOf(
        "email" to email, "name" to name, "centerName" to centerName, "phone" to phone, "message" to message,
        "status" to status.name, "createdAt" to createdAt, "answeredBy" to answeredBy,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = CenterRequest(
            email = d.str("email", id), name = d.str("name"), centerName = d.str("centerName"), phone = d.str("phone"),
            message = d.str("message"), status = d.enum("status", RequestStatus.PENDING), createdAt = d.long("createdAt"),
            answeredBy = d.str("answeredBy"),
        )
    }
}

/** An approval to create centers. */
data class Creator(val email: String, val name: String = "", val approvedBy: String = "", val approvedAt: Long = 0) {
    companion object {
        fun from(id: String, d: Map<String, Any?>) =
            Creator(email = d.str("email", id), name = d.str("name"), approvedBy = d.str("approvedBy"), approvedAt = d.long("approvedAt"))
    }
}

/** Whether the signed-in person may create a center right now. */
sealed interface CreateAccess {
    data object Loading : CreateAccess
    data object Allowed : CreateAccess

    /** Not approved; [request] is what they asked for so far, if anything. */
    data class NotAllowed(val request: CenterRequest?) : CreateAccess
}

class Approvals {
    private val db get() = Firebase.firestore

    private fun creatorRef(email: String) = db.collection("creators").document(email.lowercase())
    private fun requestRef(email: String) = db.collection("centerRequests").document(email.lowercase())

    /** Live: may [email] create a center? Updates the moment the app owner answers. */
    fun access(email: String): Flow<CreateAccess> {
        if (AppOwner.isAppOwner(email)) return flowOf(CreateAccess.Allowed)
        return combine(document(creatorRef(email)), document(requestRef(email))) { creator, request ->
            when {
                creator is Doc.Loading || request is Doc.Loading -> CreateAccess.Loading
                creator is Doc.Present -> CreateAccess.Allowed
                else -> CreateAccess.NotAllowed((request as? Doc.Present)?.let { CenterRequest.from(it.id, it.data) })
            }
        }
    }

    /** Sends (or re-sends) a request; it always goes in as waiting. Queued when offline. */
    suspend fun ask(request: CenterRequest) {
        val write = requestRef(request.email).set(request.copy(status = RequestStatus.PENDING, answeredBy = "").fields())
        withTimeoutOrNull(15_000) { write.await() }
    }

    // ---- The app owner's side ------------------------------------------------

    /** Every request, newest first. Only the app owner may read them. */
    fun requests(): Flow<List<CenterRequest>> = query(db.collection("centerRequests")) { snap ->
        snap.documents.map { CenterRequest.from(it.id, it.data.orEmpty()) }.sortedByDescending { it.createdAt }
    }

    fun creators(): Flow<List<Creator>> = query(db.collection("creators")) { snap ->
        snap.documents.map { Creator.from(it.id, it.data.orEmpty()) }.sortedByDescending { it.approvedAt }
    }

    /** Lets [email] create centers, and marks their request (if any) approved, in one batch. */
    suspend fun approve(email: String, name: String, by: String, hasRequest: Boolean) {
        val e = email.trim().lowercase()
        val batch = db.batch()
        batch.set(
            creatorRef(e),
            mapOf("email" to e, "name" to name.trim(), "approvedBy" to by, "approvedAt" to System.currentTimeMillis(), "updatedAt" to FieldValue.serverTimestamp()),
        )
        if (hasRequest) batch.set(requestRef(e), answer(RequestStatus.APPROVED, by), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun decline(email: String, by: String) {
        requestRef(email).set(answer(RequestStatus.DECLINED, by), SetOptions.merge()).await()
    }

    /** Takes the approval away: no new centers; centers already made stay. */
    suspend fun remove(email: String) {
        creatorRef(email).delete().await()
    }

    private fun answer(status: RequestStatus, by: String) =
        mapOf("status" to status.name, "answeredBy" to by, "updatedAt" to FieldValue.serverTimestamp())

    // ---- Listeners -------------------------------------------------------------

    private sealed interface Doc {
        data object Loading : Doc
        data object Missing : Doc
        class Present(val id: String, val data: Map<String, Any?>) : Doc
    }

    /**
     * One document as it changes. "Missing" only once the server has said so (a
     * cache miss may just mean "never downloaded"); a refused read counts as missing.
     */
    private fun document(ref: DocumentReference): Flow<Doc> = callbackFlow {
        trySend(Doc.Loading)
        val registration = ref.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            when {
                err != null -> trySend(Doc.Missing)
                snap == null -> Unit
                snap.exists() -> trySend(Doc.Present(snap.id, snap.data.orEmpty()))
                !snap.metadata.isFromCache -> trySend(Doc.Missing)
            }
        }
        awaitClose { registration.remove() }
    }

    /** A whole collection as it changes; it simply ends if the server refuses (not the app owner). */
    private fun <T> query(q: Query, map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        val registration = q.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w("SkillTrackApprovals", "A list was refused", err)
                close()
            } else if (snap != null) {
                trySend(map(snap))
            }
        }
        awaitClose { registration.remove() }
    }
}
