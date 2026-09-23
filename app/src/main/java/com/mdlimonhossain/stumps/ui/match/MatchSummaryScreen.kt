package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.data.export.PdfScorecardExporter
import com.mdlimonhossain.stumps.data.export.ShareUtils
import com.mdlimonhossain.stumps.domain.commentary.CommentaryEngine
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import com.mdlimonhossain.stumps.domain.repository.matchResultText
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.ScreenFadeThrough
import com.mdlimonhossain.stumps.ui.match.charts.OverRunsChart
import com.mdlimonhossain.stumps.ui.match.charts.WagonWheelChart

/**
 * The full scorecard screen shown after a match ends (or when reopening an old match from
 * history) — batting/bowling figures per innings, the over-runs chart, the wagon wheel, an
 * expandable ball-by-ball commentary feed, and PDF/text share buttons.
 */
@Composable
fun MatchSummaryScreen(
    innings: List<InningsSummary>,
    onDone: () -> Unit
) {
    // LocalContext.current gives us the current Android "Context" — needed for things like
    // building a PDF file or launching the share sheet, which both need to know which app/screen they belong to.
    val context = LocalContext.current
    val matchTitle = innings.joinToString(" vs ") { it.battingTeamName }.ifBlank { "Stumps ম্যাচ" }

    // The final scorecard is a "reveal" moment — the match just ended — so the WHOLE screen
    // fades + grows in the first time it appears, instead of just abruptly being there.
    ScreenFadeThrough {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "ম্যাচ শেষ", style = MaterialTheme.typography.headlineLarge)
        // "Team X won by Y runs/wickets" (or "match tied") — only shows once both innings are
        // actually done; a match that only got as far as innings 1 (e.g. someone exited early)
        // just shows nothing here since matchResultText returns null.
        matchResultText(innings)?.let { result ->
            Spacer(Modifier.height(4.dp))
            Text(text = result, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))

        Row {
            Button(
                onClick = {
                    // Build the PDF fresh right when the button is tapped (not ahead of time),
                    // then immediately hand it to the share sheet.
                    val file = PdfScorecardExporter.export(context, matchTitle, innings)
                    ShareUtils.sharePdf(context, file, ShareUtils.matchSummaryText(matchTitle, innings))
                }
            ) { Text("PDF শেয়ার করো") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { ShareUtils.shareText(context, ShareUtils.matchSummaryText(matchTitle, innings)) }) {
                Text("টেক্সট শেয়ার")
            }
        }
        Spacer(Modifier.height(16.dp))

        // One card per innings (1 or 2 of them, depending on how far the match got).
        innings.forEach { summary ->
            val s = summary.state
            // Each innings card remembers its OWN "is commentary expanded" state separately —
            // remember() here is scoped to this one card, not shared across all of them.
            var showCommentary by remember { mutableStateOf(false) }

            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(text = "${summary.battingTeamName} — ${s.totalRuns}/${s.totalWickets} (${s.oversDisplay})", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(text = "ব্যাটিং", style = MaterialTheme.typography.labelSmall)
                    s.batsmanFigures.forEach { (id, fig) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = id)
                            val status = when {
                                fig.isOut -> fig.dismissalType?.name ?: "out"
                                fig.isRetiredHurt -> "retired hurt"
                                else -> "not out"
                            }
                            Text(text = "${fig.runs} (${fig.ballsFaced}) $status")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(text = "বোলিং", style = MaterialTheme.typography.labelSmall)
                    s.bowlerFigures.forEach { (id, fig) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = id)
                            Text(text = "${fig.overs}-${fig.runsConceded}-${fig.wickets}")
                        }
                    }

                    if (s.runsPerOver.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(text = "ওভার-প্রতি রান", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        OverRunsChart(runsPerOver = s.runsPerOver)
                    }

                    if (s.shotEvents.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(text = "ওয়াগন হুইল", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        WagonWheelChart(shots = s.shotEvents, modifier = Modifier.fillMaxWidth())
                    }

                    // Commentary is hidden by default (it can be a long list) — only generated
                    // and shown once the user actually taps to expand it.
                    if (summary.ballLog.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { showCommentary = !showCommentary }) {
                            Text(if (showCommentary) "কমেন্ট্রি লুকাও" else "বল-বাই-বল কমেন্ট্রি দেখো")
                        }
                        if (showCommentary) {
                            // remember(summary.ballLog) means "only re-run CommentaryEngine.generate
                            // if summary.ballLog actually changes" — avoids redoing this work on
                            // every tiny redraw for no reason.
                            val lines = remember(summary.ballLog) { CommentaryEngine.generate(summary.ballLog) }
                            Column {
                                lines.forEach { line ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = line.overBallLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                                        Text(text = line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(text = line.scoreAfter, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("হোমে ফিরে যাও")
        }
    }
    }
}
