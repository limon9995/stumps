package com.mdlimonhossain.stumps.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helpers for the "share" button — this opens Android's normal system share sheet (the popup
 * that lists WhatsApp, Facebook, Gmail, etc.), the exact same one every other app uses. We
 * don't build our own custom sharing UI or connect to WhatsApp/Facebook directly — Android
 * handles showing the right apps for us, we just need to describe WHAT we're sharing.
 */
object ShareUtils {
    /** Opens the share sheet with a PDF file attached (plus a short text caption alongside it). */
    fun sharePdf(context: Context, file: File, textSummary: String) {
        // Apps aren't normally allowed to hand each other raw file paths directly (Android's
        // security rules block that) — FileProvider creates a special safe "content://" link
        // that other apps ARE allowed to open, pointing at our file.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, textSummary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // lets the OTHER app actually read our file through the link above
        }
        // createChooser always shows the "choose an app" popup, instead of Android silently
        // picking one app on its own.
        context.startActivity(Intent.createChooser(intent, "স্কোরকার্ড শেয়ার করো"))
    }

    /** Opens the share sheet with just plain text — no file attached (quicker/simpler than the PDF option). */
    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "শেয়ার করো"))
    }

    /** Builds a short, readable text summary of a match — used as the caption when sharing. */
    fun matchSummaryText(matchTitle: String, innings: List<com.mdlimonhossain.stumps.domain.repository.InningsSummary>): String =
        // buildString lets us construct a multi-line piece of text step by step, line by line,
        // instead of gluing strings together with lots of "+" signs.
        buildString {
            appendLine(matchTitle)
            innings.forEach { s ->
                appendLine("${s.battingTeamName}: ${s.state.totalRuns}/${s.state.totalWickets} (${s.state.oversDisplay} ov)")
            }
            appendLine()
            appendLine("Stumps অ্যাপ দিয়ে স্কোর করা হয়েছে")
        }
}
