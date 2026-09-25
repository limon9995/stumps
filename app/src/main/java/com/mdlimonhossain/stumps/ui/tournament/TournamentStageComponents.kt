package com.mdlimonhossain.stumps.ui.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.data.local.db.tournament.FixtureStage
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.domain.repository.FixtureScore
import com.mdlimonhossain.stumps.domain.repository.TournamentPhase
import com.mdlimonhossain.stumps.domain.repository.TournamentProgress
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.GradientHeroCard
import com.mdlimonhossain.stumps.ui.theme.BallRed
import com.mdlimonhossain.stumps.ui.theme.PitchGreen

/**
 * The small building blocks that show a tournament's JOURNEY on screen:
 *   League  ->  Semi-final  ->  Final  ->  Champion 🏆
 * Kept in their own file so TournamentDetailScreen.kt doesn't grow even bigger.
 */

// The gold "trophy" colours used for anything champion-related.
private val Gold = Color(0xFFF0BE6E)
private val DarkGold = Color(0xFF8C5F00)

/**
 * A row of 4 numbered dots joined by lines — like a progress tracker on a delivery app —
 * showing which stage the tournament has reached. Finished stages are filled green with a
 * tick, the current stage is outlined, and future stages are grey.
 */
@Composable
fun TournamentJourney(progress: TournamentProgress, hasSemiFinals: Boolean) {
    // With 2-3 teams there are no semi-finals, so the tracker skips that step.
    val steps = buildList {
        add(TournamentPhase.LEAGUE to "লিগ")
        if (hasSemiFinals) add(TournamentPhase.SEMI_FINALS to "সেমি")
        add(TournamentPhase.FINAL to "ফাইনাল")
        add(TournamentPhase.FINISHED to "চ্যাম্পিয়ন")
    }
    // SETUP (not enough teams yet) counts as "still at the league step".
    val currentPhase = if (progress.phase == TournamentPhase.SETUP) TournamentPhase.LEAGUE else progress.phase
    val currentIndex = steps.indexOfFirst { it.first == currentPhase }.coerceAtLeast(0)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, (_, label) ->
            // "done" = a stage we've already moved past. The last step (Champion) counts as
            // done once the tournament is FINISHED.
            val done = index < currentIndex || (progress.phase == TournamentPhase.FINISHED && index == currentIndex)
            val current = index == currentIndex && !done
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> PitchGreen
                                current -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(2.dp, if (current) PitchGreen else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            done && index == steps.lastIndex -> "🏆"
                            done -> "✓"
                            else -> "${index + 1}"
                        },
                        color = if (done) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (done || current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The "what happens next" card for the current stage: how many of this stage's matches are
 * done (with a progress bar), and — once they're ALL done — a big button to create the next
 * stage ("সেমিফাইনাল শুরু করো" / "ফাইনাল শুরু করো"). Shows nothing before there are 2 teams,
 * or once the tournament is finished (the champion card takes over then).
 */
@Composable
fun StageActionCard(progress: TournamentProgress, onAdvance: () -> Unit) {
    if (progress.phase == TournamentPhase.SETUP || progress.phase == TournamentPhase.FINISHED) return
    val stageName = when (progress.phase) {
        TournamentPhase.SEMI_FINALS -> "সেমিফাইনাল"
        TournamentPhase.FINAL -> "ফাইনাল"
        else -> "লিগ পর্ব"
    }
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = stageName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${progress.stagePlayed} / ${progress.stageTotal} ম্যাচ শেষ",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        // A bar that fills up as matches finish. Guard against dividing by zero with no matches yet.
        LinearProgressIndicator(
            progress = { if (progress.stageTotal == 0) 0f else progress.stagePlayed.toFloat() / progress.stageTotal },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = PitchGreen
        )
        Spacer(Modifier.height(10.dp))
        if (progress.canAdvance && progress.nextStageLabel != null) {
            val intro = if (progress.phase == TournamentPhase.LEAGUE) {
                "লিগ শেষ! পয়েন্ট টেবিলের উপরের ${progress.qualifyCount}টা দল পরের ধাপে যাবে।"
            } else {
                "সেমিফাইনাল শেষ! দুই বিজয়ী দল এবার ফাইনালে মুখোমুখি।"
            }
            Text(text = intro, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                Text("${progress.nextStageLabel} শুরু করো")
            }
        } else {
            val hint = when (progress.phase) {
                TournamentPhase.LEAGUE -> "সব লিগ ম্যাচ শেষ হলে এখানে ${progress.nextStageLabel ?: "পরের ধাপ"} শুরু করার বাটন আসবে।"
                TournamentPhase.SEMI_FINALS -> "দুটো সেমিফাইনাল শেষ হলে এখানে ফাইনাল শুরু করার বাটন আসবে।"
                else -> "ফাইনাল জিতলেই চ্যাম্পিয়ন ঠিক হয়ে যাবে!"
            }
            Text(text = hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The big gold "🏆 Champion" banner, shown once the final has been played. */
@Composable
fun ChampionCard(championName: String, runnerUpName: String?) {
    GradientHeroCard(gradientColors = listOf(DarkGold, Color(0xFF3D2800))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = "🏆", fontSize = 44.sp)
            Text(text = "চ্যাম্পিয়ন", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = championName,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (runnerUpName != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = "রানার-আপ: $runnerUpName", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = "টুর্নামেন্ট শেষ 🎉", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

/**
 * One match card in the Matches tab, drawn as a mini scoreboard:
 *   [Semi-final 1]                          [LIVE / Result]
 *   Dhaka Tigers                 120/5 (20.0)
 *   Chittagong Kings             121/3 (18.2)
 *   Chittagong Kings 7 উইকেটে জয়ী
 * The winning team's line is bold; the losing team's line is faded.
 */
@Composable
fun FixtureScoreCard(
    fixture: TournamentFixtureEntity,
    title: String, // e.g. "ম্যাচ ৩" or "সেমিফাইনাল ১"
    teamAName: String,
    teamBName: String,
    score: FixtureScore?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val s = score ?: FixtureScore()
    val isKnockout = fixture.stage != FixtureStage.LEAGUE
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                // Knockout matches get a gold label so they stand out from the league games.
                color = if (isKnockout) DarkGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            // A small coloured status pill on the right.
            val (pillText, pillColor) = when {
                s.isComplete -> "শেষ" to PitchGreen
                s.isStarted -> "LIVE" to BallRed
                else -> "আসন্ন" to MaterialTheme.colorScheme.outline
            }
            Text(
                text = pillText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(pillColor).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        TeamScoreLine(name = teamAName, score = s.teamAScore, isWinner = s.winnerTeamId == fixture.teamAId, isLoser = s.isComplete && s.winnerTeamId == fixture.teamBId)
        Spacer(Modifier.height(4.dp))
        TeamScoreLine(name = teamBName, score = s.teamBScore, isWinner = s.winnerTeamId == fixture.teamBId, isLoser = s.isComplete && s.winnerTeamId == fixture.teamAId)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                s.isComplete -> s.resultText ?: "ম্যাচ শেষ"
                s.isStarted -> "খেলা চলছে — ট্যাপ করে স্কোরিং চালিয়ে যাও"
                else -> "শুরু হয়নি — ট্যাপ করে টস করো"
            },
            fontSize = 12.sp,
            fontWeight = if (s.isComplete) FontWeight.Bold else FontWeight.Normal,
            color = if (s.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One team's line inside a FixtureScoreCard: initial circle, name, and score (or "-" if they haven't batted). */
@Composable
private fun TeamScoreLine(name: String, score: String?, isWinner: Boolean, isLoser: Boolean) {
    // Fade the losing team a little so the winner stands out at a glance.
    val textColor = if (isLoser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(if (isWinner) PitchGreen else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWinner) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = score ?: "-",
            fontSize = 15.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
