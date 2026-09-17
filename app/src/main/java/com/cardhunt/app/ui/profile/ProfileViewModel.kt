package com.cardhunt.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.optimistic
import com.cardhunt.app.cloudinary.CloudinaryUploader
import com.cardhunt.app.data.local.ThemeStore
import com.cardhunt.app.data.remote.dto.*
import com.cardhunt.app.data.repository.AuthRepository
import com.cardhunt.app.data.repository.FriendRepository
import com.cardhunt.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val friendRepo: FriendRepository,
    private val authRepo: AuthRepository,
    private val themeStore: ThemeStore,
    private val cloudinaryUploader: CloudinaryUploader,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui = _ui.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar = _snackbar.asSharedFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    val themeMode = themeStore.mode
    fun setThemeMode(mode: String) = viewModelScope.launch { themeStore.setMode(mode) }

    private var searchJob: Job? = null

    fun loadAll() {
        _loading.value = true
        viewModelScope.launch {
            val me = profileRepo.getMe()
            if (me is NetworkResult.Success) _ui.update { it.copy(profile = me.data) }
            refreshFriends()
            _loading.value = false
        }
    }

    fun refreshFriends() {
        viewModelScope.launch {
            val r = friendRepo.getFriends()
            if (r is NetworkResult.Success) {
                _ui.update {
                    it.copy(friends = r.data.friends, incoming = r.data.incoming,
                        outgoing = r.data.outgoing.toSet())
                }
            }
        }
    }

    fun onSearchChanged(q: String) {
        searchJob?.cancel()
        if (q.length < 2) { _ui.update { it.copy(searchResults = emptyList()) }; return }
        searchJob = viewModelScope.launch {
            delay(300)
            _ui.update { it.copy(searching = true) }
            val r = friendRepo.search(q)
            _ui.update {
                it.copy(searching = false,
                    searchResults = if (r is NetworkResult.Success) r.data else emptyList())
            }
        }
    }

    fun sendRequest(user: UserSearchDto) {
        viewModelScope.launch {
            optimistic(
                applyLocal = { _ui.update { it.copy(pendingActions = it.pendingActions + user.id) } },
                remote = { friendRepo.sendRequest(user.id) },
                rollback = {
                    _ui.update { it.copy(pendingActions = it.pendingActions - user.id) }
                    _snackbar.emit("Could not send request — reverted")
                },
            ).also { r ->
                if (r is NetworkResult.Success) {
                    _ui.update { it.copy(outgoing = it.outgoing + user.id) }
                    _snackbar.emit("Friend request sent to ${user.username}")
                }
            }
        }
    }

    fun accept(request: FriendRequestDto) {
        val snapshot = _ui.value.incoming
        viewModelScope.launch {
            optimistic(
                applyLocal = {
                    _ui.update {
                        it.copy(incoming = it.incoming.filter { r -> r.requestId != request.requestId },
                            friends = it.friends + request.from)
                    }
                },
                remote = { friendRepo.accept(request.requestId) },
                rollback = {
                    _ui.update { it.copy(incoming = snapshot,
                        friends = it.friends.filter { f -> f.id != request.from.id }) }
                    _snackbar.emit("Accept failed — reverted")
                },
            ).also { r ->
                if (r is NetworkResult.Success && r.data.newAchievements.isNotEmpty()) {
                    _snackbar.emit("🏆 Unlocked: " + r.data.newAchievements.joinToString { it.name })
                    loadAll()
                }
            }
        }
    }

    fun decline(request: FriendRequestDto) {
        val snapshot = _ui.value.incoming
        viewModelScope.launch {
            optimistic(
                applyLocal = {
                    _ui.update { it.copy(incoming = it.incoming.filter { r -> r.requestId != request.requestId }) }
                },
                remote = { friendRepo.decline(request.requestId) },
                rollback = {
                    _ui.update { it.copy(incoming = snapshot) }
                    _snackbar.emit("Decline failed — reverted")
                }
            )
        }
    }

    /** Uploads the selected image to Cloudinary and updates the profile avatar. */
    fun uploadAvatar(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _ui.update { it.copy(avatarUploading = true) }
            try {
                // Copy URI to a temp file for Cloudinary
                val file = File.createTempFile("avatar_", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val result = cloudinaryUploader.uploadAndStylize(file, "COMMON") // Use common preset for avatars
                val updateResult = profileRepo.updateAvatar(result.secureUrl)
                if (updateResult is NetworkResult.Success) {
                    _ui.update { it.copy(profile = it.profile?.copy(avatarUrl = result.secureUrl)) }
                    _snackbar.emit("Avatar updated!")
                } else {
                    _snackbar.emit("Failed to save avatar")
                }
            } catch (e: Exception) {
                _snackbar.emit("Upload failed: ${e.message}")
            } finally {
                _ui.update { it.copy(avatarUploading = false) }
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            val r = profileRepo.updateAvatar("")   // backend treats "" as "remove"
            if (r is NetworkResult.Success) {
                _ui.update { it.copy(profile = it.profile?.copy(avatarUrl = null)) }
                _snackbar.emit("Avatar removed")
            } else {
                _snackbar.emit("Failed to remove avatar")
            }
        }
    }

    fun logout() = viewModelScope.launch { authRepo.logout() }
}

data class ProfileUiState(
    val profile: ProfileDto? = null,
    val friends: List<FriendDto> = emptyList(),
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: Set<Int> = emptySet(),
    val searchResults: List<UserSearchDto> = emptyList(),
    val pendingActions: Set<Int> = emptySet(),
    val searching: Boolean = false,
    val avatarUploading: Boolean = false,
)