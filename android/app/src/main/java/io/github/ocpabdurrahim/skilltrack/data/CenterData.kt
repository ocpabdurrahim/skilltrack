package io.github.ocpabdurrahim.skilltrack.data

import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.AttendanceStat
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.FeeStatus
import io.github.ocpabdurrahim.skilltrack.domain.Fees
import io.github.ocpabdurrahim.skilltrack.domain.Pay
import io.github.ocpabdurrahim.skilltrack.domain.PayStatus
import io.github.ocpabdurrahim.skilltrack.domain.Result
import java.time.LocalDate
import java.time.YearMonth

/** Whether the signed-in person may use this center (their staff record). */
enum class Access { LOADING, OK, NONE }

/**
 * One immutable snapshot of a center: every record plus the indexes and sums
 * the screens need. A new snapshot replaces it after every change; the lazy
 * parts are worked out at most once per snapshot.
 */
class CenterData(
    val centerId: String?,
    val ready: Boolean,
    val synced: Boolean,
    val pending: Int,
    val error: String?,
    val access: Access,
    val center: Center?,
    val me: Member?,
    val members: List<Member>,
    val courses: List<Course>,
    val instructors: List<Instructor>,
    val batches: List<Batch>,
    val students: List<Student>,
    val enrollments: List<Enrollment>,
    val payments: List<Payment>,
    val attendance: List<Attendance>,
    val exams: List<Exam>,
    val certificates: List<Certificate>,
    val enquiries: List<Enquiry>,
    val expenseHeads: List<ExpenseHead>,
    val expenses: List<Expense>,
    val instructorPayments: List<InstructorPayment>,
    /** "Today" as of this snapshot, so a screen left open over midnight is consistent. */
    val today: String = Dates.todayIso(),
) {
    val role: Role get() = me?.role ?: Role.TEACHER

    val courseById by lazy { courses.associateBy { it.id } }
    val instructorById by lazy { instructors.associateBy { it.id } }
    val batchById by lazy { batches.associateBy { it.id } }
    val studentById by lazy { students.associateBy { it.id } }
    val enrollmentById by lazy { enrollments.associateBy { it.id } }
    val headById by lazy { expenseHeads.associateBy { it.id } }
    val paymentById by lazy { payments.associateBy { it.id } }

    val enrollmentsByStudent by lazy { enrollments.groupBy { it.studentId } }
    val enrollmentsByBatch by lazy { enrollments.groupBy { it.batchId } }
    val paymentsByEnrollment by lazy { payments.groupBy { it.enrollmentId } }
    val paymentsByStudent by lazy { payments.groupBy { it.studentId } }
    val attendanceByBatch by lazy { attendance.groupBy { it.batchId } }
    val examsByBatch by lazy { exams.groupBy { it.batchId }.mapValues { (_, list) -> list.sortedWith(compareBy({ it.date }, { it.name })) } }
    val certificatesByEnrollment by lazy { certificates.associateBy { it.enrollmentId } }
    val instructorPaymentsBy by lazy { instructorPayments.groupBy { it.instructorId } }

    private val feeCache by lazy { HashMap<String, FeeStatus>() }
    private val resultCache by lazy { HashMap<String, Map<String, Result>>() }

    fun paid(enrollmentId: String): Long = paymentsByEnrollment[enrollmentId].orEmpty().sumOf { it.amount }

    fun fee(e: Enrollment): FeeStatus = synchronized(feeCache) {
        feeCache.getOrPut(e.id) { Fees.status(e, paid(e.id), today) }
    }

    /** Admissions whose fees still count: everything except dropped ones. */
    val countedEnrollments by lazy { enrollments.filter { it.status != EnrollmentStatus.DROPPED } }

    val totalDue by lazy { countedEnrollments.sumOf { fee(it).due.coerceAtLeast(0) } }
    val totalOverdue by lazy { countedEnrollments.sumOf { fee(it).overdue } }

    fun batchStatus(b: Batch) = Academics.batchStatus(b, today)

    val runningBatches by lazy { batches.filter { batchStatus(it) == BatchStatus.RUNNING } }

    /** Students with at least one active admission. */
    val activeStudentIds by lazy { enrollments.filter { it.status == EnrollmentStatus.ACTIVE }.map { it.studentId }.toSet() }

    fun roll(batchId: String): List<Enrollment> =
        enrollmentsByBatch[batchId].orEmpty().sortedWith(compareBy({ it.status == EnrollmentStatus.DROPPED }, { it.rollNo }))

    fun seatsTaken(batchId: String): Int = enrollmentsByBatch[batchId].orEmpty().count { it.status != EnrollmentStatus.DROPPED }

    fun attendanceStat(e: Enrollment): AttendanceStat = Academics.attendance(e.id, attendanceByBatch[e.batchId].orEmpty())

    fun results(batchId: String): Map<String, Result> = synchronized(resultCache) {
        resultCache.getOrPut(batchId) {
            Academics.results(enrollmentsByBatch[batchId].orEmpty(), examsByBatch[batchId].orEmpty(), today)
        }
    }

    fun result(e: Enrollment): Result? = results(e.batchId)[e.id]

    fun payStatus(i: Instructor, on: LocalDate = LocalDate.parse(today)): PayStatus {
        val theirs = batches.filter { it.instructorId == i.id }
        val classes = theirs.sumOf { attendanceByBatch[it.id].orEmpty().size }
        val paid = instructorPaymentsBy[i.id].orEmpty().sumOf { it.amount }
        return Pay.status(i, theirs, classes, paid, on)
    }

    fun courseName(id: String?): String = id?.let { courseById[it]?.name } ?: "—"
    fun batchName(id: String?): String = id?.let { batchById[it]?.name } ?: "—"
    fun studentName(id: String?): String = id?.let { studentById[it]?.name } ?: "Unknown student"

    // ---- Money over time ---------------------------------------------------

    fun income(month: YearMonth): Long = payments.filter { Dates.inMonth(it.date, month) }.sumOf { it.amount }
    fun expenseTotal(month: YearMonth): Long = expenses.filter { Dates.inMonth(it.date, month) }.sumOf { it.amount }
    fun instructorPayTotal(month: YearMonth): Long = instructorPayments.filter { Dates.inMonth(it.date, month) }.sumOf { it.amount }
    fun profit(month: YearMonth): Long = income(month) - expenseTotal(month) - instructorPayTotal(month)

    fun collectedOn(day: String): Long = payments.filter { it.date == day }.sumOf { it.amount }

    // ---- Numbers -------------------------------------------------------------

    fun nextRegNo(date: String): String = Ids.nextNumber(
        center?.studentPrefix ?: "ST", yearOf(date), students.map { it.regNo },
    )

    fun nextReceiptNo(date: String): String = Ids.nextNumber(
        center?.receiptPrefix ?: "MR", yearOf(date), payments.map { it.receiptNo },
    )

    fun nextCertNo(date: String): String = Ids.nextNumber(
        center?.certificatePrefix ?: "CERT", yearOf(date), certificates.map { it.certNo },
    )

    fun nextRollNo(batchId: String): Int = (enrollmentsByBatch[batchId].orEmpty().maxOfOrNull { it.rollNo } ?: 0) + 1

    private fun yearOf(date: String): Int = Dates.parse(date)?.year ?: LocalDate.now().year

    companion object {
        val EMPTY = CenterData(
            centerId = null, ready = false, synced = false, pending = 0, error = null, access = Access.LOADING,
            center = null, me = null, members = emptyList(), courses = emptyList(), instructors = emptyList(),
            batches = emptyList(), students = emptyList(), enrollments = emptyList(), payments = emptyList(),
            attendance = emptyList(), exams = emptyList(), certificates = emptyList(), enquiries = emptyList(),
            expenseHeads = emptyList(), expenses = emptyList(), instructorPayments = emptyList(),
        )
    }
}
