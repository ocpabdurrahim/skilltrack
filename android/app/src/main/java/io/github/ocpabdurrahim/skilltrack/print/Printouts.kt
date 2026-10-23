package io.github.ocpabdurrahim.skilltrack.print

import io.github.ocpabdurrahim.skilltrack.data.Batch
import io.github.ocpabdurrahim.skilltrack.data.Center
import io.github.ocpabdurrahim.skilltrack.data.Certificate
import io.github.ocpabdurrahim.skilltrack.data.Course
import io.github.ocpabdurrahim.skilltrack.data.Enrollment
import io.github.ocpabdurrahim.skilltrack.data.Exam
import io.github.ocpabdurrahim.skilltrack.data.Gender
import io.github.ocpabdurrahim.skilltrack.data.Instructor
import io.github.ocpabdurrahim.skilltrack.data.Payment
import io.github.ocpabdurrahim.skilltrack.data.Student
import io.github.ocpabdurrahim.skilltrack.domain.Academics
import io.github.ocpabdurrahim.skilltrack.domain.AttendanceStat
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.FeeStatus
import io.github.ocpabdurrahim.skilltrack.domain.Result
import io.github.ocpabdurrahim.skilltrack.domain.Words

/**
 * HTML for everything SkillTrack prints: money receipts, certificates, ID
 * cards, result sheets, mark sheets, student profiles and dues lists. Each page
 * carries the center's own name, logo and address.
 */
class Printouts(private val center: Center, private val money: (Long) -> String) {

    private fun esc(s: String?): String = s.orEmpty()
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val logo: String
        get() = if (center.logo.startsWith("data:image/")) """<img class="logo" src="${center.logo}" alt="">""" else
            """<div class="logo mark">${esc(center.name.take(1).uppercase())}</div>"""

    private fun contact(): String = listOf(center.address, center.phone, center.email, center.website)
        .filter { it.isNotBlank() }.joinToString(" · ") { esc(it) }

    private fun head(title: String? = null): String = """
        <div class="head">
          $logo
          <div class="who">
            <div class="cname">${esc(center.name)}</div>
            ${if (center.tagline.isNotBlank()) """<div class="tag">${esc(center.tagline)}</div>""" else ""}
            <div class="sub">${contact()}</div>
          </div>
          ${if (title != null) """<div class="doc-title">${esc(title)}</div>""" else ""}
        </div>
    """

    private fun page(title: String, body: String, extraCss: String = "", landscape: Boolean = false): String = """
        <!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title>
        <style>
          @page { size: A4 ${if (landscape) "landscape" else "portrait"}; margin: 10mm; }
          * { box-sizing: border-box; }
          body { margin: 0; color: #1b1b1f; font: 12px/1.45 "Noto Sans", Roboto, Arial, sans-serif; }
          .head { display: flex; align-items: center; gap: 12px; padding-bottom: 8px; border-bottom: 2px solid #4355B9; }
          .logo { width: 56px; height: 56px; object-fit: contain; }
          .logo.mark { border-radius: 14px; background: linear-gradient(135deg, #4F5FD0, #26338F); color: #fff;
                       font-size: 28px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
          .who { flex: 1; min-width: 0; }
          .cname { font-size: 19px; font-weight: 700; color: #26338F; }
          .tag { font-size: 12px; color: #444; }
          .sub { font-size: 10.5px; color: #555; }
          .doc-title { font-size: 15px; font-weight: 700; letter-spacing: .06em; color: #26338F; text-align: right; }
          h2 { font-size: 15px; margin: 14px 0 6px; color: #26338F; }
          table { width: 100%; border-collapse: collapse; }
          th, td { border: 1px solid #cfd0dc; padding: 5px 7px; text-align: left; vertical-align: top; }
          th { background: #eef0fb; font-weight: 600; }
          .num { text-align: right; white-space: nowrap; }
          .muted { color: #666; }
          .small { font-size: 10.5px; }
          .sign { display: flex; justify-content: space-between; margin-top: 34px; }
          .sign div { width: 40%; border-top: 1px solid #333; text-align: center; padding-top: 4px; }
          $extraCss
        </style></head><body>$body</body></html>
    """

