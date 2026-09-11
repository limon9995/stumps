package com.mdlimonhossain.stumps.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.domain.model.MatchFormat
import com.mdlimonhossain.stumps.domain.repository.CareerStats
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.match.matchFormatLabel
import com.mdlimonhossain.stumps.ui.tournament.Cell
import com.mdlimonhossain.stumps.ui.tournament.HeaderCell

/**
 * Shared building blocks for showing ONE player's cricket record — originally written just for
 * the signed-in user's own ProfileScreen, pulled out here so a saved team's Player Profile page
 * (see ui/team/PlayerProfileScreen.kt) can show the exact same Batting/Bowling/Fielding cards and
 * format-wise breakdown table, since both screens are ultimately displaying the same
 * StatsRepository.PlayerProfileStats shape — just for a different player name.
 */

/** Which slice of detailed numbers a Statistics tab is currently showing. */
enum class StatCategory { BAT, BOWL, FIELD, MATCH_WISE }

/** Formats a stat like batting average or strike rate to one decimal place, e.g. "34.5". */
fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

/** One of the three small Batting/Bowling/Fielding summary cards shown near the top of a player's overview. */
@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, headerColor: Color, rows: List<Pair<String, String>>) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().background(headerColor).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            rows.forEachIndexed { index, (label, value) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

/** One circle in a "Recent Form" strip — the runs scored in one recent innings, or "-" if this player didn't bat in that match. */
@Composable
fun RecentFormCircle(runs: Int?, isNewest: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isNewest && runs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = runs?.toString() ?: "-",
            color = if (isNewest && runs != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

/**
 * The full "Statistics" tab body: a BAT/BOWL/FIELD/MATCH-WISE sub-tab row, then either a
 * format-wise breakdown table or the match-by-match recent form list.
 */
@Composable
fun PlayerStatisticsBreakdown(statsByFormat: Map<MatchFormat, CareerStats>, recentForm: List<Int?>) {
    var category by remember { mutableStateOf(StatCategory.BAT) }
    // Only show a column for a format this player has actually played at least one match in —
    // an all-zero column for a format they've never touched would just be visual noise.
    val formats = MatchFormat.entries.filter { statsByFormat.containsKey(it) }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedTabChip(label = "BAT", selected = category == StatCategory.BAT, modifier = Modifier.weight(1f)) { category = StatCategory.BAT }
        AnimatedTabChip(label = "BOWL", selected = category == StatCategory.BOWL, modifier = Modifier.weight(1f)) { category = StatCategory.BOWL }
        AnimatedTabChip(label = "FIELD", selected = category == StatCategory.FIELD, modifier = Modifier.weight(1f)) { category = StatCategory.FIELD }
        AnimatedTabChip(label = "MATCH-WISE", selected = category == StatCategory.MATCH_WISE, modifier = Modifier.weight(1.4f)) { category = StatCategory.MATCH_WISE }
    }
    Spacer(Modifier.height(20.dp))

    if (formats.isEmpty() && category != StatCategory.MATCH_WISE) {
        Text(
            text = "এখনো কোনো ম্যাচে ফরম্যাট (T10/T20/ODI) বেছে নেওয়া হয়নি — নতুন ম্যাচ শুরু করার সময় এখন এই তথ্য জিজ্ঞেস করা হয়",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        return
    }

    when (category) {
        StatCategory.BAT -> FormatStatsTable(
            formats, statsByFormat,
            listOf(
                "Matches" to { s: CareerStats -> s.matchesPlayed.toString() },
                "Innings" to { s -> s.inningsBatted.toString() },
                "Runs" to { s -> s.runs.toString() },
                "Balls" to { s -> s.ballsFaced.toString() },
                "Highest" to { s -> s.highScore.toString() },
                "Average" to { s -> oneDecimal(s.battingAverage) },
                "SR" to { s -> oneDecimal(s.strikeRate) },
                "Not Out" to { s -> s.notOuts.toString() },
                "Ducks" to { s -> s.ducks.toString() },
                "100s" to { s -> s.hundreds.toString() },
                "50s" to { s -> s.fifties.toString() },
                "30s" to { s -> s.thirties.toString() },
                "6s" to { s -> s.sixes.toString() },
                "4s" to { s -> s.fours.toString() }
            )
        )
        StatCategory.BOWL -> FormatStatsTable(
            formats, statsByFormat,
            listOf(
                "Matches" to { s: CareerStats -> s.matchesPlayed.toString() },
                "Innings" to { s -> s.inningsBowled.toString() },
                "Wickets" to { s -> s.wickets.toString() },
                "Balls" to { s -> s.ballsBowled.toString() },
                "Runs" to { s -> s.runsConceded.toString() },
                "Best" to { s -> s.bestBowlingFigures },
                "Average" to { s -> if (s.wickets == 0) "-" else oneDecimal(s.bowlingAverage) },
                "Economy" to { s -> oneDecimal(s.economy) }
            )
        )
        StatCategory.FIELD -> FormatStatsTable(
            formats, statsByFormat,
            listOf(
                "Catches" to { s: CareerStats -> s.catches.toString() },
                "Stumpings" to { s -> s.stumpings.toString() },
                "Runouts" to { s -> s.runOuts.toString() }
            )
        )
        StatCategory.MATCH_WISE -> MatchWiseList(recentForm)
    }
}

/**
 * A table with one row per stat and one COLUMN per match format the player has actually played
 * (T10/T20/ODI/Club/Custom) — the reference app's signature format-wise breakdown. Wrapped in
 * horizontalScroll since with 4-5 format columns plus the label column, it's wider than a phone
 * screen — same pattern as the Tournament Points table.
 */
@Composable
private fun FormatStatsTable(
    formats: List<MatchFormat>,
    statsByFormat: Map<MatchFormat, CareerStats>,
    rows: List<Pair<String, (CareerStats) -> String>>
) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            HeaderCell("", 90)
            formats.forEach { f -> HeaderCell(matchFormatLabel(f), 70) }
        }
        rows.forEach { (label, valueFor) ->
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Cell(label, 90)
                formats.forEach { f ->
                    Cell(valueFor(statsByFormat.getOrDefault(f, CareerStats())), 70)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun MatchWiseList(recentForm: List<Int?>) {
    if (recentForm.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Text(text = "কোনো ম্যাচ-ভিত্তিক পরিসংখ্যান নেই", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "এখনো কোনো ম্যাচ খেলা হয়নি।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Newest innings first — recentForm is already ordered that way (see StatsRepository).
        recentForm.forEachIndexed { index, runs ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(text = "ইনিংস ${recentForm.size - index}", modifier = Modifier.weight(1f))
                Text(text = runs?.let { "$it রান" } ?: "ব্যাট করেনি", fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
    }
}
