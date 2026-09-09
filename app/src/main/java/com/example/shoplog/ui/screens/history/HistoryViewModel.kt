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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MonthGroup(
    val monthName: String,
    val totalCents: Long,
    val lists: List<ShoppingListWithItems>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ShoppingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val groupedLists: StateFlow<List<MonthGroup>> = repository.getSavedListsWithItemsFlow()
        .combine(_searchQuery) { lists, query ->
            val filtered = if (query.isBlank()) {
                lists
            } else {
                val q = query.trim().lowercase()
                lists.filter { itemWithList ->
                    itemWithList.list.title.lowercase().contains(q) ||
                            (itemWithList.list.location?.lowercase()?.contains(q) == true)
                }
            }

            val groupedMap = filtered.groupBy { itemWithList ->
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(itemWithList.list.createdAt))
            }

            groupedMap.entries.map { (_, monthLists) ->
                val monthName = if (monthLists.isNotEmpty()) {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthLists.first().list.createdAt))
                } else "Unknown"

                MonthGroup(
                    monthName = monthName,
                    totalCents = monthLists.sumOf { it.list.totalCents },
                    lists = monthLists
                )
            }.sortedByDescending { group ->
                group.lists.maxOfOrNull { it.list.createdAt } ?: 0L
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}
