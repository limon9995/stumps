package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.data.export.PdfScorecardExporter
import com.mdlimonhossain.stumps.data.export.ShareUtils
import com.mdlimonhossain.stumps.domain.commentary.CommentaryEngine
import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import com.mdlimonhossain.stumps.domain.repository.matchResultText
import com.mdlimonhossain.stumps.domain.scoring.BatsmanFigures
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.GradientHeroCard
import com.mdlimonhossain.stumps.ui.designsystem.ScreenFadeThrough
import com.mdlimonhossain.stumps.ui.match.charts.OverRunsChart
import com.mdlimonhossain.stumps.ui.match.charts.WagonWheelChart
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import java.util.Locale

/**
 * The full scorecard screen shown after a match ends (or when reopening an old match) — laid
 * out like a real cricket scoreboard:
 *  1. A green "hero" card at the top with both teams' final scores and who won.
 *  2. Share buttons (PDF / text).
 *  3. For each innings: a proper BATTING table (runs, balls, 4s, 6s, strike rate, how they got
 *     out), extras and total, then a BOWLING table (overs, maidens, runs, wickets, economy),
 *     plus the over-runs chart, wagon wheel and ball-by-ball commentary.
 *
 * `stageLabel` is an optional small tag like "সেমিফাইনাল" or "ফাইনাল" (tournament matches only).
 * `doneLabel` is the text on the bottom button — "back to home" for a quick match, "back to the
 * tournament" for a tournament match.
 */
@Composable
fun MatchSummaryScreen(
    innings: List<InningsSummary>,
    onDone: () -> Unit,
    stageLabel: String? = null,
    doneLabel: String = "হোমে ফিরে যাও"
) {
    // LocalContext.current gives us the current Android "Context" — needed for things like
    // building a PDF file or launching the share sheet, which both need to know which app/screen they belong to.
    val context = LocalContext.current
    val matchTitle = innings.joinToString(" vs ") { it.battingTeamName }.ifBlank { "Stumps ম্যাচ" }
    // Always show innings in batting order (1st innings on top), whatever order they came in.
    val ordered = innings.sortedBy { it.innings.inningsNumber }

    // The final scorecard is a "reveal" moment — the match just ended — so the WHOLE screen
    // fades + grows in the first time it appears, instead of just abruptly being there.
    ScreenFadeThrough {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        ResultHeroCard(ordered, stageLabel)
        Spacer(Modifier.height(12.dp))

        Row {
            Button(
                onClick = {
                    // Build the PDF fresh right when the button is tapped (not ahead of time),
                    // then immediately hand it to the share sheet.
                    val file = PdfScorecardExporter.export(context, matchTitle, innings)
                    ShareUtils.sharePdf(context, file, ShareUtils.matchSummaryText(matchTitle, innings))
                },
                modifier = Modifier.weight(1f)
            ) { Text("PDF শেয়ার") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { ShareUtils.shareText(context, ShareUtils.matchSummaryText(matchTitle, innings)) },
                modifier = Modifier.weight(1f)
            ) { Text("টেক্সট শেয়ার") }
        }
        Spacer(Modifier.height(8.dp))

        // One scorecard per innings (1 or 2 of them, depending on how far the match got).
        ordered.forEach { summary -> InningsScorecard(summary) }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(doneLabel)
        }
    }
    }
}

/**
 * The big green card at the top: an optional stage tag, both teams with their scores (the
 * WINNING team's line is bold with a trophy), and the result line underneath.
 */
