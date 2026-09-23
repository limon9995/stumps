package com.mdlimonhossain.stumps.data.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a match scorecard to a single-or-multi-page PDF using Android's built-in PdfDocument —
 * no extra library needed for a page of text and simple tables.
 *
 * Think of this like using a pen to write text onto a piece of paper, except the "paper" is a
 * PDF page and the "pen" is `canvas.drawText(...)`. We keep track of a `y` position (how far
 * down the page we've written so far) and move it down after every line, starting a brand new
 * page whenever we'd run out of room on the current one.
 */
object PdfScorecardExporter {

    // A4 paper size in "points" (a printing unit — 72 points = 1 inch) at a standard 72dpi.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f // empty space kept around the edge of the page
    private const val LINE_HEIGHT = 18f // vertical gap between one line of text and the next

    /** Builds the PDF file and saves it to the app's cache folder, then hands back its location. */
    fun export(context: Context, matchTitle: String, innings: List<InningsSummary>): File {
        val document = PdfDocument()
        // A "Paint" object in Android graphics describes HOW to draw something — its size,
        // boldness, font. We set up three different styles here: for the title, section
        // headings, and normal body text (using a fixed-width MONOSPACE font so the little
        // scorecard columns line up neatly, like a table).
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val headingPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 11f; typeface = Typeface.MONOSPACE }

        // Start the first page and get its canvas (the actual drawing surface).
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create())
        var canvas = page.canvas
        var y = MARGIN // start writing near the top of the page

        canvas.drawText(matchTitle, MARGIN, y, titlePaint)
        y += LINE_HEIGHT * 2

        // A small local helper function: before writing the next block of lines, check if
        // there's enough room left on the current page. If not, finish this page and start
        // a fresh one, resetting y back to the top.
        fun newPageIfNeeded(linesNeeded: Int) {
            if (y + linesNeeded * LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        // Write one section per innings: a heading line with the score, then every batsman's
        // figures, then every bowler's figures.
        innings.forEach { summary ->
            val s = summary.state
            newPageIfNeeded(6 + s.batsmanFigures.size + s.bowlerFigures.size)

            canvas.drawText(
                "${summary.battingTeamName} — ${s.totalRuns}/${s.totalWickets} (${s.oversDisplay} ov)",
                MARGIN, y, headingPaint
            )
            y += LINE_HEIGHT * 1.5f

            canvas.drawText("ব্যাটিং", MARGIN, y, headingPaint)
            y += LINE_HEIGHT
            s.batsmanFigures.forEach { (name, fig) ->
                newPageIfNeeded(1)
                val status = when {
                    fig.isOut -> fig.dismissalType?.name ?: "out"
                    fig.isRetiredHurt -> "retired hurt"
                    else -> "not out"
                }
                // "%-20s %3d (%3d) %s" is a text-formatting template: %-20s means "the name,
                // padded with spaces to at least 20 characters wide", %3d means "a number,
                // right-aligned in 3 characters" — this is what keeps the columns lined up.
                canvas.drawText("%-20s %3d (%3d) %s".format(name.take(20), fig.runs, fig.ballsFaced, status), MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
            }

            y += LINE_HEIGHT / 2
            newPageIfNeeded(1 + s.bowlerFigures.size)
            canvas.drawText("বোলিং", MARGIN, y, headingPaint)
            y += LINE_HEIGHT
            s.bowlerFigures.forEach { (name, fig) ->
                newPageIfNeeded(1)
                canvas.drawText("%-20s %5s-%3d-%2d".format(name.take(20), fig.overs, fig.runsConceded, fig.wickets), MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
            }
            y += LINE_HEIGHT * 1.5f
        }

        document.finishPage(page)

        // Save the finished PDF into the app's private cache folder (a place Android gives
        // every app for temporary files) so we have a real file we can hand off to share.
        val dir = File(context.cacheDir, "scorecards").apply { mkdirs() }
        val file = File(dir, "scorecard_${System.currentTimeMillis()}.pdf")
        // `.use { }` automatically closes the file stream when we're done, even if something
        // goes wrong partway through — it's Kotlin's safe way of handling "open, write, close".
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
