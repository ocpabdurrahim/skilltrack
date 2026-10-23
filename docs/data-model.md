# SkillTrack data model and sync rules

The Android app and the web app share one Cloud Firestore database and follow
the same rules. Change both apps (and `firebase/firestore.rules`) together.

## Centers, staff and roles

Everything belongs to a **center**. Whoever creates a center becomes its
**Owner**; only the app owner and people they approved may create one (see
"Who may create a center"). The owner (or an admin) adds staff by their
Google email address. A person can belong to several centers and picks one
after signing in.

| Role | Can change | Can see |
|---|---|---|
| `OWNER` | everything; there is exactly one, the creator | everything |
| `ADMIN` | everything except the owner's own record | everything |
| `OFFICE` | students, photos, admissions, enquiries, certificates, fee payments, expenses, expense heads, instructor payments | everything |
| `TEACHER` | attendance and exams (marks) | everything except money: `payments`, `expenses`, `expenseHeads`, `instructorPayments` |

Only `OWNER` and `ADMIN` change the center profile, staff, courses, batches and
instructors. Inactive (`active: false`) or deleted staff records give no access.
`firebase/firestore.rules` enforces all of this; the apps also hide what a role cannot do.

## Who may create a center

The **app owner** is `ocpabdurrahim@gmail.com`. The list is written in
`firebase/firestore.rules` (`isAppAdmin`) and in both apps
(`AppOwner.EMAILS` / `APP_OWNERS`); change all three together.

- The app owner may always create centers.
- Anyone else needs a document `creators/{their lower-case email}`, which only
  the app owner can write or delete. Deleting it stops further centers; centers
  already made are not affected.
- People ask through `centerRequests/{their email}`. They write it themselves,
  always with `status: 'PENDING'` (also to ask again after a decline). The app
  owner answers by setting `APPROVED` (in the same batch as the `creators`
  document) or `DECLINED`.

| Collection | Fields |
|---|---|
| `creators/{email}` | `email`, `name`, `approvedBy` (email), `approvedAt` (ms), `updatedAt` |
| `centerRequests/{email}` | `email`, `name`, `centerName`, `phone`, `message`, `status` (`PENDING` \| `APPROVED` \| `DECLINED`), `createdAt` (ms), `answeredBy` (email or `""`), `updatedAt` |

These two are read one document at a time (or as a whole list by the app owner)
and have no `deleted` field; the rules allow no other fields.

## Where the data lives

```
centers/{centerId}                       center profile and settings
centers/{centerId}/members/{email}       staff; the id is the lower-case Google email
centers/{centerId}/{collection}/{id}     the records below
```

Collections: `courses`, `instructors`, `batches`, `students`, `photos`,
`enrollments`, `payments`, `attendance`, `exams`, `certificates`, `enquiries`,
`expenseHeads`, `expenses`, `instructorPayments`.

A signed-in person finds their centers with a collection-group query on
`members` where `email == <their lower-case email>` (the parent of each match is
the center). Results that are `deleted` or not `active` are ignored.

## Shared conventions

- Every document in `members` and the data collections has `updatedAt`
  (**server** timestamp, set on every write with `serverTimestamp()`; the rules
  reject anything else) and `deleted` (boolean). A delete writes
  `{deleted: true, updatedAt: serverTimestamp()}` with merge. Documents are never
  removed from Firestore.
- New ids are random 20-character strings of `A-Z a-z 0-9`. Fixed ids are listed
  below; they make two devices that create "the same" record end up with one.
- **Money** is an integer number of minor units (paisa/cents): ৳1,250.50 is
  `125050`.
- **Dates** without a time (admission, payment, class day, birthday…) are strings
  `YYYY-MM-DD` in the center's local calendar; an unknown date is `""`.
  Moments (`createdAt`) are integer epoch milliseconds.
- Missing optional references are `null`. Unknown enum values fall back to the
  first value listed, except `role`, which falls back to `TEACHER` (least access).
- Writes are whole documents (`set`, not `update`), plus `deleted: false` and
  `updatedAt`. The last write to reach the server wins.

## Reading (both apps)

A client keeps the whole center in memory:

1. read every collection (except `photos`) from Firestore's local cache
   (instant, works offline);
2. note the newest `updatedAt` it saw, per collection;
3. listen to `where('updatedAt', '>=', newest)` for each collection, so only
   changes are downloaded (and billed);
4. apply each document: `deleted: true` removes it, anything else replaces it.

Firestore's local cache must be **unlimited** (`CACHE_SIZE_UNLIMITED`), with
no garbage collection: a document evicted from the cache would never be
downloaded again, because the listeners only ask for newer ones.

Local writes go into memory at once and reach Firestore in the background
(Firestore queues them while offline). A `TEACHER` does not listen to the four
money collections.

## The center document

`centers/{centerId}`:

