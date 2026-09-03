package com.example.shoplog.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val isAnonymous: StateFlow<Boolean> = authRepository.currentUserState.map { user ->
        user?.isAnonymous ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currentEmail: StateFlow<String?> = authRepository.currentUserState.map { user ->
        user?.email
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _authSuccessEvent = MutableStateFlow(false)
    val authSuccessEvent: StateFlow<Boolean> = _authSuccessEvent.asStateFlow()

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.signInWithEmailPassword(email, pass)
            _isLoading.value = false
            result.onSuccess {
                _authSuccessEvent.value = true
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Sign in failed. Check email and password."
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.signUpWithEmailPassword(email, pass)
            _isLoading.value = false
            result.onSuccess {
                _authSuccessEvent.value = true
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Registration failed. Try a different email or password."
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.signInWithGoogleIdToken(idToken)
            _isLoading.value = false
            result.onSuccess {
                _authSuccessEvent.value = true
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Google SSO failed."
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun consumeSuccessEvent() {
        _authSuccessEvent.value = false
    }
}
