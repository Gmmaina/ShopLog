package com.example.shoplog.data.datasource

import android.util.Log
import com.example.shoplog.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFoodFactsProductDataSource @Inject constructor() : ProductLookupDataSource {

    private companion object {
        const val TAG = "OpenFoodFactsDS"
    }

    override suspend fun lookupProduct(barcode: String): Product? = withContext(Dispatchers.IO) {
        val cleanEan = barcode.trim()
        if (cleanEan.length < 8) return@withContext null

        val candidateCodes = mutableListOf(cleanEan)
        if (cleanEan.length == 12) {
            candidateCodes.add("0$cleanEan")
        }

        // Open Facts APIs (Food, Beauty, Products)
        val openFactsSubdomains = listOf("world", "us")
        val openFactsDomains = listOf("openfoodfacts.org", "openbeautyfacts.org", "openproductsfacts.org")

        for (code in candidateCodes) {
            for (domain in openFactsDomains) {
                for (subdomain in openFactsSubdomains) {
                    val product = fetchFromOpenFacts(subdomain, domain, code, cleanEan)
                    if (product != null) {
                        Log.d(TAG, "Resolved product for $cleanEan -> ${product.name} via $subdomain.$domain")
                        return@withContext product
                    }
                }
            }
        }

        null
    }

    private fun fetchFromOpenFacts(
        subdomain: String,
        domain: String,
        code: String,
        targetBarcode: String
    ): Product? {
        return try {
            val url = URL("https://$subdomain.$domain/api/v2/product/$code.json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "ShopLogApp/1.0 - Android Barcode Resolver")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optInt("status") == 1) {
                    val productJson = json.optJSONObject("product") ?: return null

                    // Section 6 Validation: check returned barcode/code if present
                    val returnedCode = json.optString("code", productJson.optString("_id", ""))
                    if (returnedCode.isNotBlank() && !isMatchingBarcode(returnedCode, targetBarcode)) {
                        Log.w(TAG, "Mismatched barcode in API response: expected $targetBarcode, got $returnedCode")
                        return null
                    }

                    val name = productJson.optString("product_name")
                        .ifBlank { productJson.optString("product_name_en") }
                        .ifBlank { productJson.optString("generic_name") }
                        .ifBlank { productJson.optString("generic_name_en") }
                        .ifBlank { productJson.optString("abbreviated_product_name") }

                    if (name.isNotBlank()) {
                        val brand = productJson.optString("brands")
                            .ifBlank { productJson.optString("brand") }
                        val quantity = productJson.optString("quantity")
                        val category = productJson.optString("categories")
                            .ifBlank { productJson.optString("categories_tags") }

                        var fullName = if (brand.isNotBlank() && !name.contains(brand, ignoreCase = true)) {
                            "$brand $name"
                        } else {
                            name
                        }

                        if (quantity.isNotBlank() && !fullName.contains(quantity, ignoreCase = true)) {
                            fullName = "$fullName $quantity"
                        }

                        return Product(
                            barcode = targetBarcode,
                            name = fullName.trim(),
                            brand = brand.ifBlank { null },
                            category = category.ifBlank { null }
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isMatchingBarcode(codeA: String, codeB: String): Boolean {
        val cleanA = codeA.trim().lowercase().removePrefix("0")
        val cleanB = codeB.trim().lowercase().removePrefix("0")
        return cleanA == cleanB || cleanA.endsWith(cleanB) || cleanB.endsWith(cleanA)
    }
}
