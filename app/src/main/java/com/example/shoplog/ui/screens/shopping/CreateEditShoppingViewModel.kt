package com.example.shoplog.ui.screens.shopping

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

@HiltViewModel
class CreateEditShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val navListId: String? = savedStateHandle.get<String>("listId")

    private val _currentListId = MutableStateFlow<String?>(
        if (!navListId.isNullOrBlank() && navListId != "new") navListId else null
    )
    val currentListId: StateFlow<String?> = _currentListId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val shoppingListWithItems: StateFlow<ShoppingListWithItems?> = _currentListId.flatMapLatest { listId ->
        if (listId.isNullOrEmpty()) flowOf(null) else repository.getListWithItemsFlow(listId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    init {
        if (_currentListId.value == null) {
            viewModelScope.launch {
                val newId = repository.createNewDraft()
                _currentListId.value = newId
            }
        }
    }

    fun initList(listIdParam: String) {
        if (_currentListId.value != null) return
        viewModelScope.launch {
            if (listIdParam == "new" || listIdParam.isBlank()) {
                val newId = repository.createNewDraft()
                _currentListId.value = newId
            } else {
                _currentListId.value = listIdParam
            }
        }
    }

    fun updateListInfo(title: String, location: String?) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.updateListMetadata(listId, title, location)
        }
    }

    fun addOrUpdateItem(itemId: String? = null, name: String, quantity: Int, unitPriceCents: Long) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.addOrUpdateItem(listId, itemId, name, quantity, unitPriceCents)
        }
    }

    fun deleteItem(itemId: String) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.deleteItem(itemId, listId)
        }
    }

    fun saveShopping(title: String, location: String?, onSaved: (listId: String) -> Unit) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.saveShoppingList(listId, title, location)
            onSaved(listId)
        }
    }
}
