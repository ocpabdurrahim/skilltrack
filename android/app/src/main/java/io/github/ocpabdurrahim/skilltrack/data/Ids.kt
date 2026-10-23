package io.github.ocpabdurrahim.skilltrack.data

import java.security.SecureRandom

/** Document ids and the numbers people see (docs/data-model.md, "Ids" and "Numbers people see"). */
object Ids {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    /** A random 20-character id, like Firestore's own. */
    fun new(): String = buildString(20) { repeat(20) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** One attendance record per batch and class day: `{batchId}_{YYYYMMDD}`. */
    fun attendance(batchId: String, date: String): String = "${batchId}_${date.replace("-", "")}"

    /**
     * The next `{prefix}-{year}-{nnnn}` after the highest one already used with
     * that prefix and year. [used] are the existing numbers (any format).
     */
    fun nextNumber(prefix: String, year: Int, used: Iterable<String>): String {
        val start = "$prefix-$year-"
        val highest = used.asSequence()
            .filter { it.startsWith(start) }
            .mapNotNull { it.removePrefix(start).toIntOrNull() }
            .maxOrNull() ?: 0
        return start + (highest + 1).toString().padStart(4, '0')
    }
}
