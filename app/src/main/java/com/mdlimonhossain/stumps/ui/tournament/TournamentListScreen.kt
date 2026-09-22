package com.mdlimonhossain.stumps.ui.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The tournament list screen: every tournament this user runs, plus a form to create a new one.
 * `startWithCreateForm` lets a caller (e.g. the "Create Tournament" item in the side drawer)
 * jump straight into the creation form instead of showing the list first.
 */
@Composable
fun TournamentListScreen(
    uid: String,
    onOpenTournament: (String) -> Unit,
    onBack: () -> Unit,
    startWithCreateForm: Boolean = false
) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: TournamentListViewModel = viewModel(factory = TournamentListViewModel.Factory(app.tournamentRepository, uid))
    val tournaments by viewModel.tournaments.collectAsState()
    // If the organizer has a registered club, its name/city/ball-type pre-fill the create form —
    // same idea as the reference app showing "Limon's Club" already typed in.
    val myClubs by app.clubRepository.observeClubsForUser(uid).collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(startWithCreateForm) }

    if (showCreate) {
        TournamentSetupForm(
            myClub = myClubs.firstOrNull(),
            onCancel = { showCreate = false },
            onCreate = { name, overs, venue, clubName, city, season, startDate, endDate, ballType ->
                viewModel.createTournament(uid, name, overs, venue, clubName, city, season, startDate, endDate, ballType) { id ->
                    showCreate = false
                    onOpenTournament(id) // jump straight into the new tournament once it's created
                }
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "টুর্নামেন্ট", style = MaterialTheme.typography.headlineLarge)
        TextButton(onClick = onBack) { Text("হোমে ফিরে যাও") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
            Text("নতুন টুর্নামেন্ট")
        }
        Spacer(Modifier.height(16.dp))

        if (tournaments.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "এখনো কোনো টুর্নামেন্ট নেই",
                subtitle = "উপরের বাটনে চেপে তোমার প্রথম টুর্নামেন্ট বানাও।",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn {
                itemsIndexed(tournaments, key = { _, t -> t.id }) { index, t ->
                    // Same gradient-banner-topped card style as Home's own Tournaments rail
                    // (see HomeScreen.kt's TournamentCard) — kept visually consistent so a
                    // tournament looks like the same "thing" whichever screen shows it.
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .staggeredEntrance(index),
                        onClick = { onOpenTournament(t.id) }, // tapping the whole card opens that tournament
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.tertiary, com.mdlimonhossain.stumps.ui.theme.PitchGreen))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            val subtitle = listOfNotNull(t.clubName, t.city).joinToString(", ").ifBlank { t.venue }
                            subtitle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(4.dp))
                            Text(text = t.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(text = "${t.oversPerMatch} ওভার প্রতি ম্যাচ", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The "create a new tournament" form — matches the reference app's field set: name, club/
 * organisation, city, season/year, an optional start/end date, and a ball type. Deciding which
 * TEAMS take part no longer happens here — that's now done afterwards, one team at a time, from
 * the new tournament's own Teams tab (see TournamentDetailScreen.kt), the same "structure first,
 * fill in the roster afterwards" pattern already used for creating a saved team.
 * "প্রতি ম্যাচে ওভার" (overs per match) stays on this form even though the reference app doesn't
 * show it here — every match in this app's tournaments shares one overs limit, and there's
 * nowhere else in this simpler flow to ask for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TournamentSetupForm(
    myClub: com.mdlimonhossain.stumps.data.local.db.club.ClubEntity?,
    onCancel: () -> Unit,
    onCreate: (
        name: String, overs: Int, venue: String?, clubName: String?, city: String?,
        season: String?, startDate: Long?, endDate: Long?, ballType: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var clubName by remember { mutableStateOf(myClub?.name ?: "") }
    var city by remember { mutableStateOf(myClub?.city ?: "") }
    var season by remember { mutableStateOf("") }
    var oversText by remember { mutableStateOf("20") }
    var venue by remember { mutableStateOf("") }
    var ballType by remember { mutableStateOf(myClub?.ballType ?: "LEATHER") }
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }
    var showSeasonPicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // Only starts showing red error text once the user has tapped the create button at least
    // once — a fresh form shouldn't look broken before anyone has typed anything.
    var attemptedSubmit by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(text = "নতুন টুর্নামেন্ট", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        val nameMissing = name.isBlank()
        val clubNameMissing = clubName.isBlank()
        val cityMissing = city.isBlank()
        val seasonMissing = season.isBlank()
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("টুর্নামেন্টের নাম") },
            isError = attemptedSubmit && nameMissing,
            supportingText = { if (attemptedSubmit && nameMissing) Text("টুর্নামেন্টের নাম লিখতে হবে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = clubName,
            onValueChange = { clubName = it },
            label = { Text("ক্লাব / সংগঠনের নাম") },
            isError = attemptedSubmit && clubNameMissing,
            supportingText = { if (attemptedSubmit && clubNameMissing) Text("ক্লাব বা সংগঠনের নাম লিখতে হবে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("শহর") },
            isError = attemptedSubmit && cityMissing,
            supportingText = { if (attemptedSubmit && cityMissing) Text("শহরের নাম লিখতে হবে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = season,
            onValueChange = {},
            readOnly = true,
            label = { Text("সিজন / বছর") },
            placeholder = { Text("বেছে নাও") },
            isError = attemptedSubmit && seasonMissing,
            supportingText = { if (attemptedSubmit && seasonMissing) Text("একটা সিজন/বছর বেছে নাও") },
            modifier = Modifier.fillMaxWidth().clickable { showSeasonPicker = true }
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startDateMillis?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("শুরুর তারিখ") },
                modifier = Modifier.weight(1f).clickable { showStartPicker = true }
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = endDateMillis?.let { dateFormat.format(Date(it)) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("শেষের তারিখ") },
                modifier = Modifier.weight(1f).clickable { showEndPicker = true }
            )
        }
        Spacer(Modifier.height(8.dp))
        val oversInvalid = (oversText.toIntOrNull() ?: 0) <= 0
        OutlinedTextField(
            value = oversText,
            onValueChange = { oversText = it },
            label = { Text("প্রতি ম্যাচে কত ওভার") },
            isError = attemptedSubmit && oversInvalid,
            supportingText = { if (attemptedSubmit && oversInvalid) Text("০ এর বেশি একটা ওভার সংখ্যা লিখো") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("ভেন্যু (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Text(text = "বলের ধরন", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row {
            com.mdlimonhossain.stumps.ui.common.AnimatedTabChip(
                label = "লেদার বল", selected = ballType == "LEATHER", modifier = Modifier.weight(1f)
            ) { ballType = "LEATHER" }
            Spacer(Modifier.width(8.dp))
            com.mdlimonhossain.stumps.ui.common.AnimatedTabChip(
                label = "টেনিস বল", selected = ballType == "TENNIS", modifier = Modifier.weight(1f)
            ) { ballType = "TENNIS" }
        }

        Spacer(Modifier.height(20.dp))
        val overs = oversText.toIntOrNull() ?: 0
        val isValid = !nameMissing && !clubNameMissing && !cityMissing && !seasonMissing && overs > 0
        if (attemptedSubmit && !isValid) {
            Text(
                text = "উপরে লাল করে দেখানো জায়গাগুলো ঠিক করো, তারপর আবার চেষ্টা করো।",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }
        // Always tappable — tapping while something's missing just lights up the red messages
        // above instead of silently doing nothing.
        Button(
            onClick = {
                attemptedSubmit = true
                if (isValid) {
                    onCreate(
                        name, overs, venue.ifBlank { null }, clubName.ifBlank { null }, city.ifBlank { null },
                        season.ifBlank { null }, startDateMillis, endDateMillis, ballType
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("টুর্নামেন্ট তৈরি করো") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("বাতিল") }
    }

    if (showSeasonPicker) {
        SeasonPickerDialog(
            selected = season,
            onDismiss = { showSeasonPicker = false },
            onPick = { season = it; showSeasonPicker = false }
        )
    }
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = { TextButton(onClick = { startDateMillis = state.selectedDateMillis; showStartPicker = false }) { Text("ঠিক আছে") } },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("বাতিল") } }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = { endDateMillis = state.selectedDateMillis; showEndPicker = false }) { Text("ঠিক আছে") } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("বাতিল") } }
        ) { DatePicker(state = state) }
    }
}

/**
 * A scrollable list of season labels to choose from, e.g. "2026-27" or "2026" — matches the
 * reference app's picker. Built fresh from today's actual calendar year every time (rather than
 * a hardcoded list), so it never quietly goes stale as real years pass.
 */
@Composable
private fun SeasonPickerDialog(selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val options = remember {
        buildList {
            add("${currentYear + 1}")
            for (y in currentYear downTo currentYear - 5) {
                add("$y-${((y + 1) % 100).toString().padStart(2, '0')}")
                add("$y")
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সিজন / বছর বেছে নাও") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Text(
                        text = "Year $option",
                        color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (option == selected) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করো") } }
    )
}
