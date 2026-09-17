package com.cardhunt.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.optimistic
import com.cardhunt.app.data.remote.dto.NotificationDto
import com.cardhunt.app.data.repository.CardRepository
import com.cardhunt.app.data.repository.NotificationRepository
import com.cardhunt.app.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepo: NotificationRepository,
    private val shareRepo: ShareRepository,
    private val cardRepo: CardRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<NotificationDto>>(emptyList())
    val items = _items.asStateFlow()

    private val _redeemingToken = MutableStateFlow<String?>(null)
    val redeemingToken = _redeemingToken.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar = _snackbar.asSharedFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refresh(markRead = false)
                _loading.value = false
                delay(15_000.milliseconds)
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { notificationRepo.markAllRead() }
    }

    fun refresh(markRead: Boolean = true) {
        viewModelScope.launch {
            val r = notificationRepo.fetch()
            if (r is NetworkResult.Success) _items.value = r.data
            if (markRead && _items.value.any { !it.isRead }) markAllRead()
        }
    }

    fun redeem(token: String) {
        _redeemingToken.value = token
        viewModelScope.launch {
            when (val r = shareRepo.redeem(token)) {
                is NetworkResult.Success -> {
                    _snackbar.emit("🎁 Card added to your collection!" +
                            r.data.newAchievements.joinToString("") { " · Unlocked: ${it.name}" })
                    // Signal that a card was collected via share (NOT photo)
                    cardRepo.emitCollectionEvent(r.data.cardId, byPhoto = false)
                    refresh(markRead = false)
                }
                is NetworkResult.Error -> _snackbar.emit(r.message)
            }
            _redeemingToken.value = null
        }
    }

    fun delete(id: Int) {
        val snapshot = _items.value
        viewModelScope.launch {
            optimistic(
                applyLocal = { _items.update { it.filter { n -> n.id != id } } },
                remote = { notificationRepo.delete(id) },
                rollback = {
                    _items.value = snapshot
                    _snackbar.emit("Failed to delete notification")
                }
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val r = notificationRepo.clearAll()
            if (r is NetworkResult.Success) {
                _items.value = emptyList()
                _snackbar.emit("All notifications cleared")
            } else {
                _snackbar.emit("Failed to clear notifications")
            }
        }
    }
}