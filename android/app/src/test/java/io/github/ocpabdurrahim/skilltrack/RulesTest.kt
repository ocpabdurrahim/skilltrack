package io.github.ocpabdurrahim.skilltrack

import io.github.ocpabdurrahim.skilltrack.data.Attendance
import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.BatchStatus
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Exam
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Installment
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.Mark
import io.github.ocpabdurrahim.skilltrack.data.PayType
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.Fees
import io.github.ocpabdurrahim.skilltrack.domain.Pay
import io.github.ocpabdurrahim.skilltrack.domain.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The shared rules from docs/data-model.md; the web app's tests check the same cases. */
class RulesTest {
    private fun enrollment(fee: Long, discount: Long = 0, plan: List<Installment> = emptyList(), date: String = "2026-01-10") =
        Enrollment(id = "e1", fee = fee, discount = discount, installments = plan, date = date)

    @Test
    fun withoutAPlanEverythingIsDueOnAdmission() {
        val e = enrollment(1_200_000, discount = 200_000)
        val s = Fees.status(e, paid = 300_000, today = "2026-01-10")
        assertEquals(1_000_000, s.payable)
        assertEquals(700_000, s.due)
        assertEquals(700_000, s.overdue)
        assertEquals("2026-01-10", s.nextDue)
        assertEquals(700_000, s.nextAmount)
    }

    @Test
    fun installmentsDecideWhatIsOverdueAndNext() {
        val plan = listOf(Installment("2026-01-10", 400_000), Installment("2026-02-10", 300_000), Installment("2026-03-10", 300_000))
        val e = enrollment(1_000_000, plan = plan)
        // Paid the first one and part of the second; the second is now due.
        val s = Fees.status(e, paid = 500_000, today = "2026-02-15")
        assertEquals(500_000, s.due)
        assertEquals(200_000, s.overdue)
        assertEquals("2026-02-10", s.nextDue)
        assertEquals(200_000, s.nextAmount)
        // Before the second installment's date nothing is overdue.
        assertEquals(0, Fees.status(e, paid = 400_000, today = "2026-02-01").overdue)
        // Paid in advance.
        val ahead = Fees.status(e, paid = 1_100_000, today = "2026-02-01")
        assertEquals(-100_000, ahead.due)
        assertEquals(0, ahead.overdue)
        assertNull(ahead.nextDue)
        assertTrue(ahead.settled)
    }

