package com.example.shoplog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.shoplog.data.local.dao.ShoppingDao
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity

@Database(
    entities = [ShoppingListEntity::class, ShoppingItemEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ShopLogDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
}
