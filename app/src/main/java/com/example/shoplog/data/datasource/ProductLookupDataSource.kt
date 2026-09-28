package com.example.shoplog.data.datasource

import com.example.shoplog.data.model.Product

/**
 * Abstraction for external product lookup data sources (e.g. Open Food Facts).
 */
interface ProductLookupDataSource {
    suspend fun lookupProduct(barcode: String): Product?
}
