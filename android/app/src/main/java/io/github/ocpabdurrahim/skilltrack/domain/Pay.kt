package io.github.ocpabdurrahim.skilltrack.domain

import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.PayType
import java.time.LocalDate
import java.time.YearMonth

/** An instructor's pay so far (docs/data-model.md, "Instructor pay"). */
data class PayStatus(
    /** Null for [PayType.NONE]: nothing is worked out. */
    val earned: Long?,
    val paid: Long,
    /** How many months, batches or classes [earned] is made of. */
    val units: Int,
) {
    val owed: Long? get() = earned?.let { it - paid }
}

object Pay {
    /** Months from the joining month to last month (or the leaving month, if earlier), both included. */
    fun months(joinDate: String, leaveDate: String, today: LocalDate): Int {
        val join = Dates.parse(joinDate)?.let(YearMonth::from) ?: return 0
        var last = YearMonth.from(today).minusMonths(1)
        Dates.parse(leaveDate)?.let(YearMonth::from)?.let { if (it < last) last = it }
        val count = (last.year * 12 + last.monthValue) - (join.year * 12 + join.monthValue) + 1
        return count.coerceAtLeast(0)
    }

    /**
     * [batches] are the instructor's own; [classesHeld] counts attendance records
     * in them.
     */
    fun status(i: Instructor, batches: List<Batch>, classesHeld: Int, paid: Long, today: LocalDate): PayStatus {
        val iso = today.toString()
        val units = when (i.payType) {
            PayType.MONTHLY -> months(i.joinDate, i.leaveDate, today)
            PayType.PER_BATCH -> batches.count { !it.cancelled && it.startDate.isNotEmpty() && it.startDate <= iso }
            PayType.PER_CLASS -> classesHeld
            PayType.NONE -> 0
        }
        val earned = if (i.payType == PayType.NONE) null else i.payRate * units
        return PayStatus(earned, paid, units)
    }

    fun unitLabel(type: PayType, units: Int): String = when (type) {
        PayType.MONTHLY -> if (units == 1) "1 month" else "$units months"
        PayType.PER_BATCH -> if (units == 1) "1 batch" else "$units batches"
        PayType.PER_CLASS -> if (units == 1) "1 class" else "$units classes"
        PayType.NONE -> ""
    }
}
