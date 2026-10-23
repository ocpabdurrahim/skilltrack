package io.github.ocpabdurrahim.skilltrack.domain

import io.github.ocpabdurrahim.skilltrack.data.Weekday
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dates without a time are stored as "YYYY-MM-DD" strings (docs/data-model.md),
 * which also sort correctly as text. This turns them into labels and back.
 */
object Dates {
    private val fullFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val dayYearFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val momentFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)

    fun today(): LocalDate = LocalDate.now()
    fun todayIso(): String = today().toString()

    fun parse(iso: String): LocalDate? = if (iso.isBlank()) null else runCatching { LocalDate.parse(iso) }.getOrNull()

    fun parseMonth(ym: String): YearMonth? = if (ym.isBlank()) null else runCatching { YearMonth.parse(ym.take(7)) }.getOrNull()

    /** "23 Oct 2026"; an empty date reads as [blank]. */
    fun full(iso: String, blank: String = "—"): String = parse(iso)?.format(fullFormat) ?: blank
    fun full(date: LocalDate): String = date.format(fullFormat)
    fun short(iso: String): String = parse(iso)?.format(shortFormat) ?: "—"
    fun month(month: YearMonth): String = month.format(monthFormat)
    fun shortMonth(month: YearMonth): String = month.format(shortMonthFormat)
    fun monthYear(month: YearMonth): String = month.format(monthYearFormat)
    fun moment(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(momentFormat)

    /** "Today", "Yesterday", "Tomorrow", "Mon, 20 Oct", or with the year once it is not this year. */
    fun relative(iso: String, today: LocalDate = today()): String {
        val d = parse(iso) ?: return "—"
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            today.plusDays(1) -> "Tomorrow"
            else -> if (d.year == today.year) d.format(dayFormat) else d.format(dayYearFormat)
        }
    }

    fun weekday(date: LocalDate): Weekday = Weekday.ofIso(date.dayOfWeek.value)

    fun daysBetween(fromIso: String, to: LocalDate): Long? = parse(fromIso)?.let { ChronoUnit.DAYS.between(it, to) }

    /** Whole years of age on [on], or null without a birthday. */
    fun age(dobIso: String, on: LocalDate = today()): Int? = parse(dobIso)?.let { ChronoUnit.YEARS.between(it, on).toInt() }

    fun inMonth(iso: String, month: YearMonth): Boolean = iso.startsWith(month.toString())

    fun forFileName(date: LocalDate = today()): String = date.toString()
}