    // ---- Money receipt -----------------------------------------------------

    fun receipt(
        payment: Payment,
        student: Student?,
        enrollment: Enrollment?,
        course: Course?,
        batch: Batch?,
        fee: FeeStatus?,
    ): String {
        val (major, minor) = Words.names(center.currencyCode)
        fun copy(label: String) = """
            <section class="receipt">
              ${head("MONEY RECEIPT")}
              <div class="row"><span>Receipt No: <b>${esc(payment.receiptNo)}</b></span><span class="copy">$label</span><span>Date: <b>${Dates.full(payment.date)}</b></span></div>
              <table class="kv">
                <tr><th>Received from</th><td><b>${esc(student?.name ?: "—")}</b>${student?.regNo?.takeIf { it.isNotBlank() }?.let { " &nbsp;<span class=\"muted\">Reg: ${esc(it)}</span>" } ?: ""}</td></tr>
                <tr><th>Course / batch</th><td>${esc(course?.name ?: "—")} &nbsp;·&nbsp; ${esc(batch?.name ?: "—")}${enrollment?.rollNo?.takeIf { it > 0 }?.let { " &nbsp;·&nbsp; Roll $it" } ?: ""}</td></tr>
                <tr><th>Amount</th><td><span class="amount">${esc(money(payment.amount))}</span></td></tr>
                <tr><th>In words</th><td>${esc(Words.amount(payment.amount, major, minor))}</td></tr>
                <tr><th>Paid by</th><td>${esc(payment.method.label)}${if (payment.note.isNotBlank()) " &nbsp;·&nbsp; " + esc(payment.note) else ""}</td></tr>
              </table>
              ${if (fee != null) """
              <table class="sum small">
                <tr><th>Course fee</th><th>Discount</th><th>Paid so far</th><th>Due now</th><th>Next installment</th></tr>
                <tr><td class="num">${esc(money(enrollment?.fee ?: 0))}</td><td class="num">${esc(money(enrollment?.discount ?: 0))}</td>
                    <td class="num">${esc(money(fee.paid))}</td><td class="num"><b>${esc(money(fee.due.coerceAtLeast(0)))}</b></td>
                    <td>${fee.nextDue?.let { esc(money(fee.nextAmount)) + " on " + Dates.full(it) } ?: if (fee.settled) "Fully paid" else "—"}</td></tr>
              </table>""" else ""}
              <div class="sign"><div>Received by${if (payment.receivedBy.isNotBlank()) ": " + esc(payment.receivedBy) else ""}</div><div>Authorised signature</div></div>
            </section>
        """
        val css = """
            .receipt { height: 136mm; padding: 2mm 0; display: flex; flex-direction: column; gap: 7px; }
            .row { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
            .copy { border: 1px solid #4355B9; color: #4355B9; border-radius: 10px; padding: 1px 10px; font-size: 10.5px; }
            .kv th { width: 30%; }
            .amount { font-size: 17px; font-weight: 700; }
            .cut { border-top: 1px dashed #888; margin: 3mm 0; position: relative; }
            .cut span { position: absolute; top: -8px; left: 50%; transform: translateX(-50%); background: #fff; padding: 0 8px; font-size: 10px; color: #888; }
        """
        return page(
            "Receipt ${payment.receiptNo}",
            copy("Student copy") + """<div class="cut"><span>✂ cut here</span></div>""" + copy("Office copy"),
            css,
        )
    }

    // ---- Certificate -------------------------------------------------------

