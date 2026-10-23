package io.github.ocpabdurrahim.skilltrack.data

import io.github.ocpabdurrahim.skilltrack.domain.Grouping

// The center's records, field for field as docs/data-model.md describes them.
// Each type knows how to read itself from a Firestore document (`from`) and
// what to write back (`fields`); the store adds `deleted` and `updatedAt`.

/** A synced record. */
sealed interface Rec {
    val id: String
    fun fields(): Map<String, Any?>
}

enum class Role(val label: String, val blurb: String) {
    OWNER("Owner", "Everything, including staff and center settings"),
    ADMIN("Admin", "Everything except the owner's own record"),
    OFFICE("Office", "Students, admissions, fees, enquiries and expenses"),
    TEACHER("Teacher", "Attendance and exam marks; no money"),
    ;

    val manages: Boolean get() = this == OWNER || this == ADMIN
    /** Students, admissions, fees, expenses, enquiries, certificates. */
    val office: Boolean get() = this != TEACHER
    /** Attendance and exams. */
    val academic: Boolean get() = this != OFFICE
    val seesMoney: Boolean get() = this != TEACHER
}

enum class PayType(val label: String) {
    MONTHLY("Monthly salary"),
    PER_BATCH("Per batch"),
    PER_CLASS("Per class"),
    NONE("Not worked out"),
}

enum class Gender(val code: String, val label: String) {
    NONE("", "Not given"),
    MALE("MALE", "Male"),
    FEMALE("FEMALE", "Female"),
    OTHER("OTHER", "Other"),
    ;

