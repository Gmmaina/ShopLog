package com.example.shoplog.data.datasource

import android.util.Log
import com.example.shoplog.data.model.Product
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FirebaseCommunityProductDataSource @Inject constructor() {

    private companion object {
        const val TAG = "FirebaseCommunityDS"
        const val COLLECTION_PRODUCTS = "community_products"
    }

    private val auth: FirebaseAuth?
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    private val db: FirebaseFirestore?
        get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private fun getCurrentUserId(): String {
        return auth?.currentUser?.uid ?: "anonymous_contributor"
    }

    /**
     * Normalizes product names for deterministic agreement matching.
     * Collapses spaces, trims, lowercases, and strips basic punctuation.
     */
    fun normalizeName(rawName: String): String {
        return rawName.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[.,/#!$%^&*;:{}=\\-_`~()]"), "")
    }

    /**
     * Looks up a product in the Firebase Community Database.
     * Returns canonical product if found, or null if absent.
     */
    open suspend fun lookupProduct(barcode: String): Product? {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return null
        val firestore = db ?: return null

        return try {
            val doc = firestore.collection(COLLECTION_PRODUCTS)
                .document(cleanBarcode)
                .get()
                .await()

            if (doc.exists()) {
                val canonicalName = doc.getString("canonicalName")
                if (!canonicalName.isNullOrBlank()) {
                    Log.d(TAG, "Found canonical product in Firebase for $cleanBarcode: $canonicalName")
                    Product(
                        barcode = cleanBarcode,
                        name = canonicalName,
                        brand = doc.getString("brand"),
                        category = doc.getString("category")
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Error looking up community product $cleanBarcode: ${e.message}")
            null
        }
    }

    /**
     * Submits or updates product information in the Firebase Community Database
     * using a Firestore transaction to ensure atomic updates and unique contributor counting.
     */
    open suspend fun submitProduct(product: Product): Result<Unit> {
        val cleanBarcode = product.barcode.trim()
        val rawName = product.name.trim()
        if (cleanBarcode.isBlank() || rawName.isBlank()) return Result.success(Unit)

        val firestore = db ?: return Result.failure(Exception("Firestore not initialized"))
        val userId = getCurrentUserId()
        val normName = normalizeName(rawName)
        val docRef = firestore.collection(COLLECTION_PRODUCTS).document(cleanBarcode)

        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val now = System.currentTimeMillis()

                val existingSubmissions = if (snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    snapshot.get("submissions") as? MutableMap<String, Any> ?: mutableMapOf()
                } else {
                    mutableMapOf()
                }

                @Suppress("UNCHECKED_CAST")
                val targetSub = (existingSubmissions[normName] as? Map<String, Any>)?.toMutableMap()
                    ?: mutableMapOf(
                        "displayName" to rawName,
                        "brand" to (product.brand ?: ""),
                        "category" to (product.category ?: ""),
                        "contributors" to mutableMapOf<String, Long>()
                    )

                @Suppress("UNCHECKED_CAST")
                val contributorsMap = ((targetSub["contributors"] as? Map<String, Any>)
                    ?.mapValues { (_, v) -> (v as? Long) ?: now }
                    ?: emptyMap()).toMutableMap()

                contributorsMap[userId] = now
                val uniqueCount = contributorsMap.size

                targetSub["contributors"] = contributorsMap
                targetSub["uniqueCount"] = uniqueCount
                if ((targetSub["displayName"] as? String).isNullOrBlank()) {
                    targetSub["displayName"] = rawName
                }
                if ((targetSub["brand"] as? String).isNullOrBlank() && !product.brand.isNullOrBlank()) {
                    targetSub["brand"] = product.brand
                }

                existingSubmissions[normName] = targetSub

                // Determine canonical product based on highest unique user agreement
                var bestCandidateName = rawName
                var bestBrand: String? = product.brand
                var bestCategory: String? = product.category
                var maxUniqueUsers = 0

                for ((_, subValue) in existingSubmissions) {
                    @Suppress("UNCHECKED_CAST")
                    val subMap = subValue as? Map<String, Any> ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val subContributors = subMap["contributors"] as? Map<*, *> ?: emptyMap<Any, Any>()
                    val subUniqueCount = subContributors.size
                    val subName = subMap["displayName"] as? String ?: ""

                    if (subUniqueCount > maxUniqueUsers && subName.isNotBlank()) {
                        maxUniqueUsers = subUniqueCount
                        bestCandidateName = subName
                        bestBrand = (subMap["brand"] as? String)?.ifBlank { null }
                        bestCategory = (subMap["category"] as? String)?.ifBlank { null }
                    }
                }

                val currentCanonicalName = snapshot.getString("canonicalName")
                val currentUniqueCount = snapshot.getLong("uniqueContributorCount")?.toInt() ?: 0

                val finalCanonicalName = if (snapshot.exists() && currentCanonicalName != null && maxUniqueUsers <= currentUniqueCount) {
                    currentCanonicalName
                } else {
                    bestCandidateName
                }

                val finalBrand = if (snapshot.exists() && currentCanonicalName != null && maxUniqueUsers <= currentUniqueCount) {
                    snapshot.getString("brand")
                } else {
                    bestBrand
                }

                val finalCategory = if (snapshot.exists() && currentCanonicalName != null && maxUniqueUsers <= currentUniqueCount) {
                    snapshot.getString("category")
                } else {
                    bestCategory
                }

                val finalUniqueCount = if (snapshot.exists() && currentCanonicalName != null && maxUniqueUsers <= currentUniqueCount) {
                    currentUniqueCount
                } else {
                    maxUniqueUsers
                }

                val docData = hashMapOf<String, Any?>(
                    "barcode" to cleanBarcode,
                    "canonicalName" to finalCanonicalName,
                    "brand" to finalBrand,
                    "category" to finalCategory,
                    "uniqueContributorCount" to finalUniqueCount,
                    "updatedAt" to now,
                    "submissions" to existingSubmissions
                )

                transaction.set(docRef, docData)
            }.await()

            Log.d(TAG, "Successfully submitted product $cleanBarcode ($rawName) to Firebase Community DB")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit product $cleanBarcode to Firebase Community DB: ${e.message}", e)
            Result.failure(e)
        }
    }
}
