package io.github.ocpabdurrahim.skilltrack

import io.github.ocpabdurrahim.skilltrack.data.Attendance
import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.Certificate
import io.github.ocpabdurrahim.skilltrack.data.Col
import io.github.ocpabdurrahim.skilltrack.data.Cols
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.Enquiry
import io.github.ocpabdurrahim.skilltrack.data.EnquirySource
import io.github.ocpabdurrahim.skilltrack.data.EnquiryStatus
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.EnrollmentStatus
import io.github.ocpabdurrahim.skilltrack.data.Exam
import io.github.ocpabdurrahim.skilltrack.data.Expense
import io.github.ocpabdurrahim.skilltrack.data.Gender
import io.github.ocpabdurrahim.skilltrack.data.Ids
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.InstructorPayment
import io.github.ocpabdurrahim.skilltrack.data.Mark
import io.github.ocpabdurrahim.skilltrack.data.PayMethod
import io.github.ocpabdurrahim.skilltrack.data.PayType
import io.github.ocpabdurrahim.skilltrack.data.Payment
import io.github.ocpabdurrahim.skilltrack.data.Rec
import io.github.ocpabdurrahim.skilltrack.data.Student
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.Fees
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * A believable small computer training center, with dates relative to today,
 * for the screenshot tour.
 */
object DemoData {
    private val names = listOf(
        "Nusrat Jahan" to Gender.FEMALE, "Rafiul Islam" to Gender.MALE, "Tasnim Akter" to Gender.FEMALE,
        "Mahmudul Hasan" to Gender.MALE, "Sadia Rahman" to Gender.FEMALE, "Arif Hossain" to Gender.MALE,
        "Farzana Yeasmin" to Gender.FEMALE, "Imran Kabir" to Gender.MALE, "Sumaiya Khatun" to Gender.FEMALE,
        "Rakibul Hasan" to Gender.MALE, "Mim Chowdhury" to Gender.FEMALE, "Shakil Ahmed" to Gender.MALE,
        "Jannatul Ferdous" to Gender.FEMALE, "Tanvir Alam" to Gender.MALE, "Lamia Sultana" to Gender.FEMALE,
        "Sabbir Rahman" to Gender.MALE, "Anika Tabassum" to Gender.FEMALE, "Nayeem Islam" to Gender.MALE,
        "Riya Das" to Gender.FEMALE, "Fahim Shahriar" to Gender.MALE, "Mehjabin Nur" to Gender.FEMALE,
        "Asif Iqbal" to Gender.MALE, "Sharmin Sultana" to Gender.FEMALE, "Jubayer Hossain" to Gender.MALE,
    )
    private val fathers = listOf(
        "Abdul Karim", "Md. Rafiqul Islam", "Abul Kalam", "Shahidul Haque", "Nurul Amin", "Mizanur Rahman",
        "Golam Mostafa", "Anwar Hossain", "Delwar Hossain", "Habibur Rahman", "Jamal Uddin", "Kamrul Hasan",
    )

