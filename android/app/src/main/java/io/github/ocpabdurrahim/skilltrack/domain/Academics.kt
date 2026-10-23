package io.github.ocpabdurrahim.skilltrack.domain

import io.github.ocpabdurrahim.skilltrack.data.Attendance
import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.BatchStatus
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Exam
import io.github.ocpabdurrahim.skilltrack.data.Mark
import java.time.LocalDate

/** One student's attendance in one batch. Excused days count for nothing. */
data class AttendanceStat(val present: Int = 0, val late: Int = 0, val absent: Int = 0, val excused: Int = 0) {
    val attended: Int get() = present + late
    val counted: Int get() = present + late + absent
    /** 0..1, or null before anything was marked. */
    val rate: Float? get() = if (counted == 0) null else attended.toFloat() / counted
}

data class Grade(val letter: String, val point: Double)

/** One student's result over the exams that count so far (docs/data-model.md, "Results"). */
data class Result(
    val obtained: Double,
    val full: Double,
    val percent: Double,
    val failed: Boolean,
    val grade: Grade,
    /** Rank in the batch among those who passed; null for anyone who failed. */
    val position: Int?,
    val examsCounted: Int,
)

object Academics {
    fun batchStatus(b: Batch, today: String): BatchStatus = when {
        b.cancelled -> BatchStatus.CANCELLED
        b.startDate.isNotEmpty() && today < b.startDate -> BatchStatus.UPCOMING
        b.endDate.isNotEmpty() && today > b.endDate -> BatchStatus.COMPLETED
        else -> BatchStatus.RUNNING
    }

    fun isClassDay(b: Batch, day: LocalDate): Boolean = Dates.weekday(day).code in b.days

    /** Why a day is unusual for this batch, or null when it is a normal class day. */
    fun dayWarning(b: Batch, day: LocalDate): String? {
        val iso = day.toString()
        return when {
            b.startDate.isNotEmpty() && iso < b.startDate -> "The batch starts on ${Dates.full(b.startDate)}."
            b.endDate.isNotEmpty() && iso > b.endDate -> "The batch ended on ${Dates.full(b.endDate)}."
            b.days.isNotEmpty() && !isClassDay(b, day) -> "${Dates.weekday(day).short} is not one of this batch's class days."
            else -> null
        }
    }

    /** Who is marked on [day]: active and completed students admitted by then. */
    fun rollFor(enrollments: List<Enrollment>, day: String): List<Enrollment> =
        enrollments.filter { it.status != EnrollmentStatus.DROPPED && (it.date.isEmpty() || it.date <= day) }
            .sortedWith(compareBy({ it.rollNo }, { it.date }))

    fun attendance(enrollmentId: String, sessions: List<Attendance>): AttendanceStat {
        var p = 0
        var l = 0
        var a = 0
        var e = 0
        for (s in sessions) {
            when (s.marks[enrollmentId]) {
                Mark.PRESENT -> p++
                Mark.LATE -> l++
                Mark.ABSENT -> a++
                Mark.EXCUSED -> e++
                null -> Unit
            }
        }
        return AttendanceStat(p, l, a, e)
    }

    fun grade(percent: Double, failed: Boolean): Grade = when {
        failed -> Grade("F", 0.0)
        percent >= 80 -> Grade("A+", 5.0)
        percent >= 70 -> Grade("A", 4.0)
        percent >= 60 -> Grade("A-", 3.5)
        percent >= 50 -> Grade("B", 3.0)
        percent >= 40 -> Grade("C", 2.0)
        percent >= 33 -> Grade("D", 1.0)
        else -> Grade("F", 0.0)
    }

    private fun counts(exam: Exam, enrollmentId: String, today: String): Boolean =
        exam.marks.containsKey(enrollmentId) || (exam.date.isNotEmpty() && exam.date < today)

    /** Results for a batch's enrollments, keyed by enrollment id; students with no counted exam are left out. */
    fun results(enrollments: List<Enrollment>, exams: List<Exam>, today: String): Map<String, Result> {
        data class Raw(val obtained: Double, val full: Double, val failed: Boolean, val counted: Int)

        val raw = HashMap<String, Raw>()
        for (e in enrollments) {
            var obtained = 0.0
            var full = 0.0
            var failed = false
            var counted = 0
            for (exam in exams) {
                if (!counts(exam, e.id, today)) continue
                counted++
                full += exam.fullMarks
                val mark = exam.marks[e.id]
                if (mark == null) failed = true else {
                    obtained += mark
                    if (mark < exam.passMarks) failed = true
                }
            }
            if (counted > 0 && full > 0) raw[e.id] = Raw(obtained, full, failed, counted)
        }
        val dropped = enrollments.filter { it.status == EnrollmentStatus.DROPPED }.map { it.id }.toSet()
        val ranked = raw.filter { (id, r) -> !r.failed && id !in dropped }
            .map { (id, r) -> id to r.obtained / r.full * 100 }
            .sortedByDescending { it.second }
        val positions = HashMap<String, Int>()
        ranked.forEachIndexed { index, (id, percent) ->
            val previous = ranked.getOrNull(index - 1)
            positions[id] = if (previous != null && previous.second == percent) positions.getValue(previous.first) else index + 1
        }
        return raw.mapValues { (id, r) ->
            val percent = r.obtained / r.full * 100
            Result(r.obtained, r.full, percent, r.failed, grade(percent, r.failed), positions[id], r.counted)
        }
    }

    /** "80", "72.5": marks without a pointless ".0". */
    fun marks(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

    fun percent(value: Double): String = "%.1f%%".format(value)
}
