package com.mdlimonhossain.stumps.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import com.mdlimonhossain.stumps.ui.match.MatchCentreScreen
import com.mdlimonhossain.stumps.ui.match.ResumeMatchScreen
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "আমার ম্যাচসমূহ" (my matches) screen — this is what the bottom nav bar's "Matches" tab
 * actually opens: a list of past matches, tap one to open its full Match Centre.
 * `initialMatchId`, when given, jumps straight into that match's Match Centre instead of
 * showing the list first — used when arriving here from a match card on the Home dashboard.
 * Matches still mid-innings also get a "চালিয়ে যাও" (continue) button, so exiting a match early
 * doesn't strand it forever.
 */
@Composable
fun MatchHistoryScreen(uid: String, onBack: () -> Unit, initialMatchId: String? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: MatchHistoryViewModel = viewModel(
        factory = MatchHistoryViewModel.Factory(app.matchRepository, uid, initialMatchId)
    )
    val matches by viewModel.matches.collectAsState()
    val selectedMatchId by viewModel.selectedMatchId.collectAsState()
    var resumeMatchId by remember { mutableStateOf<String?>(null) }

    if (resumeMatchId != null) {
        ResumeMatchScreen(matchId = resumeMatchId!!, onFinished = { resumeMatchId = null })
        return
    }

    // If a match has been tapped (or we arrived with one pre-selected), show its Match Centre
    // instead of the list.
    selectedMatchId?.let { matchId ->
        MatchCentreScreen(matchId = matchId, onBack = viewModel::closeMatch)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← হোমে ফিরে যাও") }
        }
        Text(
            text = "আমার ম্যাচসমূহ",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(16.dp))

        if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "এখনো কোনো ম্যাচ নেই",
                    subtitle = "হোম থেকে প্রথম ম্যাচটি শুরু করো — এখানে চলে আসবে।"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp)
            ) {
                itemsIndexed(matches, key = { _, m -> m.id }) { index, match ->
                    MatchHistoryRow(
                        match = match,
                        modifier = Modifier.staggeredEntrance(index).padding(vertical = 6.dp),
                        onView = { viewModel.openMatch(match.id) },
                        onResume = { resumeMatchId = match.id }
                    )
                }
            }
        }
    }
}

/** One match row — figures out for ITSELF whether this match is still mid-innings (and so worth offering a "continue scoring" button for). */
@Composable
private fun MatchHistoryRow(match: MatchEntity, modifier: Modifier = Modifier, onView: () -> Unit, onResume: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var isResumable by remember { mutableStateOf(false) }

    LaunchedEffect(match.id) {
        val innings = app.matchRepository.observeInningsForMatch(match.id).first()
        val lastInnings = innings.lastOrNull() ?: return@LaunchedEffect
        val finalStates = app.matchRepository.getFinalInningsStates(match.id)
        val lastState = finalStates.lastOrNull { it.innings.id == lastInnings.id }
        // Resumable = there's an innings, and it genuinely hasn't finished yet (not all-out, not
        // out of overs) — a match already fully scored has nothing left to "continue".
        isResumable = lastState != null && !lastState.state.isInningsComplete
    }

    // Format the raw saved timestamp (a plain number of milliseconds) into a readable date/time.
    val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(match.createdAt))
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onView) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${match.oversLimit}-over ম্যাচ", style = MaterialTheme.typography.titleMedium)
                Text(text = date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Status: ${match.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isResumable) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "স্কোরিং চালিয়ে যাও",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (isResumable) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("স্কোরিং চালিয়ে যাও")
            }
        }
    }
}
