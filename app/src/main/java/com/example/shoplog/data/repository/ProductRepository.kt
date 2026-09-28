package com.example.shoplog.data.repository

import android.util.Log
import com.example.shoplog.core.util.NetworkMonitor
import com.example.shoplog.data.datasource.FirebaseCommunityProductDataSource
import com.example.shoplog.data.datasource.ProductLookupDataSource
import com.example.shoplog.data.local.dao.ProductDao
import com.example.shoplog.data.local.entity.ProductEntity
import com.example.shoplog.data.model.Product
import com.example.shoplog.data.model.ProductLookupResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val firebaseCommunityDataSource: FirebaseCommunityProductDataSource,
    private val openFoodFactsDataSource: ProductLookupDataSource,
    private val networkMonitor: NetworkMonitor
) {
    private companion object {
        const val TAG = "ProductRepository"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun getLocalProduct(barcode: String): Product? {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return null

        val entity = productDao.getProductByBarcode(cleanBarcode) ?: return null
        return Product(
            barcode = entity.barcode,
            name = entity.name,
            brand = entity.brand,
            category = entity.category
        )
    }

    suspend fun saveProduct(product: Product) {
        val entity = ProductEntity(
            barcode = product.barcode.trim(),
            name = product.name.trim(),
            brand = product.brand?.trim(),
            category = product.category?.trim(),
            updatedAt = System.currentTimeMillis()
        )
        productDao.insertOrUpdateProduct(entity)
    }

    /**
     * Submits a product to the Firebase Community Database asynchronously
     * so user interactions remain fast and unblocked.
     */
    fun submitToCommunityDatabase(product: Product) {
        scope.launch {
            runCatching {
                firebaseCommunityDataSource.submitProduct(product)
            }
        }
    }

    /**
     * Saves a product locally AND submits it to the Firebase Community Database.
     * Called when a user manually enters or edits a barcode product.
     */
    suspend fun saveAndSubmitProduct(product: Product) {
        saveProduct(product)
        submitToCommunityDatabase(product)
    }

    /**
     * Executes the mandatory barcode lookup pipeline:
     * 1. LOCAL DATABASE
     * 2. FIREBASE COMMUNITY DATABASE
     * 3. OPEN FOOD FACTS
     * 4. MANUAL ENTRY (Caller handles when NotFound is returned)
     */
    suspend fun findOrLookupProduct(barcode: String): ProductLookupResult {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return ProductLookupResult.NotFound(cleanBarcode)

        // 1. Check LOCAL ROOM DATABASE FIRST
        val local = getLocalProduct(cleanBarcode)
        if (local != null) {
            return ProductLookupResult.Found(local, isFromLocalCache = true)
        }

        // Check network connectivity before remote queries
        if (!networkMonitor.isOnlineNow()) {
            return ProductLookupResult.Offline(cleanBarcode)
        }

        // 2. Check FIREBASE COMMUNITY DATABASE
        try {
            val communityProduct = firebaseCommunityDataSource.lookupProduct(cleanBarcode)
            if (communityProduct != null) {
                // Save canonical product locally for fast offline access next time
                saveProduct(communityProduct)
                return ProductLookupResult.Found(communityProduct, isFromLocalCache = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Community database lookup bypassed: ${e.message}")
        }

        // 3. Fallback to OPEN FOOD FACTS API
        return try {
            val remoteProduct = openFoodFactsDataSource.lookupProduct(cleanBarcode)
            if (remoteProduct != null) {
                // Validate returned barcode matches scanned barcode
                val scannedClean = cleanBarcode.lowercase().removePrefix("0")
                val remoteClean = remoteProduct.barcode.trim().lowercase().removePrefix("0")

                if (remoteClean.isNotBlank() && !scannedClean.endsWith(remoteClean) && !remoteClean.endsWith(scannedClean) && scannedClean != remoteClean) {
                    ProductLookupResult.NotFound(cleanBarcode)
                } else {
                    // Save to local product cache
                    saveProduct(remoteProduct)
                    // Asynchronously submit/merge into Firebase Community Database
                    submitToCommunityDatabase(remoteProduct)
                    ProductLookupResult.Found(remoteProduct, isFromLocalCache = false)
                }
            } else {
                // 4. MANUAL ENTRY required
                ProductLookupResult.NotFound(cleanBarcode)
            }
        } catch (e: Exception) {
            ProductLookupResult.Error(cleanBarcode, e.message ?: "Network lookup failed.")
        }
    }

    fun getAllProductsFlow(): Flow<List<Product>> {
        return productDao.getAllProductsFlow().map { entities ->
            entities.map { entity ->
                Product(
                    barcode = entity.barcode,
                    name = entity.name,
                    brand = entity.brand,
                    category = entity.category
                )
            }
        }
    }
}
