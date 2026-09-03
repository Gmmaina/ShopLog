package com.example.shoplog.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.repository.AuthRepository
import com.example.shoplog.data.repository.ShoppingRepository
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
class SettingsViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val currencySymbol: StateFlow<String> = shoppingRepository.currencySymbol
    val themeMode: StateFlow<String> = shoppingRepository.themeMode

    val isAnonymousState: StateFlow<Boolean> = authRepository.currentUserState.map { user ->
        user?.isAnonymous ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val userEmailState: StateFlow<String?> = authRepository.currentUserState.map { user ->
        user?.email
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun setCurrencySymbol(symbol: String) {
        shoppingRepository.setCurrencySymbol(symbol)
    }

    fun setThemeMode(mode: String) {
        shoppingRepository.setThemeMode(mode)
    }

    fun triggerSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            val result = shoppingRepository.syncUnsyncedLists()
            _isSyncing.value = false
            result.onSuccess {
                _syncMessage.value = "All changes synced successfully."
            }.onFailure { ex ->
                _syncMessage.value = ex.message ?: "Sync failed. Working in offline mode."
            }
        }
    }

    fun signInWithEmail(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authRepository.signInWithEmailPassword(email, pass)
            _isAuthLoading.value = false
            result.onSuccess {
                _syncMessage.value = "Signed in successfully! Local data synced."
                onSuccess()
            }.onFailure { ex ->
                _authError.value = ex.message ?: "Sign in failed. Check your credentials."
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authRepository.signUpWithEmailPassword(email, pass)
            _isAuthLoading.value = false
            result.onSuccess {
                _syncMessage.value = "Account created and guest lists linked!"
                onSuccess()
            }.onFailure { ex ->
                _authError.value = ex.message ?: "Registration failed."
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authRepository.signInWithGoogleIdToken(idToken)
            _isAuthLoading.value = false
            result.onSuccess {
                _syncMessage.value = "Signed in with Google! Local data linked."
                onSuccess()
            }.onFailure { ex ->
                _authError.value = ex.message ?: "Google Sign-In failed."
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _syncMessage.value = "Signed out. Switched to Guest Mode."
        }
    }

    fun setAuthError(message: String) {
        _authError.value = message
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