| Field | Type | Notes |
|---|---|---|
| `name` | string | shown on every printout |
| `tagline` | string | e.g. "Computer Training Center" |
| `address`, `phone`, `email`, `website` | string | |
| `logo` | string | a `data:image/png;base64,…` or `data:image/jpeg;base64,…` URL, at most 100 KB, or `""` |
| `signatory` | string | title under the signature line on certificates, e.g. "Director" |
| `currencyCode` | string | default `BDT` |
| `currencySymbol` | string | default `৳` |
| `grouping` | `SOUTH_ASIAN` \| `INTERNATIONAL` | 12,34,567 or 1,234,567 |
| `studentPrefix` | string | default `ST` |
| `receiptPrefix` | string | default `MR` (money receipt) |
| `certificatePrefix` | string | default `CERT` |
| `ownerUid`, `ownerEmail` | string | set at creation, never changed |
| `createdAt` | number | ms |
| `updatedAt` | timestamp | server time |

The center document has no `deleted` field; centers are not deleted.

Creating a center is one batched write of the center document and the owner's
member record (`role: 'OWNER'`). The starter expense heads are written straight
after, by whichever client first sees an empty `expenseHeads` collection with
a role that can write it.

## Collections

### members — `centers/{c}/members/{email}`

| Field | Type | Notes |
|---|---|---|
| `email` | string | lower case; equals the document id |
| `name` | string | |
| `role` | `OWNER` \| `ADMIN` \| `OFFICE` \| `TEACHER` | |
| `active` | boolean | `false` = no access, record kept |
| `instructorId` | string \| null | a teacher's own instructor record, if any |
| `addedBy` | string | email of whoever added them |
| `createdAt` | number | ms |

### courses

`name`, `code` (short, upper case, e.g. `MSO`), `fee` (money), `duration`
(free text, e.g. "3 months"), `description`, `archived` (boolean).

### instructors

`name`, `phone`, `email`, `subject` (what they teach), `payType`
(`MONTHLY` \| `PER_BATCH` \| `PER_CLASS` \| `NONE`), `payRate` (money),
`joinDate` (date), `leaveDate` (date, `""` while still working), `note`,
`archived` (boolean).

### batches

| Field | Type | Notes |
|---|---|---|
| `name` | string | e.g. `MSO-26-B01` |
| `courseId` | string | |
| `instructorId` | string \| null | |
| `startDate`, `endDate` | date | `endDate` may be `""` |
| `days` | string[] | class days, any of `SAT SUN MON TUE WED THU FRI` |
| `time` | string | free text, e.g. "4:00 PM - 6:00 PM" |
| `room` | string | |
| `capacity` | number | 0 = no limit |
| `cancelled` | boolean | |
| `note` | string | |

Status is worked out, not stored: **Cancelled** if `cancelled`; **Upcoming**
before `startDate`; **Completed** after `endDate` (when set); otherwise
**Running**.

### students

`regNo` (e.g. `ST-2026-0001`), `name`, `fatherName`, `motherName`, `gender`
(`MALE` \| `FEMALE` \| `OTHER` \| `""`), `dob` (date), `phone`, `guardianPhone`,
`email`, `address`, `education` (e.g. "HSC"), `institution`, `idNo` (NID or
birth registration), `joinDate` (date), `note`, `archived` (boolean).

### photos — `photos/{studentId}`

`data`: a `data:image/jpeg;base64,…` URL of the student's photo, about
240 × 300 px and at most 60 KB. Kept apart from `students` so the student list
stays small. Photos are **not** loaded in bulk with the other collections: a
client fetches one when it needs to show or print it (cache first). Deleting a
photo writes the usual tombstone.

### enrollments (admissions)

| Field | Type | Notes |
|---|---|---|
| `studentId`, `batchId`, `courseId` | string | `courseId` is the batch's course at admission |
| `date` | date | admission date |
| `fee` | money | the course fee at admission |
| `discount` | money | |
| `installments` | `{due: date, amount: money}[]` | payment plan; empty = everything due on `date` |
| `status` | `ACTIVE` \| `COMPLETED` \| `DROPPED` | |
| `rollNo` | number | 1, 2, 3… within the batch |
| `completedOn` | date | `""` until completed |
| `note` | string | |

A student can be in several batches; one enrollment per student and batch.

### payments (fee receipts)

`enrollmentId`, `studentId`, `amount` (money, > 0), `date` (date), `method`
(`CASH` \| `BKASH` \| `NAGAD` \| `ROCKET` \| `BANK` \| `CARD` \| `OTHER`),
`receiptNo` (string), `note`, `receivedBy` (staff name), `createdAt` (ms).

### attendance — `attendance/{batchId}_{YYYYMMDD}`

One document per batch and class day (fixed id, so two devices marking the
same class end up with one record).

`batchId`, `date` (date), `marks` (map of enrollment id → `P` present,
`A` absent, `L` late, `E` excused), `takenBy` (staff name), `note`.

### exams

`batchId`, `name` (e.g. "Final - Practical"), `date` (date), `fullMarks`
(number), `passMarks` (number), `marks` (map of enrollment id → number; a
missing key means "not entered").

