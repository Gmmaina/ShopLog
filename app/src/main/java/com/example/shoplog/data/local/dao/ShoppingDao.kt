package com.example.shoplog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE isDraft = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getSavedListsWithItemsFlow(): Flow<List<ShoppingListWithItems>>

    @Query("SELECT * FROM shopping_lists WHERE id = :listId AND deletedAt IS NULL")
    fun getListByIdFlow(listId: String): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE id = :listId AND deletedAt IS NULL")
    suspend fun getListByIdOnce(listId: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_items WHERE shoppingListId = :listId AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun getItemsForListFlow(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE shoppingListId = :listId AND deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getItemsForListOnce(listId: String): List<ShoppingItemEntity>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE id = :listId AND deletedAt IS NULL")
    fun getListWithItemsFlow(listId: String): Flow<List<ShoppingListWithItems>>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE id = :listId AND deletedAt IS NULL")
    suspend fun getListWithItemsOnce(listId: String): List<ShoppingListWithItems>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE isDraft = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    fun getLatestDraftListFlow(): Flow<List<ShoppingListWithItems>>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE isDraft = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestDraftListOnce(): List<ShoppingListWithItems>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE isDraft = 0 AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    fun getLatestSavedListFlow(): Flow<List<ShoppingListWithItems>>

    @Query("SELECT COUNT(*) FROM shopping_lists WHERE isDraft = 0 AND deletedAt IS NULL")
    fun getSavedListsCountFlow(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedListsOnce(): List<ShoppingListWithItems>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(list: ShoppingListEntity): Long

    @Update
    suspend fun updateList(list: ShoppingListEntity): Int

    @Transaction
    suspend fun insertOrUpdateList(list: ShoppingListEntity) {
        val rowId = insertList(list)
        if (rowId == -1L) {
            updateList(list)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: ShoppingItemEntity): Long

    @Update
    suspend fun updateItem(item: ShoppingItemEntity): Int

    @Transaction
    suspend fun insertOrUpdateItem(item: ShoppingItemEntity) {
        val rowId = insertItem(item)
        if (rowId == -1L) {
            updateItem(item)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<ShoppingItemEntity>): LongArray

    @Update
    suspend fun updateItems(items: List<ShoppingItemEntity>): Int

    @Transaction
    suspend fun insertOrUpdateItems(items: List<ShoppingItemEntity>) {
        val rowIds = insertItems(items)
        val itemsToUpdate = items.filterIndexed { index, _ -> rowIds[index] == -1L }
        if (itemsToUpdate.isNotEmpty()) {
            updateItems(itemsToUpdate)
        }
    }

    @Query("DELETE FROM shopping_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String): Int

    @Query("DELETE FROM shopping_items WHERE shoppingListId = :listId")
    suspend fun deleteItemsForList(listId: String): Int

    @Query("UPDATE shopping_lists SET deletedAt = :timestamp, syncStatus = 'PENDING_DELETE' WHERE id = :listId")
    suspend fun softDeleteList(listId: String, timestamp: Long): Int

    @Query("UPDATE shopping_lists SET ownerId = :newOwnerId, syncStatus = 'PENDING_UPDATE' WHERE ownerId = :previousOwnerId OR ownerId = 'offline_user'")
    suspend fun reassignListOwner(previousOwnerId: String, newOwnerId: String): Int

    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun hardDeleteList(listId: String): Int

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAllLists()

    @Transaction
    suspend fun clearAllData() {
        deleteAllItems()
        deleteAllLists()
    }
}
