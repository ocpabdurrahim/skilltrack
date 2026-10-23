package io.github.ocpabdurrahim.skilltrack.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Calls, SMS, WhatsApp and file sharing, for reminders and exports. */
object Share {
    /** "01712-345678" -> "8801712345678"; numbers that already have a country code are kept. */
    fun internationalBd(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        return when {
            digits.startsWith("880") -> digits
            digits.startsWith("0") && digits.length == 11 -> "88$digits"
            digits.length == 10 && digits.startsWith("1") -> "880$digits"
            else -> digits
        }
    }

    fun whatsApp(context: Context, phone: String, text: String) {
        val uri = Uri.parse("https://wa.me/${internationalBd(phone)}?text=${Uri.encode(text)}")
        open(context, Intent(Intent.ACTION_VIEW, uri))
    }

    fun sms(context: Context, phone: String, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}"))
            .putExtra("sms_body", text)
        open(context, intent)
    }

    fun call(context: Context, phone: String) {
        open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")))
    }

    fun text(context: Context, text: String, title: String = "Share") {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        open(context, Intent.createChooser(send, title))
    }

    /** Writes [content] to a file in the cache and offers it to other apps (Excel, Drive, Gmail…). */
    fun file(context: Context, name: String, mime: String, content: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        // A byte-order mark lets Excel read Bangla names and ৳ correctly.
        file.writeText("﻿" + content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        open(context, Intent.createChooser(send, name))
    }

    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can do that.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Comma-separated values with proper quoting. */
object Csv {
    fun build(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(",") { cell(it) })
        rows.forEach { row -> appendLine(row.joinToString(",") { cell(it) }) }
    }

    private fun cell(value: Any?): String {
        val s = value?.toString().orEmpty()
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
