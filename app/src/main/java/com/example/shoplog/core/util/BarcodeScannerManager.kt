package com.example.shoplog.core.util

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Manages native camera barcode scanning using Google Code Scanner API.
 * Prompts the user with a system camera viewfinder to scan physical product barcodes.
 */
object BarcodeScannerManager {

    fun startCameraScan(
        context: Context,
        onBarcodeScanned: (String) -> Unit,
        onErrorOrFallback: (String) -> Unit
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_CODE_128
                )
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)

            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        onBarcodeScanned(rawValue)
                    } else {
                        onErrorOrFallback("No barcode detected.")
                    }
                }
                .addOnCanceledListener {
                    onErrorOrFallback("Scan cancelled by user.")
                }
                .addOnFailureListener { e ->
                    Log.w("BarcodeScannerManager", "Google Code Scanner failed/unavailable: ${e.message}")
                    onErrorOrFallback(e.message ?: "Camera barcode scanner unavailable.")
                }
        } catch (e: Exception) {
            Log.e("BarcodeScannerManager", "Error launching barcode scanner: ${e.message}", e)
            onErrorOrFallback(e.message ?: "Camera scanner error.")
        }
    }
}
