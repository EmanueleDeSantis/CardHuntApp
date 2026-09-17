package com.cardhunt.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.local.Session
import com.cardhunt.app.data.local.ThemeStore
import com.cardhunt.app.data.repository.AuthRepository
import com.cardhunt.app.push.FcmTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    themeStore: ThemeStore,
) : ViewModel() {

    val session = authRepo.session
    val themeMode: Flow<String> = themeStore.mode
    fun snapshot(): Session? = authRepo.snapshot()

    private val _ui = MutableStateFlow(AuthUiState())
    val ui = _ui.asStateFlow()

    fun toggleMode() = _ui.update { it.copy(isRegisterMode = !it.isRegisterMode, error = null) }

    fun submit(username: String, email: String, password: String) {
        if (username.isBlank() || password.isBlank() ||
            (_ui.value.isRegisterMode && email.isBlank())) {
            _ui.update { it.copy(error = "Please fill in all fields") }; return
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (_ui.value.isRegisterMode)
                authRepo.register(username.trim(), email.trim(), password)
            else
                authRepo.login(username.trim(), password)
            when (result) {
                is NetworkResult.Success -> {
                    _ui.update { it.copy(loading = false) }
                    // Register FCM token after successful login/registration
                    val token = FcmTokenManager.getFreshToken()
                    if (token != null) {
                        authRepo.registerFcmToken(token)
                    }
                }
                is NetworkResult.Error -> _ui.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun logout() = viewModelScope.launch { authRepo.logout() }
}