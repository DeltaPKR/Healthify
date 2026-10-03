package com.healthify.app.food

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google Code Scanner: Google Play services shows the camera UI and hands
 * back only the decoded value, so the app needs no CAMERA permission.
 * Food packaging uses EAN/UPC only.
 */
object BarcodeScanner {

    sealed interface Outcome {
        data class Scanned(val code: String) : Outcome
        data object Cancelled : Outcome
        /** No Play services, scanner module not downloaded yet, or no camera. */
        data object Unavailable : Outcome
    }

    fun scan(context: Context, onOutcome: (Outcome) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
            )
            .enableAutoZoom()
            .build()
        try {
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { b ->
                    val code = b.rawValue?.filter(Char::isDigit).orEmpty()
                    onOutcome(if (code.isNotEmpty()) Outcome.Scanned(code) else Outcome.Cancelled)
                }
                .addOnCanceledListener { onOutcome(Outcome.Cancelled) }
                .addOnFailureListener { onOutcome(Outcome.Unavailable) }
        } catch (e: Exception) {
            onOutcome(Outcome.Unavailable)
        }
    }
}