    fun certificate(
        cert: Certificate,
        student: Student?,
        enrollment: Enrollment?,
        course: Course?,
        batch: Batch?,
        instructor: Instructor?,
    ): String {
        val relation = when (student?.gender) {
            Gender.MALE -> "son of"
            Gender.FEMALE -> "daughter of"
            else -> "son/daughter of"
        }
        val parent = student?.fatherName?.takeIf { it.isNotBlank() } ?: student?.motherName?.takeIf { it.isNotBlank() }
        val period = listOfNotNull(
            batch?.startDate?.takeIf { it.isNotBlank() }?.let { Dates.full(it) },
            (enrollment?.completedOn?.takeIf { it.isNotBlank() } ?: batch?.endDate?.takeIf { it.isNotBlank() })?.let { Dates.full(it) },
        ).joinToString(" to ")
        val css = """
            body { font-family: Georgia, "Noto Serif", serif; }
            .frame { height: 188mm; border: 3mm double #26338F; padding: 10mm 16mm; text-align: center; position: relative;
                     background: radial-gradient(circle at 50% 40%, #f7f8ff 0, #fff 70%); }
            .frame .logo { width: 70px; height: 70px; margin: 0 auto; }
            .frame .logo.mark { display: flex; }
            .center-name { font-size: 24px; font-weight: 700; color: #26338F; margin-top: 4px; }
            .center-sub { font-size: 12px; color: #555; }
            .title { font-size: 34px; letter-spacing: .12em; color: #8a6d1d; margin: 14px 0 4px; font-weight: 700; }
            .subtitle { font-size: 14px; letter-spacing: .3em; color: #26338F; }
            .lead { font-size: 15px; margin-top: 14px; }
            .name { font-size: 30px; font-weight: 700; color: #1b1b1f; border-bottom: 1px solid #b9bce0; display: inline-block; padding: 0 30px 2px; margin: 6px 0; }
            .course { font-size: 21px; font-weight: 700; color: #26338F; }
            .grade { display: inline-block; margin-top: 8px; font-size: 15px; border: 1.5px solid #8a6d1d; color: #8a6d1d; border-radius: 16px; padding: 2px 16px; }
            .meta { position: absolute; left: 16mm; right: 16mm; bottom: 8mm; display: flex; justify-content: space-between; align-items: flex-end; font-size: 12px; }
            .meta .sig { width: 30%; border-top: 1px solid #333; padding-top: 4px; }
            .meta .no { font-family: "Noto Sans", Arial, sans-serif; font-size: 11px; color: #444; text-align: center; }
        """
        val body = """
            <div class="frame">
              $logo
              <div class="center-name">${esc(center.name)}</div>
              <div class="center-sub">${esc(center.tagline.ifBlank { center.address })}</div>
              <div class="title">CERTIFICATE</div>
              <div class="subtitle">OF COMPLETION</div>
              <div class="lead">This is to certify that</div>
              <div class="name">${esc(student?.name ?: "")}</div>
              ${if (parent != null) """<div class="lead" style="margin-top:2px">$relation ${esc(parent)}</div>""" else ""}
              <div class="lead">has successfully completed the course</div>
              <div class="course">${esc(course?.name ?: "")}${course?.duration?.takeIf { it.isNotBlank() }?.let { " <span style=\"font-size:15px;font-weight:400\">(${esc(it)})</span>" } ?: ""}</div>
              <div class="lead">${if (period.isNotBlank()) "held from $period" else ""}${if (batch != null && batch.name.isNotBlank()) " &nbsp;·&nbsp; Batch " + esc(batch.name) else ""}</div>
              ${if (cert.grade.isNotBlank()) """<div class="grade">Grade: ${esc(cert.grade)}</div>""" else ""}
              <div class="meta">
                <div class="sig">${esc(instructor?.name ?: "Course instructor")}</div>
                <div class="no">Certificate No: <b>${esc(cert.certNo)}</b><br>Reg No: ${esc(student?.regNo ?: "")}<br>Issued: ${Dates.full(cert.issueDate)}</div>
                <div class="sig">${esc(center.signatory.ifBlank { "Director" })}</div>
              </div>
            </div>
        """
        return page("Certificate ${cert.certNo}", body, css, landscape = true)
    }

    // ---- ID cards ----------------------------------------------------------

    class Card(val student: Student, val course: Course?, val batch: Batch?, val photo: String?)

