package com.example.shoplog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.shoplog.core.model.SyncStatus

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("shoppingListId")]
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val shoppingListId: String,
    val name: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val subtotalCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE
)
