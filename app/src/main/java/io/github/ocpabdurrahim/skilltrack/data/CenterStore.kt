package io.github.ocpabdurrahim.skilltrack.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import kotlin.math.max

/** One synced collection under `centers/{id}/`. [money] collections are hidden from teachers. */
class Col<T : Rec>(val name: String, val money: Boolean = false, val parse: (String, Map<String, Any?>) -> T)

object Cols {
    val courses = Col("courses") { id, d -> Course.from(id, d) }
    val instructors = Col("instructors") { id, d -> Instructor.from(id, d) }
    val batches = Col("batches") { id, d -> Batch.from(id, d) }
    val students = Col("students") { id, d -> Student.from(id, d) }
    val enrollments = Col("enrollments") { id, d -> Enrollment.from(id, d) }
    val payments = Col("payments", money = true) { id, d -> Payment.from(id, d) }
    val attendance = Col("attendance") { id, d -> Attendance.from(id, d) }
    val exams = Col("exams") { id, d -> Exam.from(id, d) }
    val certificates = Col("certificates") { id, d -> Certificate.from(id, d) }
    val enquiries = Col("enquiries") { id, d -> Enquiry.from(id, d) }
    val expenseHeads = Col("expenseHeads", money = true) { id, d -> ExpenseHead.from(id, d) }
    val expenses = Col("expenses", money = true) { id, d -> Expense.from(id, d) }
    val instructorPayments = Col("instructorPayments", money = true) { id, d -> InstructorPayment.from(id, d) }

    val all: List<Col<*>> = listOf(
        courses, instructors, batches, students, enrollments, payments, attendance, exams, certificates,
        enquiries, expenseHeads, expenses, instructorPayments,
    )
}

/**
 * The open center, kept in memory and in step with Firestore
 * (docs/data-model.md, "Reading"). At start it reads each collection from
 * Firestore's local cache, which is instant and works offline, then listens only
 * for documents changed since the newest one it has. Writes update memory at
 * once and reach the server in the background; Firestore queues them offline.
 *
 * Everything here runs on the main thread, where Firestore delivers its callbacks.
 */
class CenterStore {
    private val db get() = Firebase.firestore
    private val main = Handler(Looper.getMainLooper())

    private val _data = MutableStateFlow(CenterData.EMPTY)
    val data: StateFlow<CenterData> = _data

    private var centerId: String? = null
    private var email: String = ""
    /** Bumped on every start/stop, so late callbacks from an older session are ignored. */
    private var generation = 0

    private val registrations = mutableListOf<ListenerRegistration>()
    private val dataRegistrations = mutableListOf<ListenerRegistration>()
    /** Whether the running data listeners include the money collections; null = none running. */
    private var listeningToMoney: Boolean? = null
    /** Bumped whenever the data listeners stop, for the same reason as [generation]. */
    private var dataGeneration = 0

    private val maps = HashMap<String, LinkedHashMap<String, Rec>>()
    private val lists = HashMap<String, List<Rec>>()
    private val dirty = HashSet<String>()
    /** Every id seen per collection, deleted or not; an empty-ever collection gets the starter records. */
    private val seen = HashMap<String, HashSet<String>>()
    private val serverLoaded = HashSet<String>()
    private var center: Center? = null
    private val members = LinkedHashMap<String, Member>()
    private var membersFromServer = false
    private var access = Access.LOADING
    private var pending = 0
    private var error: String? = null
    private var ready = false
    private var seeded = false

    val currentCenterId: String? get() = centerId

    // ---- Starting and stopping ---------------------------------------------