    /** CR80-size cards (85.6 × 54 mm), ten to an A4 page, ready to cut out. */
    fun idCards(cards: List<Card>): String {
        val css = """
            .grid { display: grid; grid-template-columns: repeat(2, 85.6mm); grid-auto-rows: 54mm; gap: 4mm 6mm; justify-content: center; }
            .card { border: 1px solid #b9bce0; border-radius: 3mm; overflow: hidden; display: flex; flex-direction: column; break-inside: avoid; }
            .band { background: linear-gradient(135deg, #4F5FD0, #26338F); color: #fff; display: flex; align-items: center; gap: 6px; padding: 2mm 3mm; }
            .band img, .band .mark { width: 9mm; height: 9mm; border-radius: 2mm; background: #fff; object-fit: contain; }
            .band .mark { color: #26338F; font-weight: 700; display: flex; align-items: center; justify-content: center; }
            .band .n { font-weight: 700; font-size: 11px; line-height: 1.15; }
            .band .t { font-size: 8px; opacity: .9; }
            .content { flex: 1; display: flex; gap: 3mm; padding: 2.5mm 3mm; }
            .photo { width: 21mm; height: 26mm; border: 1px solid #ccd; border-radius: 1.5mm; object-fit: cover; background: #eef0fb;
                     display: flex; align-items: center; justify-content: center; color: #8890c0; font-size: 20px; font-weight: 700; }
            .info { font-size: 9px; line-height: 1.35; min-width: 0; }
            .info .sn { font-size: 12px; font-weight: 700; color: #1b1b1f; margin-bottom: 1mm; }
            .info b { color: #26338F; }
            .foot { font-size: 7.5px; color: #555; padding: 0 3mm 1.5mm; display: flex; justify-content: space-between; }
            .badge { background: #eef0fb; color: #26338F; font-size: 8px; font-weight: 700; letter-spacing: .1em; text-align: center; padding: 0.6mm; }
        """
        val small = if (center.logo.startsWith("data:image/")) """<img src="${center.logo}" alt="">""" else
            """<div class="mark">${esc(center.name.take(1).uppercase())}</div>"""
        val body = cards.joinToString("", prefix = """<div class="grid">""", postfix = "</div>") { c ->
            val photo = c.photo?.let { """<img class="photo" src="$it" alt="">""" }
                ?: """<div class="photo">${esc(c.student.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() })}</div>"""
            """
            <div class="card">
              <div class="band">$small<div><div class="n">${esc(center.name)}</div><div class="t">${esc(center.tagline)}</div></div></div>
              <div class="badge">STUDENT ID CARD</div>
              <div class="content">
                $photo
                <div class="info">
                  <div class="sn">${esc(c.student.name)}</div>
                  <div><b>ID:</b> ${esc(c.student.regNo)}</div>
                  ${c.course?.let { "<div><b>Course:</b> ${esc(it.name)}</div>" } ?: ""}
                  ${c.batch?.let { "<div><b>Batch:</b> ${esc(it.name)}${if (it.time.isNotBlank()) " (" + esc(it.time) + ")" else ""}</div>" } ?: ""}
                  ${if (c.student.phone.isNotBlank()) "<div><b>Phone:</b> ${esc(c.student.phone)}</div>" else ""}
                  ${c.batch?.endDate?.takeIf { it.isNotBlank() }?.let { "<div><b>Valid till:</b> ${Dates.full(it)}</div>" } ?: ""}
                </div>
              </div>
              <div class="foot"><span>${esc(center.phone)}</span><span>${esc(center.address.take(48))}</span></div>
            </div>
            """
        }
        return page("Student ID cards", body, css)
    }

    // ---- Results -----------------------------------------------------------

    class ResultRow(val enrollment: Enrollment, val student: Student?, val result: Result?)

