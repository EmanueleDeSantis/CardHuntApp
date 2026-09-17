package com.cardhunt.app.ui.staff

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.optimistic
import com.cardhunt.app.data.remote.dto.AdminCardDto
import com.cardhunt.app.data.remote.dto.AdminUserDto
import com.cardhunt.app.data.repository.StaffRepository
import com.cardhunt.app.data.repository.AuthRepository
import com.cardhunt.app.ui.common.CardSort
import com.cardhunt.app.ui.common.UserSort
import com.cardhunt.app.ui.common.rarityRank
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffDashboardViewModel @Inject constructor(
    private val staffRepo: StaffRepository,
    authRepo: AuthRepository,
) : ViewModel() {

    private val me = authRepo.snapshot()
    val currentUserId: Int = me?.userId ?: -1
    val currentRole: String = me?.role ?: "USER"

    private val _users = MutableStateFlow<List<AdminUserDto>>(emptyList())
    val users = _users.asStateFlow()

    private val _cards = MutableStateFlow<List<AdminCardDto>>(emptyList())
    val cards = _cards.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar = _snackbar.asSharedFlow()

    var userSort by mutableStateOf(UserSort.NAME)
        private set
    var cardSort by mutableStateOf(CardSort.DATE)
        private set

    fun updateUserSort(s: UserSort) { userSort = s }
    fun updateCardSort(s: CardSort) { cardSort = s }

    fun sortUsers(list: List<AdminUserDto>): List<AdminUserDto> = when (userSort) {
        UserSort.NAME -> list.sortedBy { it.username.lowercase() }
        UserSort.XP -> list.sortedByDescending { it.xp }
        UserSort.CARDS -> list.sortedByDescending { it.cardsCount }
    }

    fun sortCards(list: List<AdminCardDto>): List<AdminCardDto> = when (cardSort) {
        CardSort.NAME -> list.sortedBy { it.name.lowercase() }
        CardSort.RARITY -> list.sortedByDescending { rarityRank(it.rarity) }
        CardSort.DATE -> list.sortedByDescending { it.createdAt ?: "" }
    }

    fun loadData() {
        viewModelScope.launch {
            val u = staffRepo.getUsers()
            if (u is NetworkResult.Success) _users.value = u.data
            val c = staffRepo.getAllCards()
            if (c is NetworkResult.Success) _cards.value = c.data
        }
    }

    fun updateStatus(userId: Int, status: String) {
        val snapshot = _users.value
        viewModelScope.launch {
            optimistic(
                applyLocal = { _users.update { list -> list.map { if (it.id == userId) it.copy(status = status) else it } } },
                remote = { staffRepo.updateUserStatus(userId, status) },
                rollback = { _users.value = snapshot; _snackbar.emit("Status change failed — reverted") }
            )
        }
    }

    fun updateRole(userId: Int, role: String) {
        val snapshot = _users.value
        viewModelScope.launch {
            optimistic(
                applyLocal = { _users.update { list -> list.map { if (it.id == userId) it.copy(role = role) else it } } },
                remote = { staffRepo.updateUserRole(userId, role) },
                rollback = { _users.value = snapshot; _snackbar.emit("Role change failed — reverted") }
            )
        }
    }

    fun deleteCard(cardId: Int) {
        val snapshot = _cards.value
        viewModelScope.launch {
            optimistic(
                applyLocal = { _cards.update { it.filter { c -> c.id != cardId } } },
                remote = { staffRepo.deleteCard(cardId) },
                rollback = { _cards.value = snapshot; _snackbar.emit("Delete failed — reverted") }
            )
        }
    }
}