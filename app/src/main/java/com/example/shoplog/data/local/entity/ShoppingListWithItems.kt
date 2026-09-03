package com.example.shoplog.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ShoppingListWithItems(
    @Embedded val list: ShoppingListEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "shoppingListId"
    )
    @JvmSuppressWildcards
    val items: List<ShoppingItemEntity>
)