    fun resultSheet(batch: Batch, course: Course?, exams: List<Exam>, rows: List<ResultRow>): String {
        val examHeads = exams.joinToString("") { "<th class=\"num\">${esc(it.name)}<br><span class=\"muted small\">${Academics.marks(it.fullMarks)}</span></th>" }
        val body = head("RESULT SHEET") + """
            <h2>${esc(course?.name ?: "")} &nbsp;·&nbsp; Batch ${esc(batch.name)}</h2>
            <table>
              <tr><th>Roll</th><th>Reg No</th><th>Name</th>$examHeads<th class="num">Total</th><th class="num">%</th><th>Grade</th><th class="num">GPA</th><th class="num">Position</th></tr>
              ${rows.joinToString("") { r ->
                  val cells = exams.joinToString("") { ex -> "<td class=\"num\">${ex.marks[r.enrollment.id]?.let { Academics.marks(it) } ?: "—"}</td>" }
                  val res = r.result
                  """<tr><td>${r.enrollment.rollNo}</td><td>${esc(r.student?.regNo)}</td><td>${esc(r.student?.name)}</td>$cells
                     <td class="num">${res?.let { Academics.marks(it.obtained) + " / " + Academics.marks(it.full) } ?: "—"}</td>
                     <td class="num">${res?.let { "%.1f".format(it.percent) } ?: "—"}</td>
                     <td><b>${res?.grade?.letter ?: "—"}</b></td>
                     <td class="num">${res?.let { "%.2f".format(it.grade.point) } ?: "—"}</td>
                     <td class="num">${res?.position ?: "—"}</td></tr>"""
              }}
            </table>
            <p class="small muted">Grades: A+ 80–100 · A 70–79 · A- 60–69 · B 50–59 · C 40–49 · D 33–39 · F below 33 or any exam below its pass mark.</p>
            <div class="sign"><div>Instructor</div><div>${esc(center.signatory.ifBlank { "Director" })}</div></div>
        """
        return page("Result sheet ${batch.name}", body, landscape = exams.size > 4)
    }

    fun markSheet(student: Student, enrollment: Enrollment, course: Course?, batch: Batch?, exams: List<Exam>, result: Result?, attendance: AttendanceStat): String {
        val body = head("MARK SHEET") + """
            <table style="margin-top:10px">
              <tr><th>Name</th><td>${esc(student.name)}</td><th>Reg No</th><td>${esc(student.regNo)}</td></tr>
              <tr><th>Course</th><td>${esc(course?.name)}</td><th>Batch / Roll</th><td>${esc(batch?.name)} / ${enrollment.rollNo}</td></tr>
              <tr><th>Father's name</th><td>${esc(student.fatherName)}</td><th>Attendance</th><td>${attendance.rate?.let { "%.0f%%".format(it * 100) + " (${attendance.attended} of ${attendance.counted})" } ?: "—"}</td></tr>
            </table>
            <h2>Exams</h2>
            <table>
              <tr><th>Exam</th><th>Date</th><th class="num">Full marks</th><th class="num">Pass marks</th><th class="num">Obtained</th></tr>
              ${exams.joinToString("") { ex ->
                  "<tr><td>${esc(ex.name)}</td><td>${Dates.full(ex.date)}</td><td class=\"num\">${Academics.marks(ex.fullMarks)}</td><td class=\"num\">${Academics.marks(ex.passMarks)}</td><td class=\"num\">${ex.marks[enrollment.id]?.let { Academics.marks(it) } ?: "—"}</td></tr>"
              }}
              <tr><th colspan="4">Total</th><th class="num">${result?.let { Academics.marks(it.obtained) + " / " + Academics.marks(it.full) } ?: "—"}</th></tr>
            </table>
            <table style="margin-top:10px; width:60%">
              <tr><th>Percentage</th><td>${result?.let { "%.1f%%".format(it.percent) } ?: "—"}</td></tr>
              <tr><th>Grade (GPA)</th><td><b>${result?.grade?.letter ?: "—"}</b>${result?.let { " (" + "%.2f".format(it.grade.point) + ")" } ?: ""}</td></tr>
              <tr><th>Position in batch</th><td>${result?.position ?: "—"}</td></tr>
            </table>
            <div class="sign"><div>Instructor</div><div>${esc(center.signatory.ifBlank { "Director" })}</div></div>
        """
        return page("Mark sheet ${student.regNo}", body)
    }

    // ---- Student profile / admission form -----------------------------------

    class ProfileAdmission(val enrollment: Enrollment, val course: Course?, val batch: Batch?, val fee: FeeStatus?)

