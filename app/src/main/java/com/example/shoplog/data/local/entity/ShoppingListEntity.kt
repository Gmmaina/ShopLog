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
    val receiptPhotoPath: String? = null
)