    companion object {
        fun of(code: Any?) = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class EnrollmentStatus(val label: String) { ACTIVE("Active"), COMPLETED("Completed"), DROPPED("Dropped") }

enum class PayMethod(val label: String) {
    CASH("Cash"), BKASH("bKash"), NAGAD("Nagad"), ROCKET("Rocket"), BANK("Bank"), CARD("Card"), OTHER("Other"),
}

enum class EnquirySource(val label: String) {
    WALK_IN("Walk-in"), PHONE("Phone call"), FACEBOOK("Facebook"), REFERRAL("Referral"), OTHER("Other"),
}

enum class EnquiryStatus(val label: String) { OPEN("Open"), CONVERTED("Converted"), LOST("Lost") }

enum class Mark(val code: String, val label: String) {
    PRESENT("P", "Present"), ABSENT("A", "Absent"), LATE("L", "Late"), EXCUSED("E", "Excused"),
    ;

    companion object {
        fun of(code: Any?): Mark? = entries.firstOrNull { it.code == code }
    }
}

/** Class days in the order a Bangladeshi week runs. */
enum class Weekday(val code: String, val short: String, val iso: Int) {
    SAT("SAT", "Sat", 6), SUN("SUN", "Sun", 7), MON("MON", "Mon", 1), TUE("TUE", "Tue", 2),
    WED("WED", "Wed", 3), THU("THU", "Thu", 4), FRI("FRI", "Fri", 5),
    ;

    companion object {
        fun ofIso(day: Int): Weekday = entries.first { it.iso == day }
    }
}

enum class BatchStatus(val label: String) { UPCOMING("Upcoming"), RUNNING("Running"), COMPLETED("Completed"), CANCELLED("Cancelled") }

// ---- Reading helpers --------------------------------------------------------

internal fun Map<String, Any?>.str(key: String, fallback: String = "") = this[key] as? String ?: fallback
internal fun Map<String, Any?>.strOrNull(key: String) = (this[key] as? String)?.takeIf { it.isNotEmpty() }
internal fun Map<String, Any?>.long(key: String, fallback: Long = 0L) = (this[key] as? Number)?.toLong() ?: fallback
internal fun Map<String, Any?>.int(key: String, fallback: Int = 0) = (this[key] as? Number)?.toInt() ?: fallback
internal fun Map<String, Any?>.double(key: String, fallback: Double = 0.0) = (this[key] as? Number)?.toDouble() ?: fallback
internal fun Map<String, Any?>.bool(key: String, fallback: Boolean = false) = this[key] as? Boolean ?: fallback
internal inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == this[key] } ?: fallback

// ---- The center -------------------------------------------------------------

data class Center(
    val id: String,
    val name: String = "",
    val tagline: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val logo: String = "",
    val signatory: String = "Director",
    val currencyCode: String = "BDT",
    val currencySymbol: String = "৳",
    val grouping: Grouping = Grouping.SOUTH_ASIAN,
    val studentPrefix: String = "ST",
    val receiptPrefix: String = "MR",
    val certificatePrefix: String = "CERT",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val createdAt: Long = 0,
) {
    fun fields(): Map<String, Any?> = mapOf(
        "name" to name, "tagline" to tagline, "address" to address, "phone" to phone, "email" to email,
        "website" to website, "logo" to logo, "signatory" to signatory, "currencyCode" to currencyCode,
        "currencySymbol" to currencySymbol, "grouping" to grouping.name, "studentPrefix" to studentPrefix,
        "receiptPrefix" to receiptPrefix, "certificatePrefix" to certificatePrefix, "ownerUid" to ownerUid,
        "ownerEmail" to ownerEmail, "createdAt" to createdAt,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Center(
            id = id, name = d.str("name"), tagline = d.str("tagline"), address = d.str("address"), phone = d.str("phone"),
            email = d.str("email"), website = d.str("website"), logo = d.str("logo"), signatory = d.str("signatory", "Director"),
            currencyCode = d.str("currencyCode", "BDT"), currencySymbol = d.str("currencySymbol", "৳"),
            grouping = if (d["grouping"] == "INTERNATIONAL") Grouping.INTERNATIONAL else Grouping.SOUTH_ASIAN,
            studentPrefix = d.str("studentPrefix", "ST").ifBlank { "ST" },
            receiptPrefix = d.str("receiptPrefix", "MR").ifBlank { "MR" },
            certificatePrefix = d.str("certificatePrefix", "CERT").ifBlank { "CERT" },
            ownerUid = d.str("ownerUid"), ownerEmail = d.str("ownerEmail"), createdAt = d.long("createdAt"),
        )
    }
}

data class Member(
    override val id: String,
    val email: String = id,
    val name: String = "",
    val role: Role = Role.TEACHER,
    val active: Boolean = true,
    val instructorId: String? = null,
    val addedBy: String = "",
    val createdAt: Long = 0,
) : Rec {
    override fun fields() = mapOf(
        "email" to email, "name" to name, "role" to role.name, "active" to active,
        "instructorId" to instructorId, "addedBy" to addedBy, "createdAt" to createdAt,
    )

    val displayName: String get() = name.ifBlank { email }

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Member(
            id = id, email = d.str("email", id), name = d.str("name"),
            // Least access for anything unexpected; the server rules decide anyway.
            role = d.enum("role", Role.TEACHER), active = d.bool("active", true),
            instructorId = d.strOrNull("instructorId"), addedBy = d.str("addedBy"), createdAt = d.long("createdAt"),
        )
    }
}

// ---- Courses, instructors, batches ------------------------------------------

data class Course(
    override val id: String = Ids.new(),
    val name: String = "",
    val code: String = "",
    val fee: Long = 0,
    val duration: String = "",
    val description: String = "",
    val archived: Boolean = false,
) : Rec {
    override fun fields() = mapOf(
        "name" to name, "code" to code, "fee" to fee, "duration" to duration,
        "description" to description, "archived" to archived,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Course(
            id = id, name = d.str("name"), code = d.str("code"), fee = d.long("fee"), duration = d.str("duration"),
            description = d.str("description"), archived = d.bool("archived"),
        )
    }
}