@Composable
private fun ResultHeroCard(innings: List<InningsSummary>, stageLabel: String?) {
    // "Team X won by Y runs/wickets" (or "match tied") — null if the match never finished.
    val result = matchResultText(innings)
    // Who won? Whoever's innings has more runs — only once both innings exist.
    val winnerName = if (innings.size >= 2) {
        val (a, b) = innings[0] to innings[1]
        when {
            a.state.totalRuns > b.state.totalRuns -> a.battingTeamName
            b.state.totalRuns > a.state.totalRuns -> b.battingTeamName
            else -> null
        }
    } else null

    GradientHeroCard(gradientColors = listOf(PitchGreen, Color(0xFF0B3D24))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "ম্যাচ শেষ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            if (stageLabel != null) {
                Spacer(Modifier.width(8.dp))
                // A little rounded "pill" tag showing the tournament stage.
                Text(
                    text = stageLabel,
                    color = Color(0xFF2B1700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(0xFFF0BE6E))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        innings.forEach { summary ->
            val s = summary.state
            val isWinner = summary.battingTeamName == winnerName
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (isWinner) "🏆 " else "") + summary.battingTeamName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "${s.totalRuns}/${s.totalWickets}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(text = "(${s.oversDisplay})", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
        if (result != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))
            Text(text = result, color = Color(0xFFF0BE6E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * One innings, drawn like a real scoreboard: batting table, extras + total, bowling table,
 * then the charts and (tap to expand) commentary.
 */
@Composable
private fun InningsScorecard(summary: InningsSummary) {
    val s = summary.state
    // Each innings card remembers its OWN "is commentary expanded" state separately —
    // remember() here is scoped to this one card, not shared across all of them.
    var showCommentary by remember { mutableStateOf(false) }
    // Extras (wides, no-balls, byes...) = the team's total minus what the batters scored off the bat.
    val extras = (s.totalRuns - s.batsmanFigures.values.sumOf { it.runs }).coerceAtLeast(0)

    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // ---- Innings header: team name, score, run rate ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.battingTeamName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(text = "${s.totalRuns}/${s.totalWickets}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "${s.oversDisplay} ওভার · রান রেট ${String.format(Locale.US, "%.2f", s.runRate)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // ---- Batting table ----
        // The first column (the name) takes all the spare width (weight 1f); the number columns
        // are fixed narrow widths so they line up neatly underneath their headings.
        TableHeader(first = "ব্যাটার", columns = listOf("R", "B", "4s", "6s", "SR"))
        s.batsmanFigures.forEach { (name, fig) ->
            val strikeRate = if (fig.ballsFaced == 0) 0.0 else fig.runs * 100.0 / fig.ballsFaced
            TableRow(
                first = name,
                subtitle = dismissalText(fig),
                columns = listOf("${fig.runs}", "${fig.ballsFaced}", "${fig.fours}", "${fig.sixes}", String.format(Locale.US, "%.1f", strikeRate)),
                // A batter still in (not out) is shown in bold, like on TV scoreboards.
                highlight = !fig.isOut && !fig.isRetiredHurt
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(text = "অতিরিক্ত (Extras)", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(text = "$extras", fontSize = 13.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(text = "মোট", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = "${s.totalRuns}/${s.totalWickets} (${s.oversDisplay})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        // ---- Bowling table ----
        TableHeader(first = "বোলার", columns = listOf("O", "M", "R", "W", "Econ"))
        s.bowlerFigures.forEach { (name, fig) ->
            TableRow(
                first = name,
                subtitle = null,
                columns = listOf(fig.overs, "${fig.maidens}", "${fig.runsConceded}", "${fig.wickets}", String.format(Locale.US, "%.1f", fig.economy)),
                // Bowlers who took wickets get picked out in bold.
                highlight = fig.wickets > 0
            )
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

// Width of each small number column in the batting/bowling tables — kept in one place so the
// headings and the rows always line up exactly.
private val NumberColumnWidth = 40.dp

/** The shaded heading row of a table, e.g. "ব্যাটার  R  B  4s  6s  SR". */
@Composable
private fun TableHeader(first: String, columns: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text = first, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        columns.forEach {
            Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumnWidth))
        }
    }
}

/** One row of a batting or bowling table. `subtitle` is the small grey "how they got out" line under a batter's name. */
@Composable
private fun TableRow(first: String, subtitle: String?, columns: List<String>, highlight: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = first,
                fontSize = 14.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        columns.forEachIndexed { index, value ->
            Text(
                text = value,
                fontSize = 14.sp,
                // The FIRST number (runs for a batter, overs for a bowler) is the headline one — bold it.
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
                modifier = Modifier.width(NumberColumnWidth)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** Turns a batter's figures into a short plain line like "ক্যাচ আউট" or "নট আউট". */
private fun dismissalText(fig: BatsmanFigures): String = when {
    fig.isRetiredHurt -> "রিটায়ার্ড হার্ট"
    !fig.isOut -> "নট আউট"
    else -> when (fig.dismissalType) {
        DismissalType.BOWLED -> "বোল্ড"
        DismissalType.CAUGHT -> "ক্যাচ আউট"
        DismissalType.LBW -> "এলবিডব্লিউ"
        DismissalType.RUN_OUT -> "রান আউট"
        DismissalType.STUMPED -> "স্টাম্পড"
        DismissalType.HIT_WICKET -> "হিট উইকেট"
        else -> "আউট"
    }
}
