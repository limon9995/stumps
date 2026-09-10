package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.remote.sync.PublicInningsWithBalls
import com.mdlimonhossain.stumps.domain.commentary.CommentaryEngine
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState

private enum class CloudMatchTab { SCORECARD, BALLS }

/**
 * A read-only scorecard for ONE match that belongs to a tournament this device does NOT
 * organize — reached from TournamentPreviewScreen's Matches tab. Unlike MatchCentreScreen (which
 * reads from local Room and has 5 tabs including a live-editable Summary), this only ever reads
 * a one-shot snapshot from the cloud mirror (see LiveBroadcastRepository.getAllInningsOnce) and
 * only offers Scorecard + Balls — there's no Wagon Wheel/Info tab here yet, to keep this first
 * version of cross-device match viewing reasonably scoped.
 */
@Composable
fun CloudMatchScorecardScreen(matchId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var innings by remember { mutableStateOf<List<PublicInningsWithBalls>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(CloudMatchTab.SCORECARD) }
    var inningsIndex by remember { mutableStateOf(0) }

    LaunchedEffect(matchId) {
        innings = app.liveBroadcastRepository.getAllInningsOnce(matchId)
        inningsIndex = (innings.size - 1).coerceAtLeast(0)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.width(4.dp))
            Text(text = "Match Scorecard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            innings.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এখনো কোনো বল হয়নি")
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimatedTabChip(label = "Scorecard", selected = tab == CloudMatchTab.SCORECARD, modifier = Modifier.weight(1f)) { tab = CloudMatchTab.SCORECARD }
                        AnimatedTabChip(label = "Balls", selected = tab == CloudMatchTab.BALLS, modifier = Modifier.weight(1f)) { tab = CloudMatchTab.BALLS }
                    }
                    if (innings.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            innings.forEachIndexed { index, summary ->
                                AnimatedTabChip(label = summary.battingTeamName, selected = inningsIndex == index, modifier = Modifier.weight(1f)) { inningsIndex = index }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    val selected = innings.getOrNull(inningsIndex) ?: innings.last()
                    when (tab) {
                        CloudMatchTab.SCORECARD -> CloudScorecardTab(selected)
                        CloudMatchTab.BALLS -> CloudBallsTab(selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudScorecardTab(summary: PublicInningsWithBalls) {
    val s = summary.state
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = summary.battingTeamName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = "${s.totalRuns}-${s.totalWickets} (${s.oversDisplay})", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Text(text = "ব্যাটিং", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        s.batsmanFigures.forEach { (name, fig) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = name, modifier = Modifier.weight(1f))
                Text(text = "${fig.runs} (${fig.ballsFaced}) ${if (fig.isOut) fig.dismissalType?.name ?: "out" else "not out"}", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(text = "বোলিং", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        s.bowlerFigures.forEach { (name, fig) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = name, modifier = Modifier.weight(1f))
                Text(text = "${fig.overs}-${fig.runsConceded}-${fig.wickets}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CloudBallsTab(summary: PublicInningsWithBalls) {
    if (summary.ballLog.isEmpty()) {
        EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এখনো কোনো বল হয়নি")
        return
    }
    val lines = remember(summary.ballLog) { CommentaryEngine.generate(summary.ballLog) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Text(text = line.overBallLabel, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(text = line.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(text = line.scoreAfter, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}