    fun start(centerId: String, email: String) = onMain {
        if (this.centerId == centerId && this.email == email) return@onMain
        stopInternal()
        this.centerId = centerId
        this.email = email
        val gen = generation
        val centerRef = centerRef()
        registrations += centerRef.addSnapshotListener { snap, err ->
            if (gen != generation) return@addSnapshotListener
            when {
                err != null -> fail(err)
                snap != null && snap.exists() -> {
                    center = Center.from(snap.id, snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty())
                    publish()
                }
            }
        }
        registrations += centerRef.collection("members").addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (gen != generation) return@addSnapshotListener
            if (err != null) {
                // Not (or no longer) on this center's staff.
                if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    access = Access.NONE
                    stopData()
                    publish()
                } else {
                    fail(err)
                }
                return@addSnapshotListener
            }
            snap ?: return@addSnapshotListener
            for (change in snap.documentChanges) {
                val doc = change.document
                val d = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (d["deleted"] == true) members.remove(doc.id) else members[doc.id] = Member.from(doc.id, d)
            }
            if (!snap.metadata.isFromCache) membersFromServer = true
            onMembership(gen)
            publish()
        }
    }

    fun stop() = onMain { stopInternal() }

    /** Starts again from the local cache, e.g. after the server turned a write down. */
    private fun reload() {
        val c = centerId ?: return
        val e = email
        stopInternal()
        start(c, e)
    }

    private fun stopInternal() {
        generation++
        registrations.forEach { it.remove() }
        registrations.clear()
        stopData()
        maps.clear()
        lists.clear()
        dirty.clear()
        seen.clear()
        center = null
        members.clear()
        membersFromServer = false
        access = Access.LOADING
        pending = 0
        error = null
        ready = false
        seeded = false
        centerId = null
        email = ""
        _data.value = CenterData.EMPTY
    }

    private fun stopData() {
        dataGeneration++
        dataRegistrations.forEach { it.remove() }
        dataRegistrations.clear()
        serverLoaded.clear()
        listeningToMoney = null
    }

    /** Opens (or re-opens) the data listeners that fit the signed-in person's role. */
    private fun onMembership(gen: Int) {
        val me = members[email]
        if (me == null || !me.active) {
            access = if (membersFromServer) Access.NONE else Access.LOADING
            if (access == Access.NONE) stopData()
            return
        }
        access = Access.OK
        val money = me.role.seesMoney
        if (listeningToMoney == money) return
        stopData()
        listeningToMoney = money
        if (!money) Cols.all.filter { it.money }.forEach { col ->
            maps.remove(col.name)
            lists.remove(col.name)
        }
        val wanted = Cols.all.filter { money || !it.money }
        var cacheReads = wanted.size
        for (col in wanted) listen(col, gen) {
            cacheReads--
            if (cacheReads == 0) {
                ready = true
                publish()
            }
        }
    }

    private fun listen(col: Col<*>, gen: Int, onCacheRead: () -> Unit) {
        val ref = centerRef().collection(col.name)
        val dataGen = dataGeneration
        ref.get(Source.CACHE).addOnCompleteListener { task ->
            if (gen != generation || dataGen != dataGeneration) return@addOnCompleteListener
            var newest = 0L
            if (task.isSuccessful) {
                for (doc in task.result.documents) {
                    apply(col, doc)
                    newest = max(newest, doc.updatedMillis())
                }
            }
            onCacheRead()
            // ">=": the newest cached document comes again, which is harmless; missing one is not.
            val changes = ref.whereGreaterThanOrEqualTo("updatedAt", Timestamp(Date(newest)))
            dataRegistrations += changes.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (gen != generation || dataGen != dataGeneration) return@addSnapshotListener
                if (err != null) {
                    fail(err)
                    return@addSnapshotListener
                }
                snap ?: return@addSnapshotListener
                for (change in snap.documentChanges) {
                    if (change.type != DocumentChange.Type.REMOVED) apply(col, change.document)
                }
                if (!snap.metadata.isFromCache && serverLoaded.add(col.name)) afterServerLoad(col)
                publish()
            }
        }
    }

    private fun apply(col: Col<*>, doc: DocumentSnapshot) {
        val d = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return
        seen.getOrPut(col.name) { HashSet() }.add(doc.id)
        val map = maps.getOrPut(col.name) { LinkedHashMap() }
        if (d["deleted"] == true) {
            map.remove(doc.id)
        } else {
            try {
                map[doc.id] = col.parse(doc.id, d)
            } catch (e: Exception) {
                Log.w(TAG, "Skipped an unreadable ${col.name} document ${doc.id}", e)
            }
        }
        dirty += col.name
    }

    /** A center that has never had expense heads gets the starter ones, once. */
    private fun afterServerLoad(col: Col<*>) {
        if (col !== Cols.expenseHeads || seeded) return
        val me = members[email] ?: return
        if (seen[col.name].isNullOrEmpty() && me.role.office) {
            seeded = true
            putMany(ExpenseHead.STARTER.map { Cols.expenseHeads to it })
        }
    }

    private fun fail(e: Exception) {
        Log.w(TAG, "Sync problem", e)
        error = e.message ?: e.javaClass.simpleName
        publish()
    }

    // ---- Snapshots ---------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun <T : Rec> list(col: Col<T>, sort: Comparator<T>): List<T> {
        if (col.name in dirty || col.name !in lists) {
            lists[col.name] = maps[col.name]?.values.orEmpty().map { it as T }.sortedWith(sort)
        }
        return lists[col.name] as List<T>
    }

    private fun publish() {
        val byName = compareBy<String> { it.lowercase() }
        val snapshot = CenterData(
            centerId = centerId,
            ready = ready,
            synced = membersFromServer && listeningToMoney != null &&
                serverLoaded.size == Cols.all.count { listeningToMoney == true || !it.money },
            pending = pending,
            error = error,
            access = access,
            center = center,
            me = members[email],
            members = members.values.sortedWith(compareBy<Member>({ it.role.ordinal }, { it.displayName.lowercase() })),
            courses = list(Cols.courses, compareBy<Course>({ it.archived }).thenBy(byName) { it.name }),
            instructors = list(Cols.instructors, compareBy<Instructor>({ it.archived }).thenBy(byName) { it.name }),
            batches = list(Cols.batches, compareByDescending<Batch> { it.startDate }.thenBy(byName) { it.name }),
            students = list(Cols.students, compareBy<Student, String>(byName) { it.name }.thenBy { it.regNo }),
            enrollments = list(Cols.enrollments, compareByDescending<Enrollment> { it.date }.thenBy { it.rollNo }),
            payments = list(Cols.payments, compareByDescending<Payment> { it.date }.thenByDescending { it.createdAt }),
            attendance = list(Cols.attendance, compareByDescending<Attendance> { it.date }),
            exams = list(Cols.exams, compareBy<Exam> { it.date }.thenBy { it.name }),
            certificates = list(Cols.certificates, compareByDescending<Certificate> { it.issueDate }.thenByDescending { it.certNo }),
            enquiries = list(Cols.enquiries, compareByDescending<Enquiry> { it.date }.thenByDescending { it.createdAt }),
            expenseHeads = list(Cols.expenseHeads, compareBy<ExpenseHead>({ it.archived }, { it.id == "head_other" }).thenBy(byName) { it.name }),
            expenses = list(Cols.expenses, compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }),
            instructorPayments = list(Cols.instructorPayments, compareByDescending<InstructorPayment> { it.date }.thenByDescending { it.createdAt }),
        )
        dirty.clear()
        _data.value = snapshot
    }

    /** Re-publishes the same records, e.g. so "today" moves on after midnight. */
    fun refresh() = onMain { if (centerId != null) publish() }

    // ---- Writes ------------------------------------------------------------

    fun <T : Rec> put(col: Col<T>, rec: T) = putMany(listOf(col to rec))

    fun putMany(items: List<Pair<Col<*>, Rec>>) = onMain {
        if (items.isEmpty() || centerId == null) return@onMain
        for ((col, rec) in items) {
            maps.getOrPut(col.name) { LinkedHashMap() }[rec.id] = rec
            seen.getOrPut(col.name) { HashSet() }.add(rec.id)
            dirty += col.name
        }
        publish()
        // Firestore batches hold at most 500 writes.
        for (chunk in items.chunked(400)) {
            val batch = db.batch()
            for ((col, rec) in chunk) batch.set(centerRef().collection(col.name).document(rec.id), rec.fields() + live())
            track(batch.commit())
        }
    }

    fun remove(col: Col<*>, id: String) = onMain {
        if (centerId == null) return@onMain
        maps[col.name]?.remove(id)
        dirty += col.name
        publish()
        track(centerRef().collection(col.name).document(id).set(tombstone(), SetOptions.merge()))
    }

    fun saveCenter(updated: Center) = onMain {
        if (centerId == null) return@onMain
        center = updated
        publish()
        track(centerRef().set(updated.fields() + mapOf("updatedAt" to FieldValue.serverTimestamp())))
    }

    fun saveMember(m: Member) = onMain {
        if (centerId == null) return@onMain
        members[m.id] = m
        publish()
        track(centerRef().collection("members").document(m.id).set(m.fields() + live()))
    }

    fun removeMember(id: String) = onMain {
        if (centerId == null) return@onMain
        members.remove(id)
        publish()
        track(centerRef().collection("members").document(id).set(tombstone(), SetOptions.merge()))
    }

    /** The photo document of a student (fetched one at a time, see [Photos]). */
    fun photoRef(studentId: String): DocumentReference? = centerId?.let { centerRef().collection("photos").document(studentId) }

    fun trackWrite(task: Task<*>) = onMain { track(task) }

    private fun track(task: Task<*>) {
        pending++
        publish()
        val gen = generation
        task.addOnCompleteListener { t ->
            if (gen != generation) return@addOnCompleteListener
            pending = (pending - 1).coerceAtLeast(0)
            val e = t.exception
            if (e != null) {
                Log.w(TAG, "A write was turned down", e)
                val denied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (denied) {
                    // Memory may now hold something the server refused; start again from the cache.
                    reload()
                    error = "You don't have permission to make that change."
                } else {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            publish()
        }
    }

    private fun centerRef() = db.collection("centers").document(centerId ?: error("No center is open"))

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val TAG = "SkillTrackStore"

        fun live(): Map<String, Any?> = mapOf("deleted" to false, "updatedAt" to FieldValue.serverTimestamp())
        fun tombstone(): Map<String, Any?> = mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp())
    }
}

private fun DocumentSnapshot.updatedMillis(): Long = getTimestamp("updatedAt")?.toDate()?.time ?: 0L
