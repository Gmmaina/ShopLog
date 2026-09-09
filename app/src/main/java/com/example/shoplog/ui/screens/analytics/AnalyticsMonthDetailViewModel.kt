package com.example.shoplog.ui.screens.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class AnalyticsMonthDetailViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val monthYearKey: String = savedStateHandle.get<String>("monthYearKey") ?: ""

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    val monthData: StateFlow<MonthlyExpenditure?> = repository.getSavedListsWithItemsFlow().map { lists ->
        val filtered = lists.filter { itemWithList ->
            val key = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(itemWithList.list.createdAt))
            key == monthYearKey || itemWithList.list.createdAt.let {
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it)).equals(monthYearKey, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) null
        else {
            val displayMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(filtered.first().list.createdAt))
            val total = filtered.sumOf { it.list.totalCents }
            MonthlyExpenditure(
                monthYearKey = monthYearKey,
                displayMonth = displayMonth,
                totalCents = total,
                listCount = filtered.size,
                lists = filtered
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
