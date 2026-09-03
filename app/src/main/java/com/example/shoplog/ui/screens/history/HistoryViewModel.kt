package com.example.shoplog.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.example.shoplog.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ShoppingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val savedLists: StateFlow<List<ShoppingListWithItems>> = repository.getSavedListsWithItemsFlow()
        .combine(_searchQuery) { lists, query ->
            if (query.isBlank()) {
                lists
            } else {
                val q = query.trim().lowercase()
                lists.filter { itemWithList ->
                    itemWithList.list.title.lowercase().contains(q) ||
                            (itemWithList.list.location?.lowercase()?.contains(q) == true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}
