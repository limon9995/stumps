package com.mdlimonhossain.stumps.ui.match

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onRuns: (runs: Int, shotAngleDegrees: Int?, selectedBowlerId: String?) -> Unit,
    onExtra: (ExtraType, extraRuns: Int, runsRun: Int, selectedBowlerId: String?) -> Unit,
    onWicket: (DismissalType, dismissedPlayerId: String, newBatsmanId: String?, fielderId: String?, selectedBowlerId: String?) -> Unit,
    // Called when a batsman retires hurt (feeling unwell/injured) and a replacement takes their
    // place. Not a wicket — the retired batsman can be recalled later (see the WicketDialog's
    // "কে ব্যাটিংয়ে আসছে?" step) instead of being permanently out.
    onRetiredHurt: (retiredPlayerId: String, replacementBatsmanId: String) -> Unit = { _, _ -> },
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
    // Whether the "বোলার বদলাও" (change bowler) popup is open — this is the ANYTIME picker
    // (e.g. the current bowler gets injured mid-over), separate from the automatic mandatory
    // pick that shows up right after an over finishes.
    var showBowlerChangeDialog by remember { mutableStateOf(false) }
    // The bowler the scorer just picked (from either dialog above), waiting to be attached to
    // the VERY NEXT ball recorded. Once that ball is sent, this is cleared back to null — from
    // then on the new bowler is simply whoever the scoreboard already says is bowling.
    var manualBowlerOverride by remember { mutableStateOf<String?>(null) }
    // Whether the "অসুস্থ (Retired Hurt)" popup is open.
    var showRetiredHurtDialog by remember { mutableStateOf(false) }

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
        // "ব্যাটসম্যান" (batsmen) section — each batsman now gets their own bordered box, with
        // the striker's box drawn in a stronger colour, so it's obvious at a glance WHO is
        // currently facing the ball. Runs from the buttons below always go to whoever is shown
        // as the striker here.
        Text(text = "ব্যাটসম্যান", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BatsmanRow(name = battingPlayerNames[s.strikerId] ?: "?", figures = s.batsmanFigures[s.strikerId], isStriker = true)
            BatsmanRow(name = battingPlayerNames[s.nonStrikerId] ?: "?", figures = s.batsmanFigures[s.nonStrikerId], isStriker = false)
        }
        Spacer(Modifier.height(10.dp))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            val bowlerFigures = s.bowlerFigures[s.currentBowlerId]
            Text(
                text = "বোলার: ${bowlingPlayerNames[s.currentBowlerId] ?: "?"}  ${bowlerFigures?.overs ?: "0.0"}-${bowlerFigures?.runsConceded ?: 0}-${bowlerFigures?.wickets ?: 0}",
                style = MaterialTheme.typography.bodyMedium
            )
            // If a new bowler has been picked but hasn't actually bowled a ball yet (still
            // waiting for the scorer to tap a run/extra/wicket button), show it here so it's
            // clear the change is queued up and about to take effect.
            manualBowlerOverride?.let { pickedId ->
                Text(
                    text = "পরের বল থেকে বোলার: ${bowlingPlayerNames[pickedId] ?: pickedId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "রান", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        // "রান" (runs) section — fillMaxWidth() + weight(1f) on every button makes the six
        // buttons share the screen's width equally, so all six ALWAYS fit on screen instead of
        // the last one (6) getting pushed off the right edge on narrower phones.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 2, 3, 4, 6).forEach { r ->
                // 4s and 6s get their own colour (the theme's gold "trophy" tertiary shade) so
                // boundaries visually pop out from the ordinary run buttons next to them.
                val isBoundary = r == 4 || r == 6
                Button(
                    // For a 4 or 6, don't record the ball straight away — first ask WHERE it
                    // went, via the shot-direction popup, for the wagon wheel chart. For every
                    // other run value, just record it immediately (no direction needed). Either
                    // way, if a bowler change was queued up (manualBowlerOverride), attach it to
                    // this ball and clear it so it doesn't get applied AGAIN on the next ball.
                    onClick = {
                        if (isBoundary) {
                            pendingBoundaryRuns = r
                        } else {
                            onRuns(r, null, manualBowlerOverride)
                            manualBowlerOverride = null
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
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
        Text(text = "অন্যান্য", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showExtraDialog = true }) { Text("Extra") }
            OutlinedButton(onClick = { showWicketDialog = true }) { Text("Wicket") }
            OutlinedButton(onClick = onUndo) { Text("Undo") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The ANYTIME bowler-change button — for when the current bowler needs to be
            // swapped mid-over (e.g. an injury), not just at the normal end-of-over point.
            OutlinedButton(onClick = { showBowlerChangeDialog = true }) { Text("বোলার বদলাও") }
            // A batsman feeling unwell/injured — NOT the same as being out. See RetiredHurtDialog.
            OutlinedButton(onClick = { showRetiredHurtDialog = true }) { Text("অসুস্থ (Retired Hurt)") }
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

        Spacer(Modifier.height(20.dp))
        ScoringGuideSection()
    }

    // Each of these popups only actually appears on screen when its "show..." state is
    // true/non-null — otherwise nothing is drawn for them at all.
    pendingBoundaryRuns?.let { runs ->
        ShotDirectionDialog(
            runs = runs,
            onPicked = { angle -> onRuns(runs, angle, manualBowlerOverride); manualBowlerOverride = null; pendingBoundaryRuns = null }
        )
    }

    if (showExtraDialog) {
        ExtraDialog(
            onDismiss = { showExtraDialog = false },
            onConfirm = { type, runs ->
                onExtra(type, runs, if (type == ExtraType.WIDE || type == ExtraType.NO_BALL) 0 else runs, manualBowlerOverride)
                manualBowlerOverride = null
                showExtraDialog = false
            }
        )
    }
    if (showWicketDialog) {
        WicketDialog(
            strikerId = s.strikerId,
            strikerName = battingPlayerNames[s.strikerId] ?: "?",
            nonStrikerId = s.nonStrikerId,
            nonStrikerName = battingPlayerNames[s.nonStrikerId] ?: "?",
            bowlingPlayerNames = bowlingPlayerNames,
            // Everyone on the batting side currently sitting out "retired hurt" — offered as a
            // "coming back in" option instead of always just picking a fresh batsman.
            retiredHurtNames = battingPlayerNames.filterKeys { id -> s.batsmanFigures[id]?.isRetiredHurt == true },
            onDismiss = { showWicketDialog = false },
            onConfirm = { type, dismissedId, fielderId, incomingBatsmanId ->
                onWicket(type, dismissedId, incomingBatsmanId, fielderId, manualBowlerOverride)
                manualBowlerOverride = null
                showWicketDialog = false
            }
        )
    }
    if (showRetiredHurtDialog) {
        RetiredHurtDialog(
            strikerId = s.strikerId,
            strikerName = battingPlayerNames[s.strikerId] ?: "?",
            nonStrikerId = s.nonStrikerId,
            nonStrikerName = battingPlayerNames[s.nonStrikerId] ?: "?",
            // Who's actually available to come in as the replacement: anyone on the batting
            // side who hasn't batted yet or is available, excluding whoever's currently at the
            // crease, anyone already out, and anyone else already sitting out retired hurt.
            availableReplacements = battingPlayerNames.filterKeys { id ->
                id != s.strikerId && id != s.nonStrikerId &&
                    s.batsmanFigures[id]?.isOut != true && s.batsmanFigures[id]?.isRetiredHurt != true
            },
            onDismiss = { showRetiredHurtDialog = false },
            onConfirm = { retiredId, replacementId ->
                onRetiredHurt(retiredId, replacementId)
                showRetiredHurtDialog = false
            }
        )
    }

    // The MANDATORY pick: shows automatically (no cancel option) the instant an over finishes,
    // since real cricket doesn't allow the same bowler to bowl two overs back to back. It stays
    // up until the scorer picks someone — as soon as they do, manualBowlerOverride gets attached
    // to the next ball, which makes s.isOverJustCompleted false again on the following redraw
    // (since that next ball is the 1st of the NEW over, not the 6th of the old one), so this
    // dialog then disappears on its own.
    if (s.isOverJustCompleted && manualBowlerOverride == null) {
        BowlerSelectDialog(
            title = "ওভার শেষ — পরের ওভার কে করবে?",
            bowlingPlayerNames = bowlingPlayerNames,
            excludeId = s.previousOverBowlerId,
            dismissible = false,
            onPicked = { id -> manualBowlerOverride = id }
        )
    }
    if (showBowlerChangeDialog) {
        BowlerSelectDialog(
            title = "নতুন বোলার বাছাই করো",
            bowlingPlayerNames = bowlingPlayerNames,
            excludeId = s.currentBowlerId,
            dismissible = true,
            onPicked = { id -> manualBowlerOverride = id; showBowlerChangeDialog = false },
            onDismiss = { showBowlerChangeDialog = false }
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

/**
 * One boxed row showing a batsman's name and their runs (balls) — the striker (the one facing
 * the next ball, and the one who receives runs when a run button is tapped) gets a thicker,
 * coloured border and a tinted background so the two batsmen are easy to tell apart at a
 * glance, instead of the only difference being a small "*" character.
 */
@Composable
private fun BatsmanRow(name: String, figures: com.mdlimonhossain.stumps.domain.scoring.BatsmanFigures?, isStriker: Boolean) {
    val borderColor = if (isStriker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isStriker) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .border(BorderStroke(if (isStriker) 2.dp else 1.dp, borderColor), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            // Spell out "স্ট্রাইকে" (on strike) in words too, not just the "*" mark — makes it
            // unmistakable which batsman the next ball's runs will be added to.
            if (isStriker) {
                Text(text = "স্ট্রাইকে — রান এখানে যোগ হবে", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(text = "${figures?.runs ?: 0} (${figures?.ballsFaced ?: 0})", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A collapsed-by-default "how does this screen work?" help card. It starts closed (just a
 * one-line prompt) so it doesn't clutter the screen, and tapping it expands to show the full
 * plain-language guide — tapping again collapses it back.
 */
@Composable
private fun ScoringGuideSection() {
    var expanded by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "কীভাবে ব্যবহার করবো? (বিস্তারিত)", style = MaterialTheme.typography.titleSmall)
            Text(text = if (expanded) "▲" else "▼", style = MaterialTheme.typography.titleSmall)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            // Each line explains one part of the screen in plain language, for someone who has
            // never scored a cricket match on this app before.
            listOf(
                "উপরে যে ব্যাটসম্যানের ঘরে \"স্ট্রাইকে\" লেখা এবং বর্ডার রঙিন, রান বাটনে চাপ দিলে সেই রান তার নামেই যোগ হবে।",
                "১ বা ৩ রান নিলে স্ট্রাইক বদলে যাবে (অন্য ব্যাটসম্যান স্ট্রাইকে চলে আসবে); ০, ২ বা ৪ রানে স্ট্রাইক বদলায় না।",
                "প্রতি ওভার শেষে স্ট্রাইক এমনিতেই বদলে যায়, ওভারের শেষ বলে যাই রান হোক না কেন।",
                "৪ বা ৬ বাটনে চাপলে বল কোন দিকে গেছে জিজ্ঞেস করবে — এটা শুধু Wagon Wheel চার্টের জন্য তথ্য জমা রাখে; \"স্কিপ করো\" চাপলেও রানটা ঠিকই যোগ হয়ে যাবে।",
                "Extra বাটনে Wide, No Ball, Bye বা Leg Bye যোগ করা যায়।",
                "Wicket বাটনে কে আউট হলো এবং কীভাবে আউট হলো (বোল্ড, ক্যাচ, রান আউট ইত্যাদি) লেখা যায়।",
                "Undo বাটনে সবশেষ বলটি ভুল হলে বাতিল করা যায়।"
            ).forEach { line ->
                Text(text = "•  $line", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
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
    // Every currently "retired hurt and not yet returned" batsman on the BATTING side, id ->
    // name. Empty for the vast majority of wickets (nobody's retired), in which case this
    // dialog behaves exactly as before — the extra "who's coming in" step only appears when
    // there's actually someone eligible to be recalled.
    retiredHurtNames: Map<String, String>,
    onDismiss: () -> Unit,
    // incomingBatsmanId: null means "just send in the next fresh batsman as usual" — non-null
    // means the scorer specifically chose to bring back one of retiredHurtNames.
    onConfirm: (DismissalType, dismissedId: String, fielderId: String?, incomingBatsmanId: String?) -> Unit
) {
    // Defaults to "the striker got out" since that's true for most dismissals — the scorer can
    // switch to the non-striker for the (rarer) case of a run out at the other end.
    var dismissed by remember { mutableStateOf(strikerId) }
    // null = still on the "who/how" step. Once set to a fielder-crediting type, the dialog
    // switches to its "who fielded it" step instead.
    var pendingFielderType by remember { mutableStateOf<DismissalType?>(null) }
    // Set once type (+ fielder, if needed) is decided AND there's at least one retired-hurt
    // batsman available to recall — the dialog then shows one more step asking who's coming in,
    // instead of confirming immediately. Holds everything onConfirm needs once that's answered.
    var awaitingIncoming by remember { mutableStateOf<Pair<DismissalType, String?>?>(null) }

    // Shared by both "who/how" and "who fielded it" steps below: once the dismissal type (and
    // fielder, if any) is settled, either ask who's batting in next (if anyone's eligible to
    // return from retired hurt) or just confirm immediately like before.
    fun proceedAfterTypeChosen(type: DismissalType, fielderId: String?) {
        if (retiredHurtNames.isEmpty()) {
            dismissed?.let { onConfirm(type, it, fielderId, null) }
        } else {
            awaitingIncoming = type to fielderId
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("উইকেট") },
        text = {
            val incoming = awaitingIncoming
            val fielderType = pendingFielderType
            if (incoming != null) {
                Column {
                    Text("কে ব্যাটিংয়ে আসছে?")
                    // The retired-hurt option(s) come first since recalling them is the whole
                    // point of this extra step — "নতুন ব্যাটসম্যান" is for the ordinary case
                    // where the scorer just wants the next fresh player as usual.
                    retiredHurtNames.forEach { (id, name) ->
                        TextButton(onClick = {
                            dismissed?.let { onConfirm(incoming.first, it, incoming.second, id) }
                        }) { Text("$name (ফিরছে)") }
                    }
                    TextButton(onClick = {
                        dismissed?.let { onConfirm(incoming.first, it, incoming.second, null) }
                    }) { Text("নতুন ব্যাটসম্যান") }
                }
            } else if (fielderType == null) {
                Column {
                    Text("কে আউট হলো?")
                    Row {
                        TextButton(onClick = { dismissed = strikerId }) { Text(strikerName + if (dismissed == strikerId) " ✓" else "") }
                        TextButton(onClick = { dismissed = nonStrikerId }) { Text(nonStrikerName + if (dismissed == nonStrikerId) " ✓" else "") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("কিভাবে আউট?")
                    // RETIRED_HURT is deliberately left out here — retiring isn't a real
                    // dismissal, so it has its own separate "অসুস্থ (Retired Hurt)" button on
                    // the scoring screen instead of going through this wicket flow at all.
                    DismissalType.entries.filter { it != DismissalType.RETIRED_HURT }.forEach { type ->
                        TextButton(onClick = {
                            if (type == DismissalType.CAUGHT || type == DismissalType.STUMPED || type == DismissalType.RUN_OUT) {
                                pendingFielderType = type
                            } else {
                                // No fielder to ask about — move straight to the next step.
                                proceedAfterTypeChosen(type, null)
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
                        TextButton(onClick = { proceedAfterTypeChosen(fielderType, name) }) { Text(name) }
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

/**
 * The popup for recording a batsman retiring hurt (feeling unwell or injured mid-innings) and
 * picking their replacement. Unlike WicketDialog, there's no "how did they get out" step at
 * all — retiring isn't a dismissal, it's just a pause; the real cricket rule is that this
 * batsman can be recalled later (see WicketDialog's "কে ব্যাটিংয়ে আসছে?" step) to resume their
 * innings from wherever they left off.
 */
@Composable
private fun RetiredHurtDialog(
    strikerId: String?,
    strikerName: String,
    nonStrikerId: String?,
    nonStrikerName: String,
    availableReplacements: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (retiredPlayerId: String, replacementId: String) -> Unit
) {
    // Defaults to the striker since that's who's actually facing the bowling right now, but the
    // scorer can switch to the non-striker if that's who actually needs to go off.
    var retiring by remember { mutableStateOf(strikerId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("অসুস্থ / আহত — বিশ্রামে যাচ্ছে") },
        text = {
            Column {
                Text("কে অসুস্থ/আহত হয়ে বিশ্রামে যাচ্ছে?")
                Row {
                    TextButton(onClick = { retiring = strikerId }) { Text(strikerName + if (retiring == strikerId) " ✓" else "") }
                    TextButton(onClick = { retiring = nonStrikerId }) { Text(nonStrikerName + if (retiring == nonStrikerId) " ✓" else "") }
                }
                Spacer(Modifier.height(8.dp))
                if (availableReplacements.isEmpty()) {
                    // Every remaining player is either already batting, already out, or already
                    // sitting out retired hurt themselves — nobody's actually free to come in.
                    Text(
                        "বাকি কোনো ব্যাটসম্যান নেই যাকে পাঠানো যায় — এখন রিটায়ার্ড হার্ট করা যাবে না।",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("কে ব্যাটিংয়ে আসছে?")
                    availableReplacements.forEach { (id, name) ->
                        TextButton(onClick = { retiring?.let { onConfirm(it, id) } }) { Text(name) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

/**
 * A popup listing everyone on the bowling side, for picking who bowls next. Used in two
 * different situations:
 *  - The MANDATORY pick right after an over ends (real cricket doesn't allow the same bowler to
 *    bowl two overs back to back) — `excludeId` is that bowler, and `dismissible = false` means
 *    there's no way to close this without picking someone.
 *  - The ANYTIME "বোলার বদলাও" button, for when the current bowler needs to be swapped mid-over
 *    (e.g. an injury) — `excludeId` is just the CURRENT bowler (picking the same person again
 *    wouldn't be a change), and `dismissible = true` lets the scorer cancel out of it.
 */
@Composable
private fun BowlerSelectDialog(
    title: String,
    bowlingPlayerNames: Map<String, String>,
    excludeId: String?,
    dismissible: Boolean,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    // If leaving out excludeId would leave NOBODY to pick (e.g. a tiny 2-player quick match),
    // show everyone anyway — better to allow a technically-against-the-rules repeat bowler than
    // to leave the scorer stuck with an empty list and no way to carry on scoring.
    val allIds = bowlingPlayerNames.keys.toList()
    val choices = allIds.filter { it != excludeId }.ifEmpty { allIds }
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { id ->
                    TextButton(onClick = { onPicked(id) }) { Text(bowlingPlayerNames[id] ?: id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { if (dismissible) TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}
