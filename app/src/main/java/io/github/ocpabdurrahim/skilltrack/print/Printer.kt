package io.github.ocpabdurrahim.skilltrack.print

import android.app.Activity
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Prints an HTML page through Android's print dialog, which also offers
 * "Save as PDF" (to share a receipt on WhatsApp, for example).
 */
object Printer {
    // The WebView must outlive the call until the print job has its pages.
    private var current: WebView? = null

    fun print(activity: Activity, jobName: String, html: String, landscape: Boolean = false) {
        val webView = WebView(activity)
        current = webView
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = activity.getSystemService(PrintManager::class.java) ?: return
                val size = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4.asPortrait()
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(size)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                manager.print(jobName, view.createPrintDocumentAdapter(jobName), attributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
