package com.example.shoplog.data.remote

import android.util.Log
import com.example.shoplog.core.model.SyncStatus
import com.example.shoplog.data.local.dao.ShoppingDao
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles background synchronization between Room local database and Cloud Firestore.
 * Gracefully operates offline when internet is unavailable or Firebase is unconfigured.
 */
@Singleton
class FirebaseSyncManager @Inject constructor(
    private val shoppingDao: ShoppingDao
) {
    private val tag = "FirebaseSyncManager"

    private val auth: FirebaseAuth?
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    /**
     * Attempts a Firestore operation across possible database instances ("default" vs default instance)
     * to gracefully handle custom or regional database identifiers.
     */
    private suspend fun performFirestoreOp(op: suspend (FirebaseFirestore) -> Unit) {
        val instances = mutableListOf<FirebaseFirestore>()
        runCatching { FirebaseFirestore.getInstance("default") }.getOrNull()?.let { instances.add(it) }
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()?.let { instances.add(it) }

        var lastException: Exception? = null
        for (db in instances.distinct()) {
            try {
                op(db)
                return
            } catch (e: Exception) {
                lastException = e
                if (e.message?.contains("NOT_FOUND", ignoreCase = true) == true ||
                    e.message?.contains("does not exist", ignoreCase = true) == true) {
                    Log.w(tag, "Firestore database instance failed with NOT_FOUND, trying next instance...", e)
                    continue
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Firebase Firestore instance unavailable.")
    }

    /**
     * Uploads local unsynced lists to Firestore.
     */
    suspend fun syncUnsyncedLists(): Result<Unit> {
        var currentUserId = auth?.currentUser?.uid ?: ""
        if (currentUserId.isBlank()) {
            runCatching {
                auth?.signInAnonymously()?.await()
            }
            currentUserId = auth?.currentUser?.uid ?: "local_user"
        }

        return try {
            val unsynced = shoppingDao.getUnsyncedListsOnce()
            if (unsynced.isEmpty()) {
                return Result.success(Unit)
            }

            for (itemWithList in unsynced) {
                val list = itemWithList.list
                val items = itemWithList.items

                val targetOwnerId = if (list.ownerId.isBlank() || list.ownerId == "offline_user" || list.ownerId == "local_user") {
                    currentUserId
                } else {
                    list.ownerId
                }

                performFirestoreOp { db ->
                    if (list.deletedAt != null || list.syncStatus == SyncStatus.PENDING_DELETE) {
                        db.collection("shopping_lists").document(list.id).delete().await()
                    } else {
                        val listMap = hashMapOf<String, Any?>(
                            "id" to list.id,
                            "ownerId" to targetOwnerId,
                            "title" to list.title,
                            "location" to list.location,
                            "totalCents" to list.totalCents,
                            "createdAt" to list.createdAt,
                            "updatedAt" to list.updatedAt,
                            "shareCode" to list.shareCode,
                            "isDraft" to list.isDraft,
                            "items" to items.map { item ->
                                hashMapOf(
                                    "id" to item.id,
                                    "name" to item.name,
                                    "quantity" to item.quantity,
                                    "unitPriceCents" to item.unitPriceCents,
                                    "subtotalCents" to item.subtotalCents,
                                    "createdAt" to item.createdAt,
                                    "updatedAt" to item.updatedAt
                                )
                            }
                        )

                        db.collection("shopping_lists").document(list.id).set(listMap).await()

                        // Also store share code mapping if present
                        list.shareCode?.let { code ->
                            db.collection("share_codes").document(code.uppercase()).set(
                                hashMapOf(
                                    "code" to code.uppercase(),
                                    "shoppingListId" to list.id,
                                    "ownerId" to targetOwnerId,
                                    "createdAt" to list.createdAt
                                )
                            ).await()
                        }
                    }
                }

                // Update Room status to SYNCED
                if (list.deletedAt != null || list.syncStatus == SyncStatus.PENDING_DELETE) {
                    shoppingDao.hardDeleteList(list.id)
                } else {
                    val syncedList = list.copy(ownerId = targetOwnerId, syncStatus = SyncStatus.SYNCED)
                    val syncedItems = items.map { it.copy(syncStatus = SyncStatus.SYNCED) }
                    shoppingDao.insertOrUpdateList(syncedList)
                    shoppingDao.insertOrUpdateItems(syncedItems)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads remote shopping lists owned by [userId] from Firestore
     * and caches them into Room local database.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchAndSyncRemoteUserLists(userId: String): Result<Unit> {
        if (userId.isBlank() || userId == "offline_user" || userId == "local_user") {
            return Result.success(Unit)
        }

        return try {
            performFirestoreOp { db ->
                val querySnapshot = db.collection("shopping_lists")
                    .whereEqualTo("ownerId", userId)
                    .get()
                    .await()

                for (doc in querySnapshot.documents) {
                    val listId = doc.id
                    val title = doc.getString("title") ?: "Shopping List"
                    val location = doc.getString("location")
                    val totalCents = doc.getLong("totalCents") ?: 0L
                    val ownerId = doc.getString("ownerId") ?: userId
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    val shareCode = doc.getString("shareCode")
                    val isDraft = doc.getBoolean("isDraft") ?: false

                    val rawItems = (doc.get("items") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                    val itemsList = rawItems.map { itemMap ->
                        ShoppingItemEntity(
                            id = itemMap["id"] as? String ?: UUID.randomUUID().toString(),
                            shoppingListId = listId,
                            name = itemMap["name"] as? String ?: "Item",
                            quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 1,
                            unitPriceCents = itemMap["unitPriceCents"] as? Long ?: 0L,
                            subtotalCents = itemMap["subtotalCents"] as? Long ?: 0L,
                            createdAt = itemMap["createdAt"] as? Long ?: System.currentTimeMillis(),
                            updatedAt = itemMap["updatedAt"] as? Long ?: System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED
                        )
                    }

                    val listEntity = ShoppingListEntity(
                        id = listId,
                        ownerId = ownerId,
                        title = title,
                        location = location,
                        totalCents = totalCents,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncStatus = SyncStatus.SYNCED,
                        isSharedWithMe = false,
                        shareCode = shareCode,
                        isDraft = isDraft
                    )

                    shoppingDao.insertOrUpdateList(listEntity)
                    shoppingDao.insertOrUpdateItems(itemsList)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Fetch remote user lists failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves a shared shopping list from Firestore using its short code (e.g. `AUG123D`)
     * and caches it locally into Room database.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun retrieveSharedListByCode(code: String): Result<ShoppingListWithItems> {
        val cleanCode = code.trim().uppercase()
        var resultListWithItems: ShoppingListWithItems? = null

        return try {
            performFirestoreOp { db ->
                val codeDoc = db.collection("share_codes").document(cleanCode).get().await()
                if (!codeDoc.exists()) {
                    throw Exception("Invalid or expired shopping code: $cleanCode")
                }

                val shoppingListId = codeDoc.getString("shoppingListId")
                    ?: throw Exception("Shopping list ID not found for code $cleanCode")

                val listDoc = db.collection("shopping_lists").document(shoppingListId).get().await()
                if (!listDoc.exists()) {
                    throw Exception("Shared shopping list no longer exists.")
                }

                val title = listDoc.getString("title") ?: "Shared Shopping"
                val location = listDoc.getString("location")
                val totalCents = listDoc.getLong("totalCents") ?: 0L
                val ownerId = listDoc.getString("ownerId") ?: ""
                val createdAt = listDoc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = listDoc.getLong("updatedAt") ?: System.currentTimeMillis()

                val rawItems = (listDoc.get("items") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                val itemsList = rawItems.map { itemMap ->
                    ShoppingItemEntity(
                        id = itemMap["id"] as? String ?: UUID.randomUUID().toString(),
                        shoppingListId = shoppingListId,
                        name = itemMap["name"] as? String ?: "Item",
                        quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 1,
                        unitPriceCents = itemMap["unitPriceCents"] as? Long ?: 0L,
                        subtotalCents = itemMap["subtotalCents"] as? Long ?: 0L,
                        createdAt = itemMap["createdAt"] as? Long ?: System.currentTimeMillis(),
                        updatedAt = itemMap["updatedAt"] as? Long ?: System.currentTimeMillis(),
                        syncStatus = SyncStatus.SYNCED
                    )
                }

                val sharedEntity = ShoppingListEntity(
                    id = shoppingListId,
                    ownerId = ownerId,
                    title = title,
                    location = location,
                    totalCents = totalCents,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    syncStatus = SyncStatus.SYNCED,
                    isSharedWithMe = true,
                    shareCode = cleanCode,
                    isDraft = false
                )

                resultListWithItems = ShoppingListWithItems(list = sharedEntity, items = itemsList)
            }

            val finalResult = resultListWithItems ?: return Result.failure(Exception("Failed to retrieve shopping list."))
            Result.success(finalResult)
        } catch (e: Exception) {
            Log.e(tag, "Retrieve by code failed: ${e.message}", e)
            val errorMessage = when {
                e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                e.message?.contains("Cloud Firestore API", ignoreCase = true) == true ->
                    "Cloud Firestore API is disabled in Firebase Console. Please enable Cloud Firestore API in Google Cloud Console."
                e.message?.contains("offline", ignoreCase = true) == true ->
                    "Unable to connect to Firebase. Check your internet connection and ensure Cloud Firestore API is enabled."
                else -> e.message ?: "Failed to retrieve shopping list."
            }
            Result.failure(Exception(errorMessage))
        }
    }
}
