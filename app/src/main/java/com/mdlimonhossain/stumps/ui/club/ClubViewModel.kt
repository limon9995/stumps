package com.mdlimonhossain.stumps.ui.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.club.ClubEntity
import com.mdlimonhossain.stumps.domain.repository.ClubRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The ViewModel behind "আমার ক্লাব" (my clubs) — the list of clubs this user has registered, plus registering a new one. */
class ClubViewModel(private val repository: ClubRepository, uid: String) : ViewModel() {
    val clubs: StateFlow<List<ClubEntity>> = repository.observeClubsForUser(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Called when the "Register" button on the club-registration form is tapped. */
    fun registerClub(uid: String, name: String, city: String, establishedYear: Int, ballType: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.registerClub(uid, name, city, establishedYear, ballType)
            onDone(id)
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(private val repository: ClubRepository, private val uid: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ClubViewModel(repository, uid) as T
    }
}
