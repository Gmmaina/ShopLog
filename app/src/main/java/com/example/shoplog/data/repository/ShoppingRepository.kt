package com.example.shoplog.data.repository

import android.content.Context
import com.example.shoplog.core.model.SyncStatus
import com.example.shoplog.core.util.Money
import com.example.shoplog.core.util.NetworkMonitor
import com.example.shoplog.core.util.ShareCodeGenerator
import com.example.shoplog.data.local.dao.ShoppingDao
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.example.shoplog.data.remote.FirebaseSyncManager
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class SharedListResult {
    data class LiveSession(val listId: String) : SharedListResult()
    data class CompletedReceipt(val listId: String) : SharedListResult()
}

@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val firebaseSyncManager: FirebaseSyncManager,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    @param:ApplicationContext private val context: Context
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isOnline: Flow<Boolean> = networkMonitor.isOnline

    init {
        repositoryScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    val uid = authRepository.currentUserId
                    if (uid.isNotBlank() && uid != "offline_user" && !authRepository.isAnonymous) {
                        runCatching { firebaseSyncManager.fetchAndSyncRemoteUserLists(uid) }
                    }
                    runCatching { firebaseSyncManager.syncUnsyncedLists() }
                }
            }
        }
    }
    private val prefs = context.getSharedPreferences("shoplog_prefs", Context.MODE_PRIVATE)

    private val _currencySymbol = MutableStateFlow(prefs.getString("currency_symbol", "KSh") ?: "KSh")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setCurrencySymbol(symbol: String) {
        val clean = symbol.trim().ifEmpty { "KSh" }
        prefs.edit().putString("currency_symbol", clean).apply()
        _currencySymbol.value = clean
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun getSavedListsWithItemsFlow(): Flow<List<ShoppingListWithItems>> {
        return shoppingDao.getSavedListsWithItemsFlow()
    }

    fun getActiveLiveListsFlow(): Flow<List<ShoppingListWithItems>> {
        return shoppingDao.getActiveLiveListsFlow()
    }

    fun getListWithItemsFlow(listId: String): Flow<ShoppingListWithItems?> {
        return shoppingDao.getListByIdFlow(listId)
            .combine(shoppingDao.getItemsForListFlow(listId)) { list, items ->
                if (list == null) null
                else ShoppingListWithItems(list = list, items = items)
            }
    }

    suspend fun getListWithItemsOnce(listId: String): ShoppingListWithItems? {
        val list = shoppingDao.getListByIdOnce(listId) ?: return null
        val items = shoppingDao.getItemsForListOnce(listId)
        return ShoppingListWithItems(list = list, items = items)
    }

    fun getLatestDraftListFlow(): Flow<ShoppingListWithItems?> {
        return shoppingDao.getLatestDraftListFlow().map { it.firstOrNull() }
    }

    fun getLatestSavedListFlow(): Flow<ShoppingListWithItems?> {
        return shoppingDao.getLatestSavedListFlow().map { it.firstOrNull() }
    }

    fun getSavedListsCountFlow(): Flow<Int> {
        return shoppingDao.getSavedListsCountFlow()
    }

    /**
     * Attaches real-time listener for live collaborative shopping.
     * Updates Room local database immediately upon remote changes.
     */
    fun attachLiveListListener(listId: String): ListenerRegistration? {
        return firebaseSyncManager.attachLiveListListener(listId) { updatedData ->
            repositoryScope.launch {
                shoppingDao.insertOrUpdateList(updatedData.list)
                shoppingDao.insertOrUpdateItems(updatedData.items)
            }
        }
    }

    suspend fun createNewDraft(title: String = "New Shopping", location: String? = null): String {
        val listId = UUID.randomUUID().toString()
        val ownerId = authRepository.currentUserId
        val now = System.currentTimeMillis()

        val draftList = ShoppingListEntity(
            id = listId,
            ownerId = ownerId,
            title = title,
            location = location,
            totalCents = 0L,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_CREATE,
            isDraft = true,
            status = ShoppingListEntity.STATUS_ACTIVE
        )
        shoppingDao.insertOrUpdateList(draftList)
        return listId
    }

    suspend fun updateListMetadata(listId: String, title: String, location: String?) {
        val list = shoppingDao.getListByIdOnce(listId) ?: return
        val currentUid = authRepository.currentUserId

        // Enforce completed read-only restriction for members
        if (list.isCompleted && list.ownerId != currentUid && list.isSharedWithMe) {
            throw IllegalStateException("Completed shopping lists cannot be edited by invited participants.")
        }

        val items = shoppingDao.getItemsForListOnce(listId)
        val currentTotal = items.sumOf { it.subtotalCents }
        val updatedList = list.copy(
            title = title.ifBlank { "Untitled Shopping" },
            location = location,
            totalCents = currentTotal,
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (list.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else list.syncStatus
        )
        shoppingDao.insertOrUpdateList(updatedList)

        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
    }

    suspend fun updateReceiptPhotoPath(listId: String, photoPath: String?) {
        val list = shoppingDao.getListByIdOnce(listId) ?: return
        val updatedList = list.copy(
            receiptPhotoPath = photoPath,
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (list.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else list.syncStatus
        )
        shoppingDao.insertOrUpdateList(updatedList)
    }

    suspend fun addOrUpdateItem(
        listId: String,
        itemId: String? = null,
        name: String,
        quantity: Int,
        unitPriceCents: Long,
        barcode: String? = null
    ) {
        val list = shoppingDao.getListByIdOnce(listId)
        val currentUid = authRepository.currentUserId

        if (list != null && list.isCompleted && list.ownerId != currentUid && list.isSharedWithMe) {
            throw IllegalStateException("Completed shopping lists cannot be edited by invited participants.")
        }

        if (list == null) {
            val ownerId = authRepository.currentUserId
            val now = System.currentTimeMillis()
            val newList = ShoppingListEntity(
                id = listId,
                ownerId = ownerId,
                title = "New Shopping",
                totalCents = 0L,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING_CREATE,
                isDraft = true,
                status = ShoppingListEntity.STATUS_ACTIVE
            )
            shoppingDao.insertOrUpdateList(newList)
        }

        val now = System.currentTimeMillis()
        val subtotalCents = Money.calculateSubtotal(quantity, unitPriceCents)
        val targetItemId = itemId ?: UUID.randomUUID().toString()

        val itemEntity = ShoppingItemEntity(
            id = targetItemId,
            shoppingListId = listId,
            name = name.ifBlank { "Item" },
            quantity = quantity.coerceAtLeast(1),
            unitPriceCents = unitPriceCents.coerceAtLeast(0L),
            subtotalCents = subtotalCents,
            barcode = barcode?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        shoppingDao.insertOrUpdateItem(itemEntity)
        recalculateAndSaveListTotal(listId)

        // Trigger background sync if online
        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
    }

    suspend fun toggleItemPurchased(itemId: String, listId: String, isPurchased: Boolean) {
        val items = shoppingDao.getItemsForListOnce(listId)
        val item = items.find { it.id == itemId } ?: return

        val updatedItem = item.copy(
            isPurchased = isPurchased,
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (item.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else item.syncStatus
        )
        shoppingDao.insertOrUpdateItem(updatedItem)

        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
    }

    suspend fun deleteItem(itemId: String, listId: String) {
        val list = shoppingDao.getListByIdOnce(listId)
        val currentUid = authRepository.currentUserId

        if (list != null && list.isCompleted && list.ownerId != currentUid && list.isSharedWithMe) {
            throw IllegalStateException("Completed shopping lists cannot be edited by invited participants.")
        }

        shoppingDao.deleteItem(itemId)
        recalculateAndSaveListTotal(listId)

        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
    }

    private suspend fun recalculateAndSaveListTotal(listId: String) {
        val list = shoppingDao.getListByIdOnce(listId) ?: return
        val items = shoppingDao.getItemsForListOnce(listId)
        val newTotal = items.sumOf { it.subtotalCents }
        val updatedList = list.copy(
            totalCents = newTotal,
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (list.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else list.syncStatus
        )
        shoppingDao.insertOrUpdateList(updatedList)
    }

    suspend fun saveShoppingList(listId: String, title: String, location: String?): Result<String> {
        val listWithItems = shoppingDao.getListWithItemsOnce(listId).firstOrNull()
            ?: return Result.failure(Exception("Shopping list not found"))

        val now = System.currentTimeMillis()
        val newTotal = listWithItems.items.filter { it.deletedAt == null }.sumOf { it.subtotalCents }

        val savedList = listWithItems.list.copy(
            title = title.ifBlank { "New Shopping" },
            location = location,
            totalCents = newTotal,
            updatedAt = now,
            isDraft = false,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        shoppingDao.insertOrUpdateList(savedList)

        // Non-blocking background sync attempt if connected
        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }

        return Result.success(listId)
    }

    /**
     * Ends live shopping session. Only list owner can perform this action.
     * Transitions status to COMPLETED and syncs change.
     */
    suspend fun endShoppingSession(listId: String): Result<Unit> {
        val list = shoppingDao.getListByIdOnce(listId) ?: return Result.failure(Exception("List not found"))
        val currentUid = authRepository.currentUserId

        if (list.ownerId.isNotBlank() && currentUid.isNotBlank() && list.ownerId != currentUid && list.isSharedWithMe) {
            return Result.failure(Exception("Only the shopping list creator can end the shopping session."))
        }

        val now = System.currentTimeMillis()
        val completedList = list.copy(
            status = ShoppingListEntity.STATUS_COMPLETED,
            completedAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        shoppingDao.insertOrUpdateList(completedList)

        val remoteResult = firebaseSyncManager.endShoppingSession(listId)
        if (remoteResult.isSuccess) {
            shoppingDao.insertOrUpdateList(completedList.copy(syncStatus = SyncStatus.SYNCED))
        }

        return Result.success(Unit)
    }

    suspend fun softDeleteList(listId: String) {
        shoppingDao.softDeleteList(listId, System.currentTimeMillis())
        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
    }

    suspend fun reassignListOwner(previousOwnerId: String, newOwnerId: String) {
        shoppingDao.reassignListOwner(previousOwnerId, newOwnerId)
        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
    }

    suspend fun generateShareCodeForList(listId: String): String {
        val listWithItems = shoppingDao.getListWithItemsOnce(listId).firstOrNull() ?: return ""
        val list = listWithItems.list

        // Legacy / completed list safety check: if list is completed or not a draft, ensure status is set to COMPLETED
        val targetStatus = if (!list.isDraft && list.status != ShoppingListEntity.STATUS_ACTIVE) {
            ShoppingListEntity.STATUS_COMPLETED
        } else {
            list.status
        }

        val existingCode = list.shareCode
        if (!existingCode.isNullOrEmpty()) {
            val updatedList = list.copy(
                status = targetStatus,
                syncStatus = SyncStatus.PENDING_UPDATE
            )
            shoppingDao.insertOrUpdateList(updatedList)
            if (networkMonitor.isOnlineNow()) {
                repositoryScope.launch { runCatching { firebaseSyncManager.syncUnsyncedLists() } }
            }
            return existingCode
        }

        val newCode = ShareCodeGenerator.generateCode()
        val updatedList = list.copy(
            shareCode = newCode,
            status = targetStatus,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        shoppingDao.insertOrUpdateList(updatedList)
        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }
        return newCode
    }

    private val _sharedPreviewList = MutableStateFlow<ShoppingListWithItems?>(null)
    val sharedPreviewList: StateFlow<ShoppingListWithItems?> = _sharedPreviewList.asStateFlow()

    /**
     * Joins or retrieves a list by code, returning a [SharedListResult]
     * that distinguishes active live sessions from static completed receipts.
     */
    suspend fun retrieveSharedListByCode(code: String): Result<SharedListResult> {
        val result = firebaseSyncManager.joinCollaborativeListByCode(code)
        return result.map { itemWithList ->
            val currentUid = authRepository.currentUserId
            val existingLocalList = shoppingDao.getListByIdOnce(itemWithList.list.id)

            val isMyOwnList = (itemWithList.list.ownerId == currentUid && currentUid != "offline_user" && currentUid.isNotBlank())
                    || (existingLocalList != null && !existingLocalList.isSharedWithMe)

            val isCompleted = itemWithList.list.isCompleted || itemWithList.list.status == ShoppingListEntity.STATUS_COMPLETED

            val savedEntity = itemWithList.list.copy(
                ownerId = if (itemWithList.list.ownerId.isBlank()) currentUid else itemWithList.list.ownerId,
                isSharedWithMe = !isMyOwnList,
                status = if (isCompleted) ShoppingListEntity.STATUS_COMPLETED else ShoppingListEntity.STATUS_ACTIVE,
                syncStatus = SyncStatus.SYNCED
            )

            shoppingDao.insertOrUpdateList(savedEntity)
            shoppingDao.insertOrUpdateItems(itemWithList.items)
            _sharedPreviewList.value = null

            if (isCompleted) {
                SharedListResult.CompletedReceipt(savedEntity.id)
            } else {
                SharedListResult.LiveSession(savedEntity.id)
            }
        }
    }

    suspend fun saveSharedListAsCopy(sharedWithItems: ShoppingListWithItems): String {
        val newListId = UUID.randomUUID().toString()
        val newOwnerId = authRepository.currentUserId
        val now = System.currentTimeMillis()

        val newList = ShoppingListEntity(
            id = newListId,
            ownerId = newOwnerId,
            title = sharedWithItems.list.title,
            location = sharedWithItems.list.location,
            totalCents = sharedWithItems.list.totalCents,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_CREATE,
            isSharedWithMe = false,
            shareCode = null,
            isDraft = false,
            status = ShoppingListEntity.STATUS_ACTIVE
        )

        val newItems = sharedWithItems.items.map { oldItem ->
            ShoppingItemEntity(
                id = UUID.randomUUID().toString(),
                shoppingListId = newListId,
                name = oldItem.name,
                quantity = oldItem.quantity,
                unitPriceCents = oldItem.unitPriceCents,
                subtotalCents = oldItem.subtotalCents,
                barcode = oldItem.barcode,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING_CREATE
            )
        }

        shoppingDao.insertOrUpdateList(newList)
        shoppingDao.insertOrUpdateItems(newItems)

        _sharedPreviewList.value = null

        if (networkMonitor.isOnlineNow()) {
            repositoryScope.launch {
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }
        }

        return newListId
    }

    fun clearSharedPreview() {
        _sharedPreviewList.value = null
    }

    suspend fun syncUnsyncedLists(): Result<Unit> {
        val uid = authRepository.currentUserId
        if (uid.isNotBlank() && uid != "offline_user" && !authRepository.isAnonymous) {
            runCatching { firebaseSyncManager.fetchAndSyncRemoteUserLists(uid) }
        }
        return firebaseSyncManager.syncUnsyncedLists()
    }
}
