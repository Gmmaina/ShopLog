package com.example.shoplog.di

import com.example.shoplog.data.datasource.OpenFoodFactsProductDataSource
import com.example.shoplog.data.datasource.ProductLookupDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProductModule {

    @Binds
    @Singleton
    abstract fun bindProductLookupDataSource(
        impl: OpenFoodFactsProductDataSource
    ): ProductLookupDataSource
}
