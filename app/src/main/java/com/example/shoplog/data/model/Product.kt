package com.example.shoplog.data.model

data class Product(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val category: String? = null
)

sealed class ProductLookupResult {
    data class Found(
        val product: Product,
        val isFromLocalCache: Boolean
    ) : ProductLookupResult()

    data class Offline(
        val barcode: String
    ) : ProductLookupResult()

    data class NotFound(
        val barcode: String
    ) : ProductLookupResult()

    data class Error(
        val barcode: String,
        val message: String
    ) : ProductLookupResult()
}
