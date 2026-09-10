package com.mdlimonhossain.stumps.ui.match

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import com.mdlimonhossain.stumps.domain.repository.LiveInnings
import com.mdlimonhossain.stumps.ui.designsystem.AppCard

/**
 * The main LIVE SCORING screen — this is the screen the scorer actually looks at during a
 * match, with run buttons, extras, wicket recording, undo, and live broadcast controls. Every
 * button here just calls one of the "on..." functions it was given — this screen doesn't save
 * anything to the database itself, it just tells MatchViewModel what happened and trusts
 * MatchViewModel + MatchRepository to do the actual work (see those files for the real logic).
 */
@Composable
fun ScoringScreen(
    live: LiveInnings?, // the current live scoreboard — null while it's still loading
    battingPlayerNames: Map<String, String>,
    bowlingPlayerNames: Map<String, String>,
    onRuns: (runs: Int, shotAngleDegrees: Int?) -> Unit,
    onExtra: (ExtraType, extraRuns: Int, runsRun: Int) -> Unit,
    onWicket: (DismissalType, dismissedPlayerId: String, newBatsmanId: String?, fielderId: String?) -> Unit,
    onUndo: () -> Unit,
    onInningsComplete: () -> Unit,
    onGoLive: () -> Unit = {},
    onStopLive: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    if (live == null) {
        Text(text = "লোড হচ্ছে...", modifier = Modifier.padding(24.dp))
        return
    }
    val s = live.state
    // Which popup dialog (if any) is currently showing on top of the screen.
    var showWicketDialog by remember { mutableStateOf(false) }
    var showExtraDialog by remember { mutableStateOf(false) }
    // Confirming before actually leaving mid-match — a plain back button here could be tapped
    // by accident, and unlike the other screens, leaving mid-scoring has a real consequence
    // worth a second's pause (see the dialog text below for exactly what it means).
    var showExitConfirm by remember { mutableStateOf(false) }
    // Set only briefly, right after tapping 4 or 6, while we wait for the scorer to pick a
    // shot direction (or skip) in the ShotDirectionDialog — null means no dialog is showing.
    var pendingBoundaryRuns by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = { showExitConfirm = true }) { Text("← বের হও") }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "${s.totalRuns}/${s.totalWickets}", style = MaterialTheme.typography.headlineLarge)
                Text(text = "${s.oversDisplay} ওভার  •  RR ${"%.2f".format(s.runRate)}", style = MaterialTheme.typography.bodyLarge)
            }
            // Show either a pulsing "LIVE" badge + share code (if broadcasting), or a button to
            // start broadcasting (if not) — never both at once.
            if (live.match.isLive) {
                Column(horizontalAlignment = Alignment.End) {
                    LiveBadge()
                    Text(text = "কোড: ${live.match.shareCode}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onStopLive) { Text("বন্ধ করো") }
                }
            } else {
                OutlinedButton(onClick = onGoLive) { Text("লাইভ ব্রডকাস্ট শুরু করো") }
            }
        }

        Spacer(Modifier.height(16.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            BatsmanRow(name = battingPlayerNames[s.strikerId] ?: "?", figures = s.batsmanFigures[s.strikerId], isStriker = true)
            BatsmanRow(name = battingPlayerNames[s.nonStrikerId] ?: "?", figures = s.batsmanFigures[s.nonStrikerId], isStriker = false)
            Spacer(Modifier.height(8.dp))
            val bowlerFigures = s.bowlerFigures[s.currentBowlerId]
            Text(
                text = "বোলার: ${bowlingPlayerNames[s.currentBowlerId] ?: "?"}  ${bowlerFigures?.overs ?: "0.0"}-${bowlerFigures?.runsConceded ?: 0}-${bowlerFigures?.wickets ?: 0}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "রান", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2, 3, 4, 6).forEach { r ->
                // 4s and 6s get their own colour (the theme's gold "trophy" tertiary shade) so
                // boundaries visually pop out from the ordinary run buttons next to them.
                val isBoundary = r == 4 || r == 6
                Button(
                    // For a 4 or 6, don't record the ball straight away — first ask WHERE it
                    // went, via the shot-direction popup, for the wagon wheel chart. For every
                    // other run value, just record it immediately (no direction needed).
                    onClick = { if (isBoundary) pendingBoundaryRuns = r else onRuns(r, null) },
                    modifier = Modifier.height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (isBoundary) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) { Text("$r") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showExtraDialog = true }) { Text("Extra") }
            OutlinedButton(onClick = { showWicketDialog = true }) { Text("Wicket") }
            OutlinedButton(onClick = onUndo) { Text("Undo") }
        }

        // Show the "finish this innings" button either when the innings genuinely ended
        // (all out / overs used up) OR when the chasing team has already reached their target
        // (in which case there's no point playing on — the match is already decided).
        val targetReached = live.innings.targetRuns?.let { s.totalRuns >= it } ?: false
        if (s.isInningsComplete || targetReached) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onInningsComplete, modifier = Modifier.fillMaxWidth()) {
                Text(if (targetReached) "টার্গেট শেষ — ম্যাচ শেষ করো" else "ইনিংস শেষ — এগিয়ে যাও")
            }
        }
    }

    // Each of these three popups only actually appears on screen when its "show..." state is
    // true/non-null — otherwise nothing is drawn for them at all.
    pendingBoundaryRuns?.let { runs ->
        ShotDirectionDialog(
            runs = runs,
            onPicked = { angle -> onRuns(runs, angle); pendingBoundaryRuns = null }
        )
    }

    if (showExtraDialog) {
        ExtraDialog(
            onDismiss = { showExtraDialog = false },
            onConfirm = { type, runs -> onExtra(type, runs, if (type == ExtraType.WIDE || type == ExtraType.NO_BALL) 0 else runs); showExtraDialog = false }
        )
    }
    if (showWicketDialog) {
        WicketDialog(
            strikerId = s.strikerId,
            strikerName = battingPlayerNames[s.strikerId] ?: "?",
            nonStrikerId = s.nonStrikerId,
            nonStrikerName = battingPlayerNames[s.nonStrikerId] ?: "?",
            bowlingPlayerNames = bowlingPlayerNames,
            onDismiss = { showWicketDialog = false },
            onConfirm = { type, dismissedId, fielderId -> onWicket(type, dismissedId, null, fielderId); showWicketDialog = false }
        )
    }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("ম্যাচ থেকে বের হবে?") },
            text = { Text("এখন পর্যন্ত যা স্কোর হয়েছে সেভ আছে, কিন্তু এখান থেকে বের হলে এই ম্যাচে আবার scoring চালিয়ে যাওয়া যাবে না — শুধু ম্যাচ হিস্ট্রি থেকে দেখা যাবে।") },
            confirmButton = { TextButton(onClick = { showExitConfirm = false; onExit() }) { Text("বের হও") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("থাকো") } }
        )
    }
}

