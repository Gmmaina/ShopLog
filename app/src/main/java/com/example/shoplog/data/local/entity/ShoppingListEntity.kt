package com.example.shoplog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.shoplog.core.model.SyncStatus

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val location: String? = null,
    val totalCents: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isSharedWithMe: Boolean = false,
    val shareCode: String? = null,
    val isDraft: Boolean = false,
    val receiptPhotoPath: String? = null,
    val status: String = STATUS_ACTIVE,
    val completedAt: Long? = null,
    val members: String = "",
    val shop: String? = null
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"
    }

    val isCompleted: Boolean
        get() = status == STATUS_COMPLETED

    val effectiveShopName: String
        get() {
            if (!shop.isNullOrBlank()) return shop.trim()
            val cleanTitle = title.trim()
            val knownShops = listOf("Naivas", "Quickmart", "Carrefour", "Chandarana", "Tuskys", "Cleanshelf", "Zucchini", "Game", "Uchumi", "Supermarket")
            for (known in knownShops) {
                if (cleanTitle.contains(known, ignoreCase = true)) return known
            }
            val firstWord = cleanTitle.split(" ", "-", "·", "@", ",").firstOrNull { it.isNotBlank() }
            return firstWord ?: "General Shop"
        }

    val effectiveLocationName: String
        get() {
            if (!location.isNullOrBlank()) return location.trim()
            return "General Location"
        }

    fun getMemberList(): List<String> {
        if (members.isBlank()) return emptyList()
        return members.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
}