### certificates (issued register)

`enrollmentId`, `studentId`, `certNo` (string), `issueDate` (date), `grade`
(string, a copy of the result at the time, e.g. "A+"), `note`.

### enquiries (leads)

`name`, `phone`, `courseId` (string \| null), `source` (`WALK_IN` \| `PHONE` \|
`FACEBOOK` \| `REFERRAL` \| `OTHER`), `date` (date), `followUp` (date or `""`),
`status` (`OPEN` \| `CONVERTED` \| `LOST`), `note`, `studentId` (string \| null,
set when converted), `createdAt` (ms).

### expenseHeads

`name`, `emoji`, `archived` (boolean). Starter heads, with fixed ids:

| id | name | emoji |
|---|---|---|
| `head_rent` | Rent | 🏠 |
| `head_bills` | Electricity & bills | 💡 |
| `head_internet` | Internet | 🌐 |
| `head_salary` | Staff salary | 👥 |
| `head_marketing` | Marketing | 📣 |
| `head_stationery` | Stationery & printing | 📝 |
| `head_equipment` | Computers & equipment | 💻 |
| `head_repairs` | Repairs & maintenance | 🔧 |
| `head_other` | Other | 📦 |

### expenses

`headId`, `amount` (money), `date` (date), `method` (as payments), `note`,
`paidTo` (string), `createdAt` (ms).

### instructorPayments

`instructorId`, `amount` (money), `date` (date), `method` (as payments),
`batchId` (string \| null), `month` (`YYYY-MM` or `""`, the month a salary is
for), `note`, `createdAt` (ms).

## Numbers people see

Student, receipt and certificate numbers are `{prefix}-{year}-{n}` with `n`
zero-padded to 4 digits (`ST-2026-0001`, `MR-2026-0042`, `CERT-2026-0007`).
`year` is the year of the record's date; `n` is one more than the highest `n`
already used with that prefix and year. Two devices that are both offline can
pick the same number; the document id stays the real key, and a duplicate
number is shown as it is.

## Rules both apps compute the same way

### Fees and dues (per enrollment)

- `payable = max(fee - discount, 0)`
- `paid` = sum of its payments
- `due = payable - paid` (negative means paid in advance)
- The plan is `installments`, or one installment `{due: date, amount: payable}`
  when it is empty.
- `overdue = clamp(sum of installment amounts with due <= today - paid, 0, max(due, 0))`
- **Next due**: walk the installments in date order keeping a running total;
  the first one whose running total exceeds `paid` is next, and what is left of
  it is `running total - paid`.
- `DROPPED` enrollments are left out of dues lists and dues totals.
- A plan of N monthly installments starting on a date: each installment is
  `floor(total / N)` rounded down to a whole currency unit (a multiple of 100
  minor units), on the same day of each following month; the last one takes the
  remainder. People can edit the rows afterwards.

### Attendance

- Present count = `P` + `L`.
- `rate = (P + L) / (P + L + A)`; `E` (excused) is left out. No marks = no rate.
- Students to mark on a day: the batch's `ACTIVE` and `COMPLETED` enrollments
  whose admission `date` is on or before that day.
- A day that is not one of the batch's `days`, or is outside its dates, can
  still be marked (make-up classes); the apps say so.

### Results (per enrollment, over its batch's exams)

- An exam **counts** for a student once it has a mark for them, or once its
  `date` is before today (it has been held). Exams still ahead without a mark
  are left out, so results build up as exams happen.
- `obtained` = sum of the marks over counted exams (a missing mark is 0);
  `full` = sum of their `fullMarks`. No counted exams = no result yet.
- `percent = obtained / full × 100`.
- Failed if any counted exam has no mark or a mark below its `passMarks`.
- Grade from `percent` (Bangladesh scale, compared on the unrounded percent),
  F when failed:

| Percent | Grade | Point |
|---|---|---|
| 80 – 100 | A+ | 5.00 |
| 70 – 79 | A | 4.00 |
| 60 – 69 | A- | 3.50 |
| 50 – 59 | B | 3.00 |
| 40 – 49 | C | 2.00 |
| 33 – 39 | D | 1.00 |
| below 33 | F | 0.00 |

- Position: rank by `percent` within the batch among enrollments that are not
  `DROPPED`, have a result and did not fail; ties share a position (1, 1, 3).

### Instructor pay (what is owed)

- `MONTHLY`: `payRate` × the months from the month of `joinDate` to `last`,
  both included, where `last` is last month, or the month of `leaveDate` if that
  is earlier. No `joinDate` = nothing earned.
- `PER_BATCH`: `payRate` × the instructor's batches that are not cancelled and
  have started.
- `PER_CLASS`: `payRate` × attendance records in the instructor's batches.
- `NONE`: nothing is worked out; payments are only recorded.
- `owed = earned - sum of their instructorPayments`.

### Profit (per month)

`income` = payments dated in the month; `spending` = expenses + instructor
payments dated in the month; `profit = income - spending`.