    fun profile(student: Student, photo: String?, admissions: List<ProfileAdmission>): String {
        fun row(label: String, value: String) = "<tr><th>$label</th><td>${esc(value).ifBlank { "—" }}</td></tr>"
        val photoHtml = photo?.let { """<img src="$it" style="width:32mm;height:40mm;object-fit:cover;border:1px solid #ccd;border-radius:2mm">""" }
            ?: """<div style="width:32mm;height:40mm;border:1px dashed #aab;border-radius:2mm;display:flex;align-items:center;justify-content:center;color:#889">Photo</div>"""
        val body = head("ADMISSION FORM") + """
            <div style="display:flex;gap:14px;margin-top:12px">
              <table style="flex:1">
                ${row("Reg No", student.regNo)}${row("Name", student.name)}${row("Father's name", student.fatherName)}
                ${row("Mother's name", student.motherName)}${row("Gender", if (student.gender == Gender.NONE) "" else student.gender.label)}
                ${row("Date of birth", Dates.full(student.dob, ""))}${row("NID / birth reg.", student.idNo)}
              </table>
              $photoHtml
            </div>
            <table style="margin-top:10px">
              ${row("Phone", student.phone)}${row("Guardian's phone", student.guardianPhone)}${row("Email", student.email)}
              ${row("Address", student.address)}${row("Education", listOf(student.education, student.institution).filter { it.isNotBlank() }.joinToString(", "))}
              ${row("Joined", Dates.full(student.joinDate, ""))}
            </table>
            <h2>Courses</h2>
            <table>
              <tr><th>Course</th><th>Batch</th><th>Admitted</th><th>Status</th><th class="num">Payable</th><th class="num">Paid</th><th class="num">Due</th></tr>
              ${admissions.joinToString("") { a ->
                  "<tr><td>${esc(a.course?.name)}</td><td>${esc(a.batch?.name)}</td><td>${Dates.full(a.enrollment.date)}</td><td>${a.enrollment.status.label}</td>" +
                      "<td class=\"num\">${a.fee?.let { esc(money(it.payable)) } ?: "—"}</td><td class=\"num\">${a.fee?.let { esc(money(it.paid)) } ?: "—"}</td>" +
                      "<td class=\"num\">${a.fee?.let { esc(money(it.due.coerceAtLeast(0))) } ?: "—"}</td></tr>"
              }}
            </table>
            <p class="small" style="margin-top:14px">I confirm that the information above is correct and I agree to follow the rules of ${esc(center.name)}.</p>
            <div class="sign"><div>Student's signature</div><div>${esc(center.signatory.ifBlank { "Director" })}</div></div>
        """
        return page("Admission form ${student.regNo}", body)
    }

    // ---- Dues list ---------------------------------------------------------

    class DueRow(val student: Student?, val course: Course?, val batch: Batch?, val fee: FeeStatus)

    fun duesList(title: String, rows: List<DueRow>): String {
        val body = head("DUES LIST") + """
            <h2>${esc(title)} <span class="muted small">(${Dates.full(Dates.today())})</span></h2>
            <table>
              <tr><th>#</th><th>Student</th><th>Phone</th><th>Course / batch</th><th class="num">Payable</th><th class="num">Paid</th><th class="num">Due</th><th class="num">Overdue</th><th>Next due</th></tr>
              ${rows.withIndex().joinToString("") { (i, r) ->
                  "<tr><td>${i + 1}</td><td>${esc(r.student?.name)}<br><span class=\"muted small\">${esc(r.student?.regNo)}</span></td><td>${esc(r.student?.phone)}</td>" +
                      "<td>${esc(r.course?.name)}<br><span class=\"muted small\">${esc(r.batch?.name)}</span></td>" +
                      "<td class=\"num\">${esc(money(r.fee.payable))}</td><td class=\"num\">${esc(money(r.fee.paid))}</td>" +
                      "<td class=\"num\"><b>${esc(money(r.fee.due))}</b></td><td class=\"num\">${esc(money(r.fee.overdue))}</td>" +
                      "<td>${r.fee.nextDue?.let { Dates.full(it) } ?: "—"}</td></tr>"
              }}
              <tr><th colspan="6">Total</th><th class="num">${esc(money(rows.sumOf { it.fee.due }))}</th><th class="num">${esc(money(rows.sumOf { it.fee.overdue }))}</th><th></th></tr>
            </table>
        """
        return page("Dues list", body)
    }
}
