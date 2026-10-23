package io.github.ocpabdurrahim.skilltrack.domain

import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.Installment
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Where one admission stands with its fees (docs/data-model.md, "Fees and dues"). */
data class FeeStatus(
    val payable: Long,
    val paid: Long,
    /** Negative when paid in advance. */
    val due: Long,
    val overdue: Long,
    /** The next installment that is not fully paid, if any. */
    val nextDue: String?,
    /** What is left of that installment. */
    val nextAmount: Long,
) {
    val settled: Boolean get() = due <= 0
}

object Fees {
    /** The payment plan in date order; no plan means everything is due on the admission date. */
    fun schedule(e: Enrollment): List<Installment> =
        if (e.installments.isEmpty()) listOf(Installment(e.date, e.payable)) else e.installments.sortedBy { it.due }

    fun status(e: Enrollment, paid: Long, today: String): FeeStatus {
        val payable = e.payable
        val due = payable - paid
        val plan = schedule(e)
        val dueByToday = plan.filter { it.due.isNotEmpty() && it.due <= today }.sumOf { it.amount }
        val overdue = (dueByToday - paid).coerceIn(0, max(due, 0))
        var running = 0L
        var next: Installment? = null
        var nextAmount = 0L
        for (installment in plan) {
            running += installment.amount
            if (running > paid) {
                next = installment
                nextAmount = running - paid
                break
            }
        }
        if (due <= 0) return FeeStatus(payable, paid, due, 0, null, 0)
        return FeeStatus(payable, paid, due, overdue, next?.due, if (next == null) due else min(nextAmount, due))
    }

    /**
     * [count] monthly installments for [total], the first on [first]. Each is a
     * whole number of currency units; the last one takes what is left over.
     */
    fun plan(total: Long, count: Int, first: LocalDate): List<Installment> {
        if (count <= 1 || total <= 0) return listOf(Installment(first.toString(), total.coerceAtLeast(0)))
        val each = (total / count) / 100 * 100
        return List(count) { i ->
            val amount = if (i == count - 1) total - each * (count - 1) else each
            Installment(first.plusMonths(i.toLong()).toString(), amount)
        }
    }
}
