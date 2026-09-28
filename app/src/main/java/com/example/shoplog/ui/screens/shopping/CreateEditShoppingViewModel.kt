package com.example.shoplog.ui.screens.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.example.shoplog.data.model.Product
import com.example.shoplog.data.model.ProductLookupResult
import com.example.shoplog.data.repository.AuthRepository
import com.example.shoplog.data.repository.ProductRepository
import com.example.shoplog.data.repository.ShoppingRepository
import com.google.firebase.firestore.ListenerRegistration
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
    private val productRepository: ProductRepository,
    private val authRepository: AuthRepository,
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

    val currentUserId: String
        get() = authRepository.currentUserId

    private var listenerRegistration: ListenerRegistration? = null

    init {
        if (_currentListId.value == null) {
            viewModelScope.launch {
                val newId = repository.createNewDraft()
                _currentListId.value = newId
                startLiveSync(newId)
            }
        } else {
            _currentListId.value?.let { startLiveSync(it) }
        }
    }

    private fun startLiveSync(listId: String) {
        listenerRegistration?.remove()
        listenerRegistration = repository.attachLiveListListener(listId)
    }

    fun initList(listIdParam: String) {
        if (_currentListId.value != null && _currentListId.value == listIdParam) return
        viewModelScope.launch {
            val targetId = if (listIdParam == "new" || listIdParam.isBlank()) {
                repository.createNewDraft()
            } else {
                listIdParam
            }
            _currentListId.value = targetId
            startLiveSync(targetId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }

    fun updateListInfo(title: String, location: String?) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            runCatching {
                repository.updateListMetadata(listId, title, location)
            }
        }
    }

    fun addOrUpdateItem(
        itemId: String? = null,
        name: String,
        quantity: Int,
        unitPriceCents: Long,
        barcode: String? = null
    ) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            if (!barcode.isNullOrBlank()) {
                productRepository.saveAndSubmitProduct(Product(barcode = barcode, name = name))
            }
            runCatching {
                repository.addOrUpdateItem(listId, itemId, name, quantity, unitPriceCents, barcode)
            }
        }
    }

    suspend fun lookupBarcode(barcode: String): ProductLookupResult {
        return productRepository.findOrLookupProduct(barcode)
    }

    fun deleteItem(itemId: String) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            runCatching {
                repository.deleteItem(itemId, listId)
            }
        }
    }

    fun toggleItemPurchased(itemId: String, isPurchased: Boolean) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            runCatching {
                repository.toggleItemPurchased(itemId, listId, isPurchased)
            }
        }
    }

    fun endShopping(onEnded: () -> Unit, onError: (String) -> Unit) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            val result = repository.endShoppingSession(listId)
            result.onSuccess {
                onEnded()
            }.onFailure { ex ->
                onError(ex.message ?: "Failed to end shopping session.")
            }
        }
    }

    fun generateShareCode(onCodeGenerated: (code: String) -> Unit) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            val code = repository.generateShareCodeForList(listId)
            onCodeGenerated(code)
        }
    }

    fun saveShopping(title: String, location: String?, onSaved: (listId: String) -> Unit) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.saveShoppingList(listId, title, location)
            onSaved(listId)
        }
    }

    fun attachReceiptPhoto(photoPath: String?) {
        val listId = _currentListId.value ?: return
        viewModelScope.launch {
            repository.updateReceiptPhotoPath(listId, photoPath)
        }
    }
}
