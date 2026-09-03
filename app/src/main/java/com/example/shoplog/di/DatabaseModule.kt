package com.example.shoplog.di

import android.content.Context
import androidx.room.Room
import com.example.shoplog.data.local.ShopLogDatabase
import com.example.shoplog.data.local.dao.ShoppingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideShopLogDatabase(
        @ApplicationContext context: Context
    ): ShopLogDatabase {
        return Room.databaseBuilder(
            context,
            ShopLogDatabase::class.java,
            "shoplog_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideShoppingDao(database: ShopLogDatabase): ShoppingDao {
        return database.shoppingDao()
    }
}
