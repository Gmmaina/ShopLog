package com.example.shoplog

import android.content.Context
import android.content.ContextWrapper
import com.example.shoplog.core.util.NetworkMonitor
import com.example.shoplog.data.datasource.FirebaseCommunityProductDataSource
import com.example.shoplog.data.datasource.ProductLookupDataSource
import com.example.shoplog.data.local.dao.ProductDao
import com.example.shoplog.data.local.entity.ProductEntity
import com.example.shoplog.data.model.Product
import com.example.shoplog.data.model.ProductLookupResult
import com.example.shoplog.data.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductRepositoryTest {

    private lateinit var fakeProductDao: FakeProductDao
    private lateinit var fakeCommunityDataSource: FakeCommunityProductDataSource
    private lateinit var fakeOpenFoodFactsDataSource: FakeProductLookupDataSource
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var productRepository: ProductRepository

    @Before
    fun setUp() {
        fakeProductDao = FakeProductDao()
        fakeCommunityDataSource = FakeCommunityProductDataSource()
        fakeOpenFoodFactsDataSource = FakeProductLookupDataSource()
        fakeNetworkMonitor = FakeNetworkMonitor()

        productRepository = ProductRepository(
            productDao = fakeProductDao,
            firebaseCommunityDataSource = fakeCommunityDataSource,
            openFoodFactsDataSource = fakeOpenFoodFactsDataSource,
            networkMonitor = fakeNetworkMonitor
        )
    }

    @Test
    fun test1_knownLocalBarcode_returnsLocalProductWithoutNetworkCall() = runTest {
        // Given a product exists in local database
        val localProduct = ProductEntity(
            barcode = "6161107151836",
            name = "Brookside Powder Milk 2.5kg",
            brand = "Brookside"
        )
        fakeProductDao.insertOrUpdateProduct(localProduct)
        fakeNetworkMonitor.onlineStatus = true

        // When scanned
        val result = productRepository.findOrLookupProduct("6161107151836")

        // Then returns local cached product, no remote call made
        assertTrue(result is ProductLookupResult.Found)
        val found = result as ProductLookupResult.Found
        assertEquals("Brookside Powder Milk 2.5kg", found.product.name)
        assertTrue(found.isFromLocalCache)
        assertEquals(0, fakeOpenFoodFactsDataSource.lookupCallCount)
    }

    @Test
    fun test2_newKnownOnlineBarcode_queriesApiAndCachesLocally() = runTest {
        // Given barcode is NOT in local DB or Community DB, but Open Food Facts has product
        fakeNetworkMonitor.onlineStatus = true
        fakeOpenFoodFactsDataSource.remoteProducts["6161107151836"] = Product(
            barcode = "6161107151836",
            name = "Brookside Powder Milk 2.5kg",
            brand = "Brookside"
        )

        // When scanned
        val result = productRepository.findOrLookupProduct("6161107151836")

        // Then returns product from remote and caches it locally
        assertTrue(result is ProductLookupResult.Found)
        val found = result as ProductLookupResult.Found
        assertEquals("Brookside Powder Milk 2.5kg", found.product.name)
        assertEquals(false, found.isFromLocalCache)

        // Verify product was saved into local DAO
        val cached = fakeProductDao.getProductByBarcode("6161107151836")
        assertEquals("Brookside Powder Milk 2.5kg", cached?.name)
    }

    @Test
    fun test3_sameBarcodeOffline_retrievesFromLocalCache() = runTest {
        // First populate local cache (from previous scan)
        fakeProductDao.insertOrUpdateProduct(
            ProductEntity(barcode = "6161107151836", name = "Brookside Powder Milk 2.5kg")
        )

        // Given device goes offline
        fakeNetworkMonitor.onlineStatus = false

        // When scanned again
        val result = productRepository.findOrLookupProduct("6161107151836")

        // Then returns local product successfully offline
        assertTrue(result is ProductLookupResult.Found)
        val found = result as ProductLookupResult.Found
        assertEquals("Brookside Powder Milk 2.5kg", found.product.name)
        assertTrue(found.isFromLocalCache)
    }

    @Test
    fun test4_unknownBarcodeOffline_returnsOfflineResult() = runTest {
        // Given barcode is NOT in local DB and device is offline
        fakeNetworkMonitor.onlineStatus = false

        // When scanned
        val result = productRepository.findOrLookupProduct("6161999999999")

        // Then returns Offline status without error
        assertTrue(result is ProductLookupResult.Offline)
        val offline = result as ProductLookupResult.Offline
        assertEquals("6161999999999", offline.barcode)
    }

    @Test
    fun test5_unknownBarcodeOnline_returnsNotFoundResult() = runTest {
        // Given device is online, but barcode is not in remote database
        fakeNetworkMonitor.onlineStatus = true

        // When scanned
        val result = productRepository.findOrLookupProduct("6161999999999")

        // Then returns NotFound status
        assertTrue(result is ProductLookupResult.NotFound)
        val notFound = result as ProductLookupResult.NotFound
        assertEquals("6161999999999", notFound.barcode)
    }

    @Test
    fun test6_mismatchedBarcodeResponse_rejectsResponse() = runTest {
        // Given API returns a product with a completely different barcode
        fakeNetworkMonitor.onlineStatus = true
        fakeOpenFoodFactsDataSource.remoteProducts["6161107151836"] = Product(
            barcode = "9999999999999", // Mismatched GTIN
            name = "Wrong Item"
        )

        // When scanned
        val result = productRepository.findOrLookupProduct("6161107151836")

        // Then lookup is rejected as NotFound
        assertTrue(result is ProductLookupResult.NotFound)
    }

    @Test
    fun test7_manualEntry_worksWithoutBarcode() = runTest {
        // Blank barcode query returns NotFound immediately
        val result = productRepository.findOrLookupProduct("")
        assertTrue(result is ProductLookupResult.NotFound)
    }
}

private class FakeProductDao : ProductDao {
    private val db = mutableMapOf<String, ProductEntity>()

    override suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return db[barcode]
    }

    override suspend fun insertOrUpdateProduct(product: ProductEntity) {
        db[product.barcode] = product
    }

    override fun getAllProductsFlow(): Flow<List<ProductEntity>> {
        return flowOf(db.values.toList())
    }

    override suspend fun deleteProductByBarcode(barcode: String) {
        db.remove(barcode)
    }

    override suspend fun deleteAllProducts() {
        db.clear()
    }
}

private class FakeCommunityProductDataSource : FirebaseCommunityProductDataSource() {
    val communityDb = mutableMapOf<String, Product>()

    override suspend fun lookupProduct(barcode: String): Product? {
        return communityDb[barcode]
    }

    override suspend fun submitProduct(product: Product): Result<Unit> {
        communityDb[product.barcode] = product
        return Result.success(Unit)
    }
}

private class FakeProductLookupDataSource : ProductLookupDataSource {
    val remoteProducts = mutableMapOf<String, Product>()
    var lookupCallCount = 0

    override suspend fun lookupProduct(barcode: String): Product? {
        lookupCallCount++
        return remoteProducts[barcode]
    }
}

private class DummyContext : ContextWrapper(null) {
    override fun getSystemService(name: String): Any? = null
}

private class FakeNetworkMonitor : NetworkMonitor(
    context = DummyContext()
) {
    var onlineStatus = true

    override fun isOnlineNow(): Boolean {
        return onlineStatus
    }
}