data class Instructor(
    override val id: String = Ids.new(),
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val subject: String = "",
    val payType: PayType = PayType.MONTHLY,
    val payRate: Long = 0,
    val joinDate: String = "",
    val leaveDate: String = "",
    val note: String = "",
    val archived: Boolean = false,
) : Rec {
    override fun fields() = mapOf(
        "name" to name, "phone" to phone, "email" to email, "subject" to subject, "payType" to payType.name,
        "payRate" to payRate, "joinDate" to joinDate, "leaveDate" to leaveDate, "note" to note, "archived" to archived,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Instructor(
            id = id, name = d.str("name"), phone = d.str("phone"), email = d.str("email"), subject = d.str("subject"),
            payType = d.enum("payType", PayType.MONTHLY), payRate = d.long("payRate"), joinDate = d.str("joinDate"),
            leaveDate = d.str("leaveDate"), note = d.str("note"), archived = d.bool("archived"),
        )
    }
}

data class Batch(
    override val id: String = Ids.new(),
    val name: String = "",
    val courseId: String = "",
    val instructorId: String? = null,
    val startDate: String = "",
    val endDate: String = "",
    val days: List<String> = emptyList(),
    val time: String = "",
    val room: String = "",
    val capacity: Int = 0,
    val cancelled: Boolean = false,
    val note: String = "",
) : Rec {
    override fun fields() = mapOf(
        "name" to name, "courseId" to courseId, "instructorId" to instructorId, "startDate" to startDate,
        "endDate" to endDate, "days" to days, "time" to time, "room" to room, "capacity" to capacity,
        "cancelled" to cancelled, "note" to note,
    )

    val weekdays: List<Weekday> get() = Weekday.entries.filter { it.code in days }

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Batch(
            id = id, name = d.str("name"), courseId = d.str("courseId"), instructorId = d.strOrNull("instructorId"),
            startDate = d.str("startDate"), endDate = d.str("endDate"),
            days = (d["days"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            time = d.str("time"), room = d.str("room"), capacity = d.int("capacity"),
            cancelled = d.bool("cancelled"), note = d.str("note"),
        )
    }
}

// ---- Students and admissions ------------------------------------------------

data class Student(
    override val id: String = Ids.new(),
    val regNo: String = "",
    val name: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val gender: Gender = Gender.NONE,
    val dob: String = "",
    val phone: String = "",
    val guardianPhone: String = "",
    val email: String = "",
    val address: String = "",
    val education: String = "",
    val institution: String = "",
    val idNo: String = "",
    val joinDate: String = "",
    val note: String = "",
    val archived: Boolean = false,
) : Rec {
    override fun fields() = mapOf(
        "regNo" to regNo, "name" to name, "fatherName" to fatherName, "motherName" to motherName,
        "gender" to gender.code, "dob" to dob, "phone" to phone, "guardianPhone" to guardianPhone, "email" to email,
        "address" to address, "education" to education, "institution" to institution, "idNo" to idNo,
        "joinDate" to joinDate, "note" to note, "archived" to archived,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Student(
            id = id, regNo = d.str("regNo"), name = d.str("name"), fatherName = d.str("fatherName"),
            motherName = d.str("motherName"), gender = Gender.of(d["gender"]), dob = d.str("dob"), phone = d.str("phone"),
            guardianPhone = d.str("guardianPhone"), email = d.str("email"), address = d.str("address"),
            education = d.str("education"), institution = d.str("institution"), idNo = d.str("idNo"),
            joinDate = d.str("joinDate"), note = d.str("note"), archived = d.bool("archived"),
        )
    }
}

data class Installment(val due: String, val amount: Long)

data class Enrollment(
    override val id: String = Ids.new(),
    val studentId: String = "",
    val batchId: String = "",
    val courseId: String = "",
    val date: String = "",
    val fee: Long = 0,
    val discount: Long = 0,
    val installments: List<Installment> = emptyList(),
    val status: EnrollmentStatus = EnrollmentStatus.ACTIVE,
    val rollNo: Int = 0,
    val completedOn: String = "",
    val note: String = "",
) : Rec {
    override fun fields() = mapOf(
        "studentId" to studentId, "batchId" to batchId, "courseId" to courseId, "date" to date, "fee" to fee,
        "discount" to discount, "installments" to installments.map { mapOf("due" to it.due, "amount" to it.amount) },
        "status" to status.name, "rollNo" to rollNo, "completedOn" to completedOn, "note" to note,
    )

    val payable: Long get() = (fee - discount).coerceAtLeast(0)

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Enrollment(
            id = id, studentId = d.str("studentId"), batchId = d.str("batchId"), courseId = d.str("courseId"),
            date = d.str("date"), fee = d.long("fee"), discount = d.long("discount"),
            installments = (d["installments"] as? List<*>).orEmpty().mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                val m = item as? Map<String, Any?> ?: return@mapNotNull null
                Installment(m.str("due"), m.long("amount"))
            },
            status = d.enum("status", EnrollmentStatus.ACTIVE), rollNo = d.int("rollNo"),
            completedOn = d.str("completedOn"), note = d.str("note"),
        )
    }
}

