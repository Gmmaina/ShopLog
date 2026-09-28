package com.example.shoplog.ui.screens.analytics

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class AnalyticsTimeFilter(val label: String, val days: Int) {
    SEVEN_DAYS("7D", 7),
    THIRTY_DAYS("30D", 30),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365)
}

data class MonthlyExpenditure(
    val monthYearKey: String,
    val displayMonth: String,
    val totalCents: Long,
    val listCount: Int,
    val lists: List<ShoppingListWithItems>
)

data class LineChartPoint(
    val label: String,
    val fullDateLabel: String,
    val totalCents: Long,
    val timestamp: Long
)

data class ShopExpenditure(
    val shopName: String,
    val totalCents: Long,
    val listCount: Int,
    val percentageOfMax: Float
)

data class LocationExpenditure(
    val locationName: String,
    val totalCents: Long,
    val listCount: Int
)

data class AnalyticsState(
    val selectedFilter: AnalyticsTimeFilter = AnalyticsTimeFilter.THIRTY_DAYS,
    val currentPeriodTotalCents: Long = 0L,
    val previousPeriodTotalCents: Long = 0L,
    val percentageChange: Double? = null,
    val periodLabel: String = "Last 30 days",
    val lineChartPoints: List<LineChartPoint> = emptyList(),
    val shopExpenditures: List<ShopExpenditure> = emptyList(),
    val locationExpenditures: List<LocationExpenditure> = emptyList(),
    val recentShoppingLists: List<ShoppingListWithItems> = emptyList(),
    val hasAnyCompletedShopping: Boolean = false
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: ShoppingRepository
) : ViewModel() {

    val currencySymbol: StateFlow<String> = repository.currencySymbol

    private val _selectedFilter = MutableStateFlow(AnalyticsTimeFilter.THIRTY_DAYS)
    val selectedFilter: StateFlow<AnalyticsTimeFilter> = _selectedFilter.asStateFlow()

    fun setTimeFilter(filter: AnalyticsTimeFilter) {
        _selectedFilter.value = filter
    }

    val state: StateFlow<AnalyticsState> = combine(
        repository.getSavedListsWithItemsFlow(),
        _selectedFilter
    ) { savedLists, filter ->
        val validLists = savedLists.filter { it.list.deletedAt == null && !it.list.isDraft }
        if (validLists.isEmpty()) {
            return@combine AnalyticsState(
                selectedFilter = filter,
                hasAnyCompletedShopping = false
            )
        }

        val now = System.currentTimeMillis()
        val filterMillis = filter.days * 24L * 60L * 60L * 1000L
        val currentPeriodStart = now - filterMillis
        val previousPeriodStart = currentPeriodStart - filterMillis

        val currentPeriodLists = validLists.filter { it.list.createdAt in currentPeriodStart..now }
        val previousPeriodLists = validLists.filter { it.list.createdAt in previousPeriodStart until currentPeriodStart }

        val currentTotalCents = currentPeriodLists.sumOf { it.list.totalCents }
        val previousTotalCents = previousPeriodLists.sumOf { it.list.totalCents }

        val percentageChange = if (previousTotalCents > 0L) {
            ((currentTotalCents - previousTotalCents).toDouble() / previousTotalCents.toDouble()) * 100.0
        } else null

        val periodLabel = when (filter) {
            AnalyticsTimeFilter.SEVEN_DAYS -> "Last 7 days"
            AnalyticsTimeFilter.THIRTY_DAYS -> "This month"
            AnalyticsTimeFilter.SIX_MONTHS -> "Last 6 months"
            AnalyticsTimeFilter.ONE_YEAR -> "Last 1 year"
        }

        // Line Chart Points Aggregation
        val lineChartPoints = buildLineChartPoints(filter, currentPeriodLists, currentPeriodStart, now)

        // Unified Location / Shop Expenditure Aggregation
        val locationMap = currentPeriodLists.groupBy { itemWithList ->
            val list = itemWithList.list
            val loc = list.location?.trim()
            if (!loc.isNullOrBlank()) {
                loc
            } else {
                val shp = list.shop?.trim()
                if (!shp.isNullOrBlank()) shp else "Others"
            }
        }

        val rawShopExps = locationMap.map { (displayName, lists) ->
            ShopExpenditure(
                shopName = displayName,
                totalCents = lists.sumOf { it.list.totalCents },
                listCount = lists.size,
                percentageOfMax = 0f
            )
        }.sortedWith(compareBy<ShopExpenditure> { it.shopName == "Others" }.thenByDescending { it.totalCents })

        val maxShopCents = rawShopExps.maxOfOrNull { it.totalCents }?.coerceAtLeast(1L) ?: 1L
        val shopExpenditures = rawShopExps.map {
            it.copy(percentageOfMax = (it.totalCents.toFloat() / maxShopCents.toFloat()).coerceIn(0.08f, 1.0f))
        }

        val locationExpenditures = rawShopExps.map {
            LocationExpenditure(
                locationName = it.shopName,
                totalCents = it.totalCents,
                listCount = it.listCount
            )
        }

        // Recent Shopping
        val recentLists = validLists.sortedByDescending { it.list.createdAt }.take(5)

        AnalyticsState(
            selectedFilter = filter,
            currentPeriodTotalCents = currentTotalCents,
            previousPeriodTotalCents = previousTotalCents,
            percentageChange = percentageChange,
            periodLabel = periodLabel,
            lineChartPoints = lineChartPoints,
            shopExpenditures = shopExpenditures,
            locationExpenditures = locationExpenditures,
            recentShoppingLists = recentLists,
            hasAnyCompletedShopping = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())

    val monthlyBreakdown: StateFlow<List<MonthlyExpenditure>> = repository.getSavedListsWithItemsFlow().map { lists ->
        val validLists = lists.filter { it.list.deletedAt == null && !it.list.isDraft }
        val groupedMap = validLists.groupBy { itemWithList ->
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

    private fun buildLineChartPoints(
        filter: AnalyticsTimeFilter,
        lists: List<ShoppingListWithItems>,
        startMillis: Long,
        endMillis: Long
    ): List<LineChartPoint> {
        return when (filter) {
            AnalyticsTimeFilter.SEVEN_DAYS, AnalyticsTimeFilter.THIRTY_DAYS -> {
                val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                val fullDateFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                val pointCount = if (filter == AnalyticsTimeFilter.SEVEN_DAYS) 7 else 8
                val intervalMillis = (endMillis - startMillis) / pointCount

                (0 until pointCount).map { i ->
                    val intervalStart = startMillis + (i * intervalMillis)
                    val intervalEnd = intervalStart + intervalMillis
                    val bucketLists = lists.filter { it.list.createdAt in intervalStart until intervalEnd }
                    val total = bucketLists.sumOf { it.list.totalCents }
                    val midTime = intervalStart + (intervalMillis / 2)
                    val dateObj = Date(midTime)

                    LineChartPoint(
                        label = dateFormat.format(dateObj),
                        fullDateLabel = fullDateFormat.format(dateObj),
                        totalCents = total,
                        timestamp = midTime
                    )
                }
            }
            AnalyticsTimeFilter.SIX_MONTHS, AnalyticsTimeFilter.ONE_YEAR -> {
                val monthCount = if (filter == AnalyticsTimeFilter.SIX_MONTHS) 6 else 12
                val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                val fullMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

                val points = mutableListOf<LineChartPoint>()
                for (i in (monthCount - 1) downTo 0) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MONTH, -i)
                    val monthKey = monthKeyFormat.format(cal.time)
                    val bucketLists = lists.filter {
                        monthKeyFormat.format(Date(it.list.createdAt)) == monthKey
                    }
                    val total = bucketLists.sumOf { it.list.totalCents }

                    points.add(
                        LineChartPoint(
                            label = monthFormat.format(cal.time),
                            fullDateLabel = fullMonthFormat.format(cal.time),
                            totalCents = total,
                            timestamp = cal.timeInMillis
                        )
                    )
                }
                points
            }
        }
    }
}