    @Test
    fun plansSplitIntoWholeUnitsWithTheRestLast() {
        val plan = Fees.plan(1_000_000, 3, LocalDate.parse("2026-01-31"))
        assertEquals(listOf(333_300L, 333_300L, 333_400L), plan.map { it.amount })
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), plan.map { it.due })
        assertEquals(1_000_000, plan.sumOf { it.amount })
    }

    @Test
    fun attendanceLeavesExcusedDaysOut() {
        val sessions = listOf(
            Attendance("b_1", marks = mapOf("e1" to Mark.PRESENT)),
            Attendance("b_2", marks = mapOf("e1" to Mark.LATE)),
            Attendance("b_3", marks = mapOf("e1" to Mark.ABSENT)),
            Attendance("b_4", marks = mapOf("e1" to Mark.EXCUSED)),
            Attendance("b_5", marks = mapOf("e2" to Mark.PRESENT)),
        )
        val stat = Academics.attendance("e1", sessions)
        assertEquals(2, stat.attended)
        assertEquals(3, stat.counted)
        assertEquals(2f / 3f, stat.rate!!, 0.0001f)
        assertNull(Academics.attendance("e9", sessions).rate)
    }

    @Test
    fun gradesFollowTheBangladeshScale() {
        assertEquals("A+", Academics.grade(80.0, false).letter)
        assertEquals("A", Academics.grade(79.99, false).letter)
        assertEquals("A-", Academics.grade(60.0, false).letter)
        assertEquals("B", Academics.grade(55.0, false).letter)
        assertEquals("C", Academics.grade(40.0, false).letter)
        assertEquals("D", Academics.grade(33.0, false).letter)
        assertEquals("F", Academics.grade(32.9, false).letter)
        assertEquals("F", Academics.grade(95.0, true).letter)
    }

    @Test
    fun resultsCountHeldExamsAndRankThePassed() {
        val roll = listOf(
            Enrollment(id = "a"), Enrollment(id = "b"), Enrollment(id = "c"), Enrollment(id = "d"),
            Enrollment(id = "x", status = EnrollmentStatus.DROPPED),
        )
        val written = Exam(
            id = "w", date = "2026-03-01", fullMarks = 50.0, passMarks = 17.0,
            marks = mapOf("a" to 45.0, "b" to 40.0, "c" to 10.0, "x" to 50.0),
        )
        val practical = Exam(
            id = "p", date = "2026-03-02", fullMarks = 50.0, passMarks = 17.0,
            marks = mapOf("a" to 35.0, "b" to 40.0, "c" to 45.0, "x" to 50.0),
        )
        // Not held yet and nobody has a mark: it does not count.
        val viva = Exam(id = "v", date = "2026-12-01", fullMarks = 20.0, passMarks = 7.0)
        val results = Academics.results(roll, listOf(written, practical, viva), today = "2026-03-10")
        assertEquals(80.0, results.getValue("a").percent, 0.001)
        assertEquals(2, results.getValue("a").examsCounted)
        assertEquals(1, results.getValue("a").position)
        assertEquals(1, results.getValue("b").position) // a tie with a
        assertTrue(results.getValue("c").failed) // 10 is below the pass mark
        assertNull(results.getValue("c").position)
        assertEquals("F", results.getValue("c").grade.letter)
        assertNull(results.getValue("x").position) // dropped
        assertTrue(results.getValue("d").failed) // missed exams that were held
    }

    @Test
    fun batchStatusComesFromItsDates() {
        val b = Batch(startDate = "2026-02-01", endDate = "2026-04-30")
        assertEquals(BatchStatus.UPCOMING, Academics.batchStatus(b, "2026-01-31"))
        assertEquals(BatchStatus.RUNNING, Academics.batchStatus(b, "2026-04-30"))
        assertEquals(BatchStatus.COMPLETED, Academics.batchStatus(b, "2026-05-01"))
        assertEquals(BatchStatus.CANCELLED, Academics.batchStatus(b.copy(cancelled = true), "2026-03-01"))
        assertEquals(BatchStatus.RUNNING, Academics.batchStatus(b.copy(endDate = ""), "2027-01-01"))
    }

    @Test
    fun instructorPayIsWorkedOutPerType() {
        val today = LocalDate.parse("2026-10-23")
        assertEquals(3, Pay.months("2026-07-15", "", today)) // Jul, Aug, Sep
        assertEquals(2, Pay.months("2026-07-01", "2026-08-20", today)) // left in August
        assertEquals(0, Pay.months("2026-10-01", "", today)) // this month is not over
        assertEquals(0, Pay.months("", "", today))
        val monthly = Instructor(payType = PayType.MONTHLY, payRate = 1_500_000, joinDate = "2026-07-01")
        val status = Pay.status(monthly, emptyList(), 0, paid = 3_000_000, today)
        assertEquals(4_500_000L, status.earned)
        assertEquals(1_500_000L, status.owed)
        val perBatch = Instructor(payType = PayType.PER_BATCH, payRate = 500_000)
        val batches = listOf(Batch(startDate = "2026-09-01"), Batch(startDate = "2026-11-01"), Batch(startDate = "2026-01-01", cancelled = true))
        assertEquals(500_000L, Pay.status(perBatch, batches, 0, 0, today).earned)
        val perClass = Instructor(payType = PayType.PER_CLASS, payRate = 50_000)
        assertEquals(1_200_000L, Pay.status(perClass, emptyList(), 24, 0, today).earned)
        assertNull(Pay.status(Instructor(payType = PayType.NONE), emptyList(), 0, 0, today).owed)
    }

    @Test
    fun numbersCountUpPerPrefixAndYear() {
        assertEquals("ST-2026-0001", Ids.nextNumber("ST", 2026, emptyList()))
        assertEquals("ST-2026-0013", Ids.nextNumber("ST", 2026, listOf("ST-2026-0012", "ST-2025-0099", "MR-2026-0500", "ST-2026-0003")))
        assertEquals("b1_20261023", Ids.attendance("b1", "2026-10-23"))
        assertEquals(20, Ids.new().length)
    }

    @Test
    fun amountsInWordsUseLakhAndCrore() {
        assertEquals("Twelve Thousand Five Hundred Taka Only", Words.amount(1_250_000))
        assertEquals("One Lakh Twenty Three Thousand Four Hundred Fifty Six Taka and Seventy Eight Paisa Only", Words.amount(12_345_678))
        assertEquals("One Crore Taka Only", Words.amount(1_000_000_000))
        assertEquals("Zero Taka Only", Words.amount(0))
    }
}
