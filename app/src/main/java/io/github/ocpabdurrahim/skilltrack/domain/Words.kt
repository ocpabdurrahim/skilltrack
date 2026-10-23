package io.github.ocpabdurrahim.skilltrack.domain

/**
 * Amounts in words for money receipts, in the South Asian way:
 * 125050 (minor units) -> "One Thousand Two Hundred Fifty Taka and Fifty Paisa Only".
 */
object Words {
    private val ones = listOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
    )
    private val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    fun amount(minor: Long, major: String = "Taka", minorName: String = "Paisa"): String {
        val whole = kotlin.math.abs(minor) / 100
        val fraction = kotlin.math.abs(minor) % 100
        val words = buildString {
            append(if (whole == 0L) "Zero" else number(whole))
            append(" ").append(major)
            if (fraction > 0) append(" and ").append(number(fraction)).append(" ").append(minorName)
            append(" Only")
        }
        return if (minor < 0) "Minus $words" else words
    }

    /** 1,23,45,678 -> "One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight". */
    fun number(n: Long): String {
        if (n == 0L) return "Zero"
        val parts = mutableListOf<String>()
        var rest = n
        val crore = rest / 10_000_000
        rest %= 10_000_000
        if (crore > 0) parts += number(crore) + " Crore"
        val lakh = rest / 100_000
        rest %= 100_000
        if (lakh > 0) parts += belowHundred(lakh.toInt()) + " Lakh"
        val thousand = rest / 1000
        rest %= 1000
        if (thousand > 0) parts += belowHundred(thousand.toInt()) + " Thousand"
        val hundred = rest / 100
        rest %= 100
        if (hundred > 0) parts += ones[hundred.toInt()] + " Hundred"
        if (rest > 0) parts += belowHundred(rest.toInt())
        return parts.joinToString(" ")
    }

    private fun belowHundred(n: Int): String = when {
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + " " + ones[n % 10]
    }

    /** "Taka"/"Paisa" for BDT; otherwise the currency code with "cents". */
    fun names(currencyCode: String): Pair<String, String> = when (currencyCode) {
        "BDT" -> "Taka" to "Paisa"
        "INR", "PKR", "NPR", "LKR" -> "Rupees" to "Paisa"
        else -> currencyCode to "Cents"
    }
}
