package com.example.shoplog.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.example.shoplog.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ShoppingRepository
) : ViewModel() {

    val savedListsCount: StateFlow<Int> = repository.getSavedListsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val latestSavedList: StateFlow<ShoppingListWithItems?> = repository.getLatestSavedListFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestDraftList: StateFlow<ShoppingListWithItems?> = repository.getLatestDraftListFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    val isOnline: StateFlow<Boolean> = repository.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _retrieveMessage = MutableStateFlow<String?>(null)
    val retrieveMessage: StateFlow<String?> = _retrieveMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun createNewDraft(onCreated: (listId: String) -> Unit) {
        viewModelScope.launch {
            val listId = repository.createNewDraft()
            onCreated(listId)
        }
    }

    fun retrieveSharedList(code: String, onSuccess: (listId: String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _retrieveMessage.value = null
            val result = repository.retrieveSharedListByCode(code)
            _isLoading.value = false
            result.onSuccess { targetListId ->
                onSuccess(targetListId)
            }.onFailure { exception ->
                _retrieveMessage.value = exception.message ?: "Failed to retrieve shopping list."
            }
        }
    }

    fun clearRetrieveMessage() {
        _retrieveMessage.value = null
    }
}