/** A small red dot that gently pulses (fades in and out forever) next to the word "LIVE", the
 * way a live-broadcast indicator usually does — a quick, wordless visual cue that this match is
 * actively being shared right now, not just a static label. */
@Composable
private fun LiveBadge() {
    // rememberInfiniteTransition + animateFloat with infiniteRepeatable is Compose's way of
    // saying "keep animating this value back and forth forever, until this composable goes
    // away" — unlike the other animations in this app, which all play ONCE and stop.
    val infiniteTransition = rememberInfiniteTransition(label = "liveBadgePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveBadgeDotAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .alpha(dotAlpha)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = "LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}

/** One line showing a batsman's name (with a "*" if they're on strike) and their runs (balls). */
@Composable
private fun BatsmanRow(name: String, figures: com.mdlimonhossain.stumps.domain.scoring.BatsmanFigures?, isStriker: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = (if (isStriker) "* " else "") + name, style = MaterialTheme.typography.bodyLarge)
        Text(text = "${figures?.runs ?: 0} (${figures?.ballsFaced ?: 0})", style = MaterialTheme.typography.bodyMedium)
    }
}

/** The popup for recording a wide/no-ball/bye/leg-bye. */
@Composable
private fun ExtraDialog(onDismiss: () -> Unit, onConfirm: (ExtraType, Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extra") },
        text = {
            Column {
                // One button per extra type — tapping any of them immediately records 1 extra
                // run of that type (a scorer can add MORE runs separately using the normal run
                // buttons afterwards if the batsmen also ran, e.g. "wide + 1 run").
                listOf(
                    ExtraType.WIDE to "Wide",
                    ExtraType.NO_BALL to "No Ball",
                    ExtraType.BYE to "Bye",
                    ExtraType.LEG_BYE to "Leg Bye"
                ).forEach { (type, label) ->
                    TextButton(onClick = { onConfirm(type, if (type == ExtraType.WIDE || type == ExtraType.NO_BALL) 1 else 1) }) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

/**
 * The popup for recording a wicket: pick who got out, then how, then — ONLY for CAUGHT/STUMPED/
 * RUN_OUT, since those are the three dismissal types where a fielder actually did something —
 * an extra step to credit which fielder gets the catch/stumping/run-out. BOWLED/LBW/etc skip
 * straight to confirming, since there's no fielder to credit for those.
 */
@Composable
private fun WicketDialog(
    strikerId: String?,
    strikerName: String,
    nonStrikerId: String?,
    nonStrikerName: String,
    bowlingPlayerNames: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (DismissalType, dismissedId: String, fielderId: String?) -> Unit
) {
    // Defaults to "the striker got out" since that's true for most dismissals — the scorer can
    // switch to the non-striker for the (rarer) case of a run out at the other end.
    var dismissed by remember { mutableStateOf(strikerId) }
    // null = still on the "who/how" step. Once set to a fielder-crediting type, the dialog
    // switches to its "who fielded it" step instead.
    var pendingFielderType by remember { mutableStateOf<DismissalType?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("উইকেট") },
        text = {
            val fielderType = pendingFielderType
            if (fielderType == null) {
                Column {
                    Text("কে আউট হলো?")
                    Row {
                        TextButton(onClick = { dismissed = strikerId }) { Text(strikerName + if (dismissed == strikerId) " ✓" else "") }
                        TextButton(onClick = { dismissed = nonStrikerId }) { Text(nonStrikerName + if (dismissed == nonStrikerId) " ✓" else "") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("কিভাবে আউট?")
                    DismissalType.entries.forEach { type ->
                        TextButton(onClick = {
                            if (type == DismissalType.CAUGHT || type == DismissalType.STUMPED || type == DismissalType.RUN_OUT) {
                                pendingFielderType = type
                            } else {
                                // No fielder to ask about — confirm the whole wicket right away.
                                dismissed?.let { onConfirm(type, it, null) }
                            }
                        }) { Text(type.name) }
                    }
                }
            } else {
                Column {
                    Text(fielderPromptText(fielderType))
                    // bowlingPlayerNames' keys and values are the SAME string in this app's
                    // simplified player-identity model (see the note in MatchFlow.kt) — so any
                    // one of them works equally well as both the fielder's id and display name.
                    bowlingPlayerNames.keys.forEach { name ->
                        TextButton(onClick = { dismissed?.let { onConfirm(fielderType, it, name) } }) { Text(name) }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { pendingFielderType = null }) { Text("← ফিরে যাও") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

private fun fielderPromptText(type: DismissalType): String = when (type) {
    DismissalType.CAUGHT -> "কে ক্যাচ ধরলো?"
    DismissalType.STUMPED -> "কোন উইকেট-কিপার স্টাম্প করলো?"
    DismissalType.RUN_OUT -> "কে রান আউট করলো?"
    else -> "কে করলো?" // unreachable — this dialog step only ever shows for the three types above
}
