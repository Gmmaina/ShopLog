package com.example.shoplog.data.remote

import android.util.Log
import com.example.shoplog.core.model.SyncStatus
import com.example.shoplog.data.local.dao.ShoppingDao
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity
import com.example.shoplog.data.local.entity.ShoppingListWithItems
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles background synchronization, real-time live collaboration, and Cloud Firestore operations.
 * Gracefully operates offline when internet is unavailable.
 */
@Singleton
class FirebaseSyncManager @Inject constructor(
    private val shoppingDao: ShoppingDao
) {
    private val tag = "FirebaseSyncManager"

    private val auth: FirebaseAuth?
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    private val db: FirebaseFirestore?
        get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    /**
     * Attempts a Firestore operation across possible database instances ("default" vs default instance)
     * to gracefully handle custom or regional database identifiers.
     */
    private suspend fun performFirestoreOp(op: suspend (FirebaseFirestore) -> Unit) {
        val instances = mutableListOf<FirebaseFirestore>()
        runCatching { FirebaseFirestore.getInstance("default") }.getOrNull()?.let { instances.add(it) }
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()?.let { instances.add(it) }

        var lastException: Exception? = null
        for (database in instances.distinct()) {
            try {
                op(database)
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
     * Attaches a real-time Firestore listener to a shopping list for Live Collaborative Shopping.
     * When remote participants add/edit/delete items or update status, Room local DB is updated instantly.
     */
    @Suppress("UNCHECKED_CAST")
    fun attachLiveListListener(
        listId: String,
        onRemoteUpdated: ((ShoppingListWithItems) -> Unit)? = null
    ): ListenerRegistration? {
        val firestore = db ?: return null

        return firestore.collection("shopping_lists").document(listId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Live sync listener error for $listId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val title = snapshot.getString("title") ?: "Shopping List"
                    val location = snapshot.getString("location")
                    val totalCents = snapshot.getLong("totalCents") ?: 0L
                    val ownerId = snapshot.getString("ownerId") ?: ""
                    val createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis()
                    val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                    val shareCode = snapshot.getString("shareCode")
                    val isDraft = snapshot.getBoolean("isDraft") ?: false
                    val status = snapshot.getString("status") ?: ShoppingListEntity.STATUS_ACTIVE
                    val completedAt = snapshot.getLong("completedAt")
                    val members = snapshot.getString("members") ?: ""
                    val shop = snapshot.getString("shop")

                    val rawItems = (snapshot.get("items") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                    val itemsList = rawItems.map { itemMap ->
                        ShoppingItemEntity(
                            id = itemMap["id"] as? String ?: UUID.randomUUID().toString(),
                            shoppingListId = listId,
                            name = itemMap["name"] as? String ?: "Item",
                            quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 1,
                            unitPriceCents = itemMap["unitPriceCents"] as? Long ?: 0L,
                            subtotalCents = itemMap["subtotalCents"] as? Long ?: 0L,
                            barcode = itemMap["barcode"] as? String,
                            isPurchased = itemMap["isPurchased"] as? Boolean ?: false,
                            createdAt = itemMap["createdAt"] as? Long ?: System.currentTimeMillis(),
                            updatedAt = itemMap["updatedAt"] as? Long ?: System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED
                        )
                    }

                    val currentUid = auth?.currentUser?.uid ?: ""
                    val isSharedWithMe = ownerId.isNotBlank() && currentUid.isNotBlank() && ownerId != currentUid

                    val listEntity = ShoppingListEntity(
                        id = listId,
                        ownerId = ownerId,
                        title = title,
                        location = location,
                        totalCents = totalCents,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncStatus = SyncStatus.SYNCED,
                        isSharedWithMe = isSharedWithMe,
                        shareCode = shareCode,
                        isDraft = isDraft,
                        status = status,
                        completedAt = completedAt,
                        members = members,
                        shop = shop
                    )

                    val updatedWithItems = ShoppingListWithItems(list = listEntity, items = itemsList)
                    onRemoteUpdated?.invoke(updatedWithItems)
                }
            }
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

                performFirestoreOp { database ->
                    if (list.deletedAt != null || list.syncStatus == SyncStatus.PENDING_DELETE) {
                        database.collection("shopping_lists").document(list.id).delete().await()
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
                            "status" to list.status,
                            "completedAt" to list.completedAt,
                            "members" to list.members,
                            "shop" to list.shop,
                            "items" to items.map { item ->
                                hashMapOf(
                                    "id" to item.id,
                                    "name" to item.name,
                                    "quantity" to item.quantity,
                                    "unitPriceCents" to item.unitPriceCents,
                                    "subtotalCents" to item.subtotalCents,
                                    "barcode" to item.barcode,
                                    "isPurchased" to item.isPurchased,
                                    "createdAt" to item.createdAt,
                                    "updatedAt" to item.updatedAt
                                )
                            }
                        )

                        database.collection("shopping_lists").document(list.id).set(listMap).await()

                        // Also store share code mapping if present
                        list.shareCode?.let { code ->
                            database.collection("share_codes").document(code.uppercase()).set(
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
     * Downloads remote shopping lists owned by or shared with [userId] from Firestore
     * and caches them into Room local database.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchAndSyncRemoteUserLists(userId: String): Result<Unit> {
        if (userId.isBlank() || userId == "offline_user" || userId == "local_user") {
            return Result.success(Unit)
        }

        return try {
            performFirestoreOp { database ->
                val querySnapshot = database.collection("shopping_lists")
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
                    val status = doc.getString("status") ?: ShoppingListEntity.STATUS_ACTIVE
                    val completedAt = doc.getLong("completedAt")
                    val members = doc.getString("members") ?: ""
                    val shop = doc.getString("shop")

                    val rawItems = (doc.get("items") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                    val itemsList = rawItems.map { itemMap ->
                        ShoppingItemEntity(
                            id = itemMap["id"] as? String ?: UUID.randomUUID().toString(),
                            shoppingListId = listId,
                            name = itemMap["name"] as? String ?: "Item",
                            quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 1,
                            unitPriceCents = itemMap["unitPriceCents"] as? Long ?: 0L,
                            subtotalCents = itemMap["subtotalCents"] as? Long ?: 0L,
                            barcode = itemMap["barcode"] as? String,
                            isPurchased = itemMap["isPurchased"] as? Boolean ?: false,
                            createdAt = itemMap["createdAt"] as? Long ?: System.currentTimeMillis(),
                            updatedAt = itemMap["updatedAt"] as? Long ?: System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED
                        )
                    }

                    val isSharedWithMe = ownerId.isNotBlank() && userId.isNotBlank() && ownerId != userId

                    val listEntity = ShoppingListEntity(
                        id = listId,
                        ownerId = ownerId,
                        title = title,
                        location = location,
                        totalCents = totalCents,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncStatus = SyncStatus.SYNCED,
                        isSharedWithMe = isSharedWithMe,
                        shareCode = shareCode,
                        isDraft = isDraft,
                        status = status,
                        completedAt = completedAt,
                        members = members,
                        shop = shop
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
     * Joins a collaborative shopping session using a share code (e.g. `AUG123D`).
     * Registers current user as a member and returns the shopping list.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun joinCollaborativeListByCode(code: String): Result<ShoppingListWithItems> {
        val cleanCode = code.trim().uppercase()
        var resultListWithItems: ShoppingListWithItems? = null
        var currentUid = auth?.currentUser?.uid ?: ""

        if (currentUid.isBlank()) {
            runCatching {
                auth?.signInAnonymously()?.await()
            }
            currentUid = auth?.currentUser?.uid ?: ""
        }

        return try {
            performFirestoreOp { database ->
                val codeDoc = database.collection("share_codes").document(cleanCode).get().await()
                if (!codeDoc.exists()) {
                    throw Exception("Invalid or expired shopping code: $cleanCode")
                }

                val shoppingListId = codeDoc.getString("shoppingListId")
                    ?: throw Exception("Shopping list ID not found for code $cleanCode")

                val listDocRef = database.collection("shopping_lists").document(shoppingListId)
                val listDoc = listDocRef.get().await()
                if (!listDoc.exists()) {
                    throw Exception("Shared shopping list no longer exists.")
                }

                val ownerId = listDoc.getString("ownerId") ?: ""
                var existingMembers = listDoc.getString("members") ?: ""

                // Add current user to members list if not present
                if (currentUid.isNotBlank() && !existingMembers.contains(currentUid)) {
                    existingMembers = if (existingMembers.isBlank()) currentUid else "$existingMembers,$currentUid"
                    listDocRef.update("members", existingMembers).await()
                }

                val title = listDoc.getString("title") ?: "Shared Shopping"
                val location = listDoc.getString("location")
                val totalCents = listDoc.getLong("totalCents") ?: 0L
                val createdAt = listDoc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = listDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                val status = listDoc.getString("status") ?: ShoppingListEntity.STATUS_ACTIVE
                val completedAt = listDoc.getLong("completedAt")
                val shop = listDoc.getString("shop")

                val rawItems = (listDoc.get("items") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                val itemsList = rawItems.map { itemMap ->
                    ShoppingItemEntity(
                        id = itemMap["id"] as? String ?: UUID.randomUUID().toString(),
                        shoppingListId = shoppingListId,
                        name = itemMap["name"] as? String ?: "Item",
                        quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 1,
                        unitPriceCents = itemMap["unitPriceCents"] as? Long ?: 0L,
                        subtotalCents = itemMap["subtotalCents"] as? Long ?: 0L,
                        barcode = itemMap["barcode"] as? String,
                        isPurchased = itemMap["isPurchased"] as? Boolean ?: false,
                        createdAt = itemMap["createdAt"] as? Long ?: System.currentTimeMillis(),
                        updatedAt = itemMap["updatedAt"] as? Long ?: System.currentTimeMillis(),
                        syncStatus = SyncStatus.SYNCED
                    )
                }

                val isMyOwn = ownerId.isNotBlank() && currentUid.isNotBlank() && ownerId == currentUid

                val sharedEntity = ShoppingListEntity(
                    id = shoppingListId,
                    ownerId = ownerId,
                    title = title,
                    location = location,
                    totalCents = totalCents,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    syncStatus = SyncStatus.SYNCED,
                    isSharedWithMe = !isMyOwn,
                    shareCode = cleanCode,
                    isDraft = false,
                    status = status,
                    completedAt = completedAt,
                    members = existingMembers,
                    shop = shop
                )

                resultListWithItems = ShoppingListWithItems(list = sharedEntity, items = itemsList)
            }

            val finalResult = resultListWithItems ?: return Result.failure(Exception("Failed to retrieve shopping list."))
            Result.success(finalResult)
        } catch (e: Exception) {
            Log.e(tag, "Join by code failed: ${e.message}", e)
            val errorMessage = when {
                e is SecurityException || e.message?.contains("calling package name", ignoreCase = true) == true ->
                    "Google Play Services security error. Please ensure Google Play Services is enabled and updated on this device."
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

    /**
     * Ends an active live shopping session. Only the list owner can invoke this action.
     * Transitions state to COMPLETED and records completion timestamp.
     */
    suspend fun endShoppingSession(listId: String): Result<Unit> {
        val currentUid = auth?.currentUser?.uid ?: ""
        return try {
            performFirestoreOp { database ->
                val listDocRef = database.collection("shopping_lists").document(listId)
                val listDoc = listDocRef.get().await()

                if (listDoc.exists()) {
                    val ownerId = listDoc.getString("ownerId") ?: ""
                    if (ownerId.isNotBlank() && currentUid.isNotBlank() && ownerId != currentUid) {
                        throw Exception("Only the list owner can end the shopping session.")
                    }

                    val now = System.currentTimeMillis()
                    listDocRef.update(
                        mapOf(
                            "status" to ShoppingListEntity.STATUS_COMPLETED,
                            "completedAt" to now,
                            "updatedAt" to now
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "End shopping session failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