    fun load() {
        val today = LocalDate.now()
        val random = Random(7)
        val writes = mutableListOf<Pair<Col<*>, Rec>>()
        fun d(daysAgo: Long) = today.minusDays(daysAgo).toString()

        val office = Course(name = "Computer Office Application", code = "MSO", fee = 600_000, duration = "3 months", description = "Word, Excel, PowerPoint, typing (Bangla & English), email")
        val design = Course(name = "Graphic Design", code = "GD", fee = 1_200_000, duration = "4 months", description = "Photoshop, Illustrator, Canva, freelancing basics")
        val web = Course(name = "Web Development", code = "WEB", fee = 1_500_000, duration = "6 months", description = "HTML, CSS, JavaScript, WordPress")
        val english = Course(name = "Spoken English", code = "ENG", fee = 400_000, duration = "2 months")
        listOf(office, design, web, english).forEach { writes += Cols.courses to it }

        val rahim = Instructor(name = "Rahim Uddin", phone = "01711-223344", subject = "Office, typing", payType = PayType.MONTHLY, payRate = 1_500_000, joinDate = today.minusMonths(5).withDayOfMonth(1).toString())
        val nusrat = Instructor(name = "Shirin Akter", phone = "01819-556677", subject = "Graphic design", payType = PayType.PER_BATCH, payRate = 800_000, joinDate = today.minusMonths(8).toString())
        val kamal = Instructor(name = "Kamal Hossain", phone = "01912-889900", subject = "Web development", payType = PayType.PER_CLASS, payRate = 50_000, joinDate = today.minusMonths(3).toString())
        listOf(rahim, nusrat, kamal).forEach { writes += Cols.instructors to it }

        val yy = today.year % 100
        val mso5 = Batch(name = "MSO-$yy-B05", courseId = office.id, instructorId = rahim.id, startDate = d(52), endDate = today.plusDays(40).toString(), days = listOf("SAT", "MON", "WED"), time = "4:00 PM - 6:00 PM", room = "Lab 1", capacity = 20)
        val gd2 = Batch(name = "GD-$yy-B02", courseId = design.id, instructorId = nusrat.id, startDate = d(30), endDate = today.plusDays(90).toString(), days = listOf("SUN", "TUE", "THU"), time = "10:00 AM - 12:00 PM", room = "Lab 2", capacity = 15)
        val web1 = Batch(name = "WEB-$yy-B01", courseId = web.id, instructorId = kamal.id, startDate = today.plusDays(5).toString(), endDate = today.plusMonths(6).toString(), days = listOf("FRI", "SAT"), time = "3:00 PM - 6:00 PM", room = "Lab 2", capacity = 12)
        val mso4 = Batch(name = "MSO-$yy-B04", courseId = office.id, instructorId = rahim.id, startDate = d(150), endDate = d(60), days = listOf("SUN", "TUE", "THU"), time = "4:00 PM - 6:00 PM", room = "Lab 1", capacity = 20)
        listOf(mso5, gd2, web1, mso4).forEach { writes += Cols.batches to it }

        var reg = 0
        var receipt = 0
        val year = today.year
        val students = names.mapIndexed { i, (name, gender) ->
            reg++
            Student(
                regNo = "ST-$year-%04d".format(reg), name = name, gender = gender, fatherName = fathers[i % fathers.size],
                phone = "01${7 + i % 3}${(10_000_000 + random.nextInt(89_999_999))}".take(11),
                address = listOf("Mirpur 10, Dhaka", "Uttara Sector 7, Dhaka", "Dhanmondi 27, Dhaka", "Mohammadpur, Dhaka")[i % 4],
                education = listOf("SSC", "HSC", "Honours", "Diploma")[i % 4],
                joinDate = d((150 - i * 5).toLong().coerceAtLeast(3)),
            ).also { writes += Cols.students to it }
        }

        fun admit(s: Student, b: Batch, course: Course, date: String, roll: Int, months: Int, discount: Long, paidUpTo: Int, status: EnrollmentStatus = EnrollmentStatus.ACTIVE): Enrollment {
            val payable = course.fee - discount
            val plan = if (months > 1) Fees.plan(payable, months, LocalDate.parse(date)) else emptyList()
            val e = Enrollment(
                studentId = s.id, batchId = b.id, courseId = course.id, date = date, fee = course.fee, discount = discount,
                installments = plan, status = status, rollNo = roll, completedOn = if (status == EnrollmentStatus.COMPLETED) b.endDate else "",
            )
            writes += Cols.enrollments to e
            val schedule = Fees.schedule(e)
            schedule.take(paidUpTo).forEachIndexed { i, inst ->
                receipt++
                val day = if (inst.due > today.toString()) today.toString() else inst.due
                writes += Cols.payments to Payment(
                    enrollmentId = e.id, studentId = s.id, amount = inst.amount, date = day,
                    method = listOf(PayMethod.CASH, PayMethod.BKASH, PayMethod.NAGAD, PayMethod.CASH)[(roll + i) % 4],
                    receiptNo = "MR-$year-%04d".format(receipt), note = if (i == 0) "Admission" else "Installment ${i + 1}", receivedBy = "Office",
                    createdAt = System.currentTimeMillis() - (schedule.size - i) * 60_000L,
                )
            }
            return e
        }

        // A completed batch with results and certificates.
        val old = students.take(8).mapIndexed { i, s -> admit(s, mso4, office, mso4.startDate, i + 1, 1, if (i % 3 == 0) 50_000 else 0, 1, EnrollmentStatus.COMPLETED) }
        // The running office batch: some behind on their installments.
        val running = students.drop(8).take(10).mapIndexed { i, s ->
            admit(s, mso5, office, d(52 - i.toLong()), i + 1, 3, if (i == 2) 100_000 else 0, if (i % 4 == 3) 1 else 2)
        }
        val design2 = students.drop(18).take(6).mapIndexed { i, s ->
            admit(s, gd2, design, d(30 - i.toLong()), i + 1, 4, 0, if (i == 4) 0 else 1)
        }
        // Two of the finished students went on to design.
        admit(students[0], gd2, design, d(25), 7, 4, 200_000, 1)
        admit(students[3], web1, web, d(2), 1, 6, 0, 1)

        // Attendance on every class day so far.
        fun mark(b: Batch, roll: List<Enrollment>, from: LocalDate, to: LocalDate) {
            var day = from
            while (!day.isAfter(to)) {
                if (Academics.isClassDay(b, day)) {
                    val iso = day.toString()
                    val marks = roll.filter { it.date <= iso }.associate { e ->
                        val r = random.nextInt(100)
                        e.id to when {
                            r < 80 -> Mark.PRESENT
                            r < 88 -> Mark.LATE
                            r < 97 -> Mark.ABSENT
                            else -> Mark.EXCUSED
                        }
                    }
                    if (marks.isNotEmpty()) writes += Cols.attendance to Attendance(id = Ids.attendance(b.id, iso), batchId = b.id, date = iso, marks = marks, takenBy = "Rahim Uddin")
                }
                day = day.plusDays(1)
            }
        }
        mark(mso5, running, LocalDate.parse(mso5.startDate), today.minusDays(1))
        mark(gd2, design2, LocalDate.parse(gd2.startDate), today.minusDays(1))
        mark(mso4, old, LocalDate.parse(mso4.startDate), LocalDate.parse(mso4.endDate))

        // Exams.
        val written = Exam(batchId = mso4.id, name = "Final - Written", date = d(64), fullMarks = 50.0, passMarks = 17.0,
            marks = old.associate { it.id to (28 + random.nextInt(22)).toDouble() })
        val practical = Exam(batchId = mso4.id, name = "Final - Practical", date = d(62), fullMarks = 50.0, passMarks = 17.0,
            marks = old.mapIndexed { i, e -> e.id to (if (i == 5) 14.0 else (30 + random.nextInt(20)).toDouble()) }.toMap())
        val midterm = Exam(batchId = mso5.id, name = "Mid-term (Excel)", date = d(10), fullMarks = 40.0, passMarks = 14.0,
            marks = running.associate { it.id to (18 + random.nextInt(22)).toDouble() })
        listOf(written, practical, midterm).forEach { writes += Cols.exams to it }

        // Certificates for the ones who passed.
        val results = Academics.results(old, listOf(written, practical), today.toString())
        var cert = 0
        old.filter { results[it.id]?.failed == false }.take(5).forEach { e ->
            cert++
            writes += Cols.certificates to Certificate(enrollmentId = e.id, studentId = e.studentId, certNo = "CERT-$year-%04d".format(cert), issueDate = d(55), grade = results[e.id]?.grade?.letter.orEmpty())
        }

        // Enquiries.
        listOf(
            Enquiry(name = "Sohel Rana", phone = "01711-445566", courseId = web.id, source = EnquirySource.FACEBOOK, date = d(3), followUp = today.toString(), note = "Wants weekend batch"),
            Enquiry(name = "Afroza Begum", phone = "01822-334455", courseId = office.id, source = EnquirySource.WALK_IN, date = d(1), followUp = today.plusDays(2).toString(), note = "Daughter, HSC candidate"),
            Enquiry(name = "Mehedi Hasan", phone = "01933-221100", courseId = design.id, source = EnquirySource.PHONE, date = d(6), followUp = d(1), note = "Asked about installments"),
            Enquiry(name = "Nazmul Huda", phone = "01644-998877", courseId = english.id, source = EnquirySource.REFERRAL, date = d(12), status = EnquiryStatus.LOST, note = "Joined elsewhere"),
            Enquiry(name = students[3].name, phone = students[3].phone, courseId = web.id, source = EnquirySource.WALK_IN, date = d(4), status = EnquiryStatus.CONVERTED, studentId = students[3].id),
        ).forEach { writes += Cols.enquiries to it }

        // Expenses this month and the five before it.
        for (m in 0..5) {
            val month = YearMonth.from(today).minusMonths(m.toLong())
            fun on(day: Int) = month.atDay(day.coerceAtMost(month.lengthOfMonth())).let { if (it.isAfter(today)) today else it }.toString()
            writes += Cols.expenses to Expense(headId = "head_rent", amount = 2_500_000, date = on(5), method = PayMethod.BANK, paidTo = "Landlord")
            writes += Cols.expenses to Expense(headId = "head_bills", amount = (4_200 + random.nextLong(1_500)) * 100, date = on(10), paidTo = "DESCO")
            writes += Cols.expenses to Expense(headId = "head_internet", amount = 150_000, date = on(8), method = PayMethod.BKASH, paidTo = "Link3")
            if (m % 2 == 0) writes += Cols.expenses to Expense(headId = "head_marketing", amount = 300_000, date = on(15), note = "Facebook boost")
            if (m > 0) writes += Cols.instructorPayments to InstructorPayment(instructorId = rahim.id, amount = 1_500_000, date = on(2), month = month.minusMonths(1).toString(), method = PayMethod.BANK)
        }
        writes += Cols.expenses to Expense(headId = "head_stationery", amount = 85_000, date = d(2), note = "Certificate paper, toner")
        writes += Cols.instructorPayments to InstructorPayment(instructorId = nusrat.id, amount = 800_000, date = d(60), batchId = mso4.id, method = PayMethod.BKASH)

        container.store.putMany(writes)
    }
}
