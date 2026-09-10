package com.mdlimonhossain.stumps.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.export.ShareUtils
import com.mdlimonhossain.stumps.data.local.db.UserEntity
import com.mdlimonhossain.stumps.domain.repository.CareerStats
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.theme.PitchGreen

/** Which of the two top-level tabs is showing: the summary view, or the detailed numbers. */
private enum class ProfileTab { OVERVIEW, STATISTICS }

/** Which slice of detailed numbers the Statistics tab is currently showing. */
private enum class StatCategory { BAT, BOWL, FIELD, MATCH_WISE }

/**
 * The signed-in user's own Player Profile screen: an "Overview" tab with headline stat cards
 * and a recent-form strip, and a "Statistics" tab with a full breakdown table. This screen
 * builds its own ProfileViewModel (same pattern as HomeScreen.kt) to load career stats, since
 * StumpsApp.kt only hands it the raw UserEntity.
 */
@Composable
fun ProfileScreen(
    profile: UserEntity?,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: ProfileViewModel = viewModel(
        key = profile.uid,
        factory = ProfileViewModel.Factory(profile.uid, profile.name, app.statsRepository)
    )
    val uiState by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(ProfileTab.OVERVIEW) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        }

        // The two-tab pill switcher at the top — AnimatedTabChip fades colours smoothly on tap.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedTabChip(label = "Player Overview", selected = tab == ProfileTab.OVERVIEW, modifier = Modifier.weight(1f)) { tab = ProfileTab.OVERVIEW }
            AnimatedTabChip(label = "Statistics", selected = tab == ProfileTab.STATISTICS, modifier = Modifier.weight(1f)) { tab = ProfileTab.STATISTICS }
        }
        Spacer(Modifier.height(20.dp))

        when (tab) {
            ProfileTab.OVERVIEW -> OverviewTab(profile, uiState.stats.careerStats, uiState.stats.recentForm, onSignOut)
            ProfileTab.STATISTICS -> StatisticsTab(uiState.stats.statsByFormat, uiState.stats.recentForm)
        }
    }
}

@Composable
private fun OverviewTab(profile: UserEntity, stats: CareerStats, recentForm: List<Int?>, onSignOut: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // No photo-upload feature exists yet, so a big initials circle stands in for a profile photo.
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(PitchGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(text = profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // A short, readable id built from the user's own account id — just for display, not
            // a separate database field.
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = "Profile ID : ${profile.uid.take(8)}",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        ShareUtils.shareText(context, "${profile.name}-এর Stumps প্রোফাইল দেখো — ${stats.runs} রান, ${stats.wickets} উইকেট!")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↗", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Batting",
                headerColor = Color(0xFFDB8A93),
                rows = listOf(
                    "Runs" to stats.runs.toString(),
                    "Average" to oneDecimal(stats.battingAverage),
                    "High Score" to stats.highScore.toString()
                )
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Bowling",
                headerColor = Color(0xFF7C9E82),
                rows = listOf(
                    "Wickets" to stats.wickets.toString(),
                    "Average" to (if (stats.wickets == 0) "-" else oneDecimal(stats.bowlingAverage)),
                    "Best Bowling" to stats.bestBowlingFigures
                )
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Fielding",
                headerColor = Color(0xFF6F8FA6),
                rows = listOf(
                    "Catches" to stats.catches.toString(),
                    "Stumpings" to stats.stumpings.toString(),
                    "Runouts" to stats.runOuts.toString()
                )
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Recent Form", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Always draw exactly 5 circles — pad with "-" placeholders if this player has fewer
            // than 5 recorded innings, so the row never looks broken/uneven.
            val slots = (0 until 5).map { recentForm.getOrNull(it) }
            slots.forEachIndexed { index, runs ->
                RecentFormCircle(runs = runs, isNewest = index == 0)
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, headerColor: Color, rows: List<Pair<String, String>>) {
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

@Composable
private fun RecentFormCircle(runs: Int?, isNewest: Boolean) {
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

@Composable
private fun StatisticsTab(statsByFormat: Map<com.mdlimonhossain.stumps.domain.model.MatchFormat, CareerStats>, recentForm: List<Int?>) {
    var category by remember { mutableStateOf(StatCategory.BAT) }
    // Only show a column for a format this player has actually played at least one match in —
    // an all-zero column for a format they've never touched would just be visual noise.
    val formats = com.mdlimonhossain.stumps.domain.model.MatchFormat.entries.filter { statsByFormat.containsKey(it) }

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
    formats: List<com.mdlimonhossain.stumps.domain.model.MatchFormat>,
    statsByFormat: Map<com.mdlimonhossain.stumps.domain.model.MatchFormat, CareerStats>,
    rows: List<Pair<String, (CareerStats) -> String>>
) {
    Column(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            com.mdlimonhossain.stumps.ui.tournament.HeaderCell("", 90)
            formats.forEach { f -> com.mdlimonhossain.stumps.ui.tournament.HeaderCell(com.mdlimonhossain.stumps.ui.match.matchFormatLabel(f), 70) }
        }
        rows.forEach { (label, valueFor) ->
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                com.mdlimonhossain.stumps.ui.tournament.Cell(label, 90)
                formats.forEach { f ->
                    com.mdlimonhossain.stumps.ui.tournament.Cell(valueFor(statsByFormat.getOrDefault(f, CareerStats())), 70)
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
                text = "তুমি এখনো Stumps অ্যাপে কোনো ম্যাচ খেলোনি।",
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

/** Formats a stat like batting average or strike rate to one decimal place, e.g. "34.5". */
private fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