data class Payment(
    override val id: String = Ids.new(),
    val enrollmentId: String = "",
    val studentId: String = "",
    val amount: Long = 0,
    val date: String = "",
    val method: PayMethod = PayMethod.CASH,
    val receiptNo: String = "",
    val note: String = "",
    val receivedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) : Rec {
    override fun fields() = mapOf(
        "enrollmentId" to enrollmentId, "studentId" to studentId, "amount" to amount, "date" to date,
        "method" to method.name, "receiptNo" to receiptNo, "note" to note, "receivedBy" to receivedBy,
        "createdAt" to createdAt,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Payment(
            id = id, enrollmentId = d.str("enrollmentId"), studentId = d.str("studentId"), amount = d.long("amount"),
            date = d.str("date"), method = d.enum("method", PayMethod.CASH), receiptNo = d.str("receiptNo"),
            note = d.str("note"), receivedBy = d.str("receivedBy"), createdAt = d.long("createdAt"),
        )
    }
}

// ---- Attendance, exams, certificates ----------------------------------------

data class Attendance(
    override val id: String,
    val batchId: String = "",
    val date: String = "",
    val marks: Map<String, Mark> = emptyMap(),
    val takenBy: String = "",
    val note: String = "",
) : Rec {
    override fun fields() = mapOf(
        "batchId" to batchId, "date" to date, "marks" to marks.mapValues { it.value.code },
        "takenBy" to takenBy, "note" to note,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Attendance(
            id = id, batchId = d.str("batchId"), date = d.str("date"),
            marks = (d["marks"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
                val mark = Mark.of(v) ?: return@mapNotNull null
                (k as? String ?: return@mapNotNull null) to mark
            }.toMap(),
            takenBy = d.str("takenBy"), note = d.str("note"),
        )
    }
}

data class Exam(
    override val id: String = Ids.new(),
    val batchId: String = "",
    val name: String = "",
    val date: String = "",
    val fullMarks: Double = 100.0,
    val passMarks: Double = 33.0,
    val marks: Map<String, Double> = emptyMap(),
) : Rec {
    override fun fields() = mapOf(
        "batchId" to batchId, "name" to name, "date" to date, "fullMarks" to fullMarks.asNumber(),
        "passMarks" to passMarks.asNumber(), "marks" to marks.mapValues { it.value.asNumber() },
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Exam(
            id = id, batchId = d.str("batchId"), name = d.str("name"), date = d.str("date"),
            fullMarks = d.double("fullMarks", 100.0), passMarks = d.double("passMarks", 33.0),
            marks = (d["marks"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
                val n = (v as? Number)?.toDouble() ?: return@mapNotNull null
                (k as? String ?: return@mapNotNull null) to n
            }.toMap(),
        )
    }
}

/** Whole numbers are written as integers, so the web reads 80, not 80.0. */
private fun Double.asNumber(): Number = if (this == Math.floor(this) && !isInfinite()) toLong() else this

data class Certificate(
    override val id: String = Ids.new(),
    val enrollmentId: String = "",
    val studentId: String = "",
    val certNo: String = "",
    val issueDate: String = "",
    val grade: String = "",
    val note: String = "",
) : Rec {
    override fun fields() = mapOf(
        "enrollmentId" to enrollmentId, "studentId" to studentId, "certNo" to certNo,
        "issueDate" to issueDate, "grade" to grade, "note" to note,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Certificate(
            id = id, enrollmentId = d.str("enrollmentId"), studentId = d.str("studentId"), certNo = d.str("certNo"),
            issueDate = d.str("issueDate"), grade = d.str("grade"), note = d.str("note"),
        )
    }
}

// ---- Enquiries, expenses, instructor pay ------------------------------------

data class Enquiry(
    override val id: String = Ids.new(),
    val name: String = "",
    val phone: String = "",
    val courseId: String? = null,
    val source: EnquirySource = EnquirySource.WALK_IN,
    val date: String = "",
    val followUp: String = "",
    val status: EnquiryStatus = EnquiryStatus.OPEN,
    val note: String = "",
    val studentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) : Rec {
    override fun fields() = mapOf(
        "name" to name, "phone" to phone, "courseId" to courseId, "source" to source.name, "date" to date,
        "followUp" to followUp, "status" to status.name, "note" to note, "studentId" to studentId,
        "createdAt" to createdAt,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Enquiry(
            id = id, name = d.str("name"), phone = d.str("phone"), courseId = d.strOrNull("courseId"),
            source = d.enum("source", EnquirySource.WALK_IN), date = d.str("date"), followUp = d.str("followUp"),
            status = d.enum("status", EnquiryStatus.OPEN), note = d.str("note"), studentId = d.strOrNull("studentId"),
            createdAt = d.long("createdAt"),
        )
    }
}

data class ExpenseHead(
    override val id: String = Ids.new(),
    val name: String = "",
    val emoji: String = "📦",
    val archived: Boolean = false,
) : Rec {
    override fun fields() = mapOf("name" to name, "emoji" to emoji, "archived" to archived)

    companion object {
        fun from(id: String, d: Map<String, Any?>) =
            ExpenseHead(id = id, name = d.str("name"), emoji = d.str("emoji", "📦"), archived = d.bool("archived"))

        /** The starter heads, with the fixed ids from docs/data-model.md. */
        val STARTER = listOf(
            ExpenseHead("head_rent", "Rent", "🏠"),
            ExpenseHead("head_bills", "Electricity & bills", "💡"),
            ExpenseHead("head_internet", "Internet", "🌐"),
            ExpenseHead("head_salary", "Staff salary", "👥"),
            ExpenseHead("head_marketing", "Marketing", "📣"),
            ExpenseHead("head_stationery", "Stationery & printing", "📝"),
            ExpenseHead("head_equipment", "Computers & equipment", "💻"),
            ExpenseHead("head_repairs", "Repairs & maintenance", "🔧"),
            ExpenseHead("head_other", "Other", "📦"),
        )
    }
}

data class Expense(
    override val id: String = Ids.new(),
    val headId: String = "",
    val amount: Long = 0,
    val date: String = "",
    val method: PayMethod = PayMethod.CASH,
    val note: String = "",
    val paidTo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) : Rec {
    override fun fields() = mapOf(
        "headId" to headId, "amount" to amount, "date" to date, "method" to method.name, "note" to note,
        "paidTo" to paidTo, "createdAt" to createdAt,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = Expense(
            id = id, headId = d.str("headId"), amount = d.long("amount"), date = d.str("date"),
            method = d.enum("method", PayMethod.CASH), note = d.str("note"), paidTo = d.str("paidTo"),
            createdAt = d.long("createdAt"),
        )
    }
}

data class InstructorPayment(
    override val id: String = Ids.new(),
    val instructorId: String = "",
    val amount: Long = 0,
    val date: String = "",
    val method: PayMethod = PayMethod.CASH,
    val batchId: String? = null,
    val month: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) : Rec {
    override fun fields() = mapOf(
        "instructorId" to instructorId, "amount" to amount, "date" to date, "method" to method.name,
        "batchId" to batchId, "month" to month, "note" to note, "createdAt" to createdAt,
    )

    companion object {
        fun from(id: String, d: Map<String, Any?>) = InstructorPayment(
            id = id, instructorId = d.str("instructorId"), amount = d.long("amount"), date = d.str("date"),
            method = d.enum("method", PayMethod.CASH), batchId = d.strOrNull("batchId"), month = d.str("month"),
            note = d.str("note"), createdAt = d.long("createdAt"),
        )
    }
}
