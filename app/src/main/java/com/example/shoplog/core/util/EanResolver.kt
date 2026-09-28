package com.example.shoplog.core.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Legacy barcode resolver.
 * @deprecated Replaced by [com.example.shoplog.data.repository.ProductRepository] and [com.example.shoplog.data.datasource.OpenFoodFactsProductDataSource].
 */
@Deprecated("Replaced by ProductRepository and OpenFoodFactsProductDataSource", ReplaceWith("ProductRepository.findOrLookupProduct(eanCode)"))
object EanResolver {

    private const val TAG = "EanResolver"

    suspend fun lookupEan(eanCode: String): String? = withContext(Dispatchers.IO) {
        val cleanEan = eanCode.trim()
        if (cleanEan.length < 8) return@withContext null

        val candidates = mutableListOf(cleanEan)
        if (cleanEan.length == 12) {
            candidates.add("0$cleanEan")
        }

        // 1. Check Open Facts APIs (Food, Beauty, Products)
        val openFactsSubdomains = listOf("world", "us")
        val openFactsDomains = listOf("openfoodfacts.org", "openbeautyfacts.org", "openproductsfacts.org")

        for (code in candidates) {
            for (domain in openFactsDomains) {
                for (subdomain in openFactsSubdomains) {
                    val name = fetchFromOpenFacts(subdomain, domain, code)
                    if (!name.isNullOrBlank()) {
                        Log.d(TAG, "Resolved EAN $cleanEan -> $name via $subdomain.$domain")
                        return@withContext name
                    }
                }
            }
        }

        // 2. Fallback to UPC Item DB
        for (code in candidates) {
            val upcName = fetchFromUpcItemDb(code)
            if (!upcName.isNullOrBlank()) {
                Log.d(TAG, "Resolved EAN $cleanEan -> $upcName via UPC Item DB")
                return@withContext upcName
            }
        }

        null
    }

    private fun fetchFromOpenFacts(subdomain: String, domain: String, ean: String): String? {
        return try {
            val url = URL("https://$subdomain.$domain/api/v2/product/$ean.json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "ShopLogApp/1.0 - Android Barcode Resolver")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optInt("status") == 1) {
                    val product = json.optJSONObject("product") ?: return null

                    val name = product.optString("product_name")
                        .ifBlank { product.optString("product_name_en") }
                        .ifBlank { product.optString("generic_name") }
                        .ifBlank { product.optString("generic_name_en") }
                        .ifBlank { product.optString("abbreviated_product_name") }

                    if (name.isNotBlank()) {
                        val brand = product.optString("brands")
                            .ifBlank { product.optString("brand") }
                        val quantity = product.optString("quantity")

                        var fullName = if (brand.isNotBlank() && !name.contains(brand, ignoreCase = true)) {
                            "$brand $name"
                        } else {
                            name
                        }

                        if (quantity.isNotBlank() && !fullName.contains(quantity, ignoreCase = true)) {
                            fullName = "$fullName $quantity"
                        }

                        return fullName.trim()
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFromUpcItemDb(upc: String): String? {
        return try {
            val url = URL("https://api.upcitemdb.com/prod/trial/lookup?upc=$upc")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "ShopLogApp/1.0 - Android Barcode Resolver")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val firstItem = items.getJSONObject(0)
                    val title = firstItem.optString("title")
                    val brand = firstItem.optString("brand")

                    if (title.isNotBlank()) {
                        return if (brand.isNotBlank() && !title.contains(brand, ignoreCase = true)) {
                            "$brand $title"
                        } else {
                            title
                        }.trim()
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
