package com.example.shoplog.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.example.shoplog.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MonthlyExpenditure(
    val monthYearKey: String,
    val displayMonth: String,
    val totalCents: Long,
    val listCount: Int,
    val lists: List<ShoppingListWithItems>
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: ShoppingRepository
) : ViewModel() {

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    val currentMonthName: String
        get() = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

    val currentMonthTotalCents: StateFlow<Long> = repository.getSavedListsWithItemsFlow().map { lists ->
        val currentKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        lists.filter { itemWithList ->
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(itemWithList.list.createdAt))
            monthKey == currentKey
        }.sumOf { it.list.totalCents }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val currentMonthListCount: StateFlow<Int> = repository.getSavedListsWithItemsFlow().map { lists ->
        val currentKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        lists.count { itemWithList ->
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(itemWithList.list.createdAt))
            monthKey == currentKey
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val grandTotalCents: StateFlow<Long> = repository.getSavedListsWithItemsFlow().map { lists ->
        lists.sumOf { it.list.totalCents }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val grandListCount: StateFlow<Int> = repository.getSavedListsWithItemsFlow().map { lists ->
        lists.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val monthlyBreakdown: StateFlow<List<MonthlyExpenditure>> = repository.getSavedListsWithItemsFlow().map { lists ->
        val groupedMap = lists.groupBy { itemWithList ->
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(itemWithList.list.createdAt))
        }

        groupedMap.entries.map { (yearMonth, monthLists) ->
            val displayMonth = if (monthLists.isNotEmpty()) {
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthLists.first().list.createdAt))
            } else yearMonth

            MonthlyExpenditure(
                monthYearKey = yearMonth,
                displayMonth = displayMonth,
                totalCents = monthLists.sumOf { it.list.totalCents },
                listCount = monthLists.size,
                lists = monthLists
            )
        }.sortedByDescending { it.monthYearKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
