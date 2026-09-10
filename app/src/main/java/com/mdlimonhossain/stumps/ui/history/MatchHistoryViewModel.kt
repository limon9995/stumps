package com.mdlimonhossain.stumps.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.domain.repository.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The ViewModel behind "আমার ম্যাচসমূহ" (my matches) — the list of past matches, and which one (if any) is open in Match Centre. */
class MatchHistoryViewModel(
    repository: MatchRepository,
    uid: String,
    initialMatchId: String? = null
) : ViewModel() {
    // A live, always up-to-date list of every match this user has recorded.
    val matches: StateFlow<List<MatchEntity>> = repository.observeMatchesForUser(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // null = no match currently open. Once set, the screen shows MatchCentreScreen for that
    // match instead of the list — MatchCentreScreen loads its OWN data given just this id, so
    // this ViewModel doesn't need to fetch a match's innings/scorecard itself any more.
    private val _selectedMatchId = MutableStateFlow(initialMatchId)
    val selectedMatchId: StateFlow<String?> = _selectedMatchId

    /** Called when the user taps a match in the list. */
    fun openMatch(matchId: String) {
        _selectedMatchId.value = matchId
    }

    /** Called when the user backs out of Match Centre, back to the list. */
    fun closeMatch() {
        _selectedMatchId.value = null
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(
        private val repository: MatchRepository,
        private val uid: String,
        private val initialMatchId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MatchHistoryViewModel(repository, uid, initialMatchId) as T
    }
}
