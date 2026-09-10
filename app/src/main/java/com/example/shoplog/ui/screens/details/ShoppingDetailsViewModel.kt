package com.example.shoplog.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.example.shoplog.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.map

@HiltViewModel
class ShoppingDetailsViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val navListId: String? = savedStateHandle.get<String>("listId")

    private val _currentListId = MutableStateFlow<String?>(navListId)
    val currentListId: StateFlow<String?> = _currentListId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val shoppingListWithItems: StateFlow<ShoppingListWithItems?> = _currentListId.flatMapLatest { listId ->
        when (listId) {
            "shared" -> repository.sharedPreviewList
            null, "" -> flowOf(null)
            else -> repository.getListWithItemsFlow(listId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isSharedPreview: StateFlow<Boolean> = _currentListId.flatMapLatest { listId ->
        if (listId == "shared") flowOf(true)
        else repository.getListWithItemsFlow(listId ?: "").map { itemWithList ->
            itemWithList?.list?.isSharedWithMe == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    private val _shareCode = MutableStateFlow<String?>(null)
    val shareCode: StateFlow<String?> = _shareCode.asStateFlow()

    fun initListId(listId: String) {
        _currentListId.value = listId
    }

    fun generateShareCode(onCodeGenerated: (code: String) -> Unit) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            val code = repository.generateShareCodeForList(listId)
            _shareCode.value = code
            onCodeGenerated(code)
        }
    }

    fun deleteList(onDeleted: () -> Unit) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.softDeleteList(listId)
            onDeleted()
        }
    }

    fun saveSharedListAsCopy(onSaved: (newListId: String) -> Unit) {
        val currentData = shoppingListWithItems.value ?: return
        viewModelScope.launch {
            val newListId = repository.saveSharedListAsCopy(currentData)
            onSaved(newListId)
        }
    }

    fun attachReceiptPhoto(photoPath: String?) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.updateReceiptPhotoPath(listId, photoPath)
        }
    }
}
