/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.pinxoft.orderpiqr

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeScannerProcessor(private val context: Context) :
    VisionProcessorBase<List<Barcode>>(context) {

    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

    override fun stop() {
        super.stop()
        barcodeScanner.close()
    }

    override fun detectInImage(image: InputImage): Task<List<Barcode>> {
        return barcodeScanner.process(image)
    }

    override fun onSuccess(results: List<Barcode>) {
        if (results.isEmpty()) {
            Log.v(MANUAL_TESTING_LOG, "No barcode has been detected")
        }
        for (i in results.indices) {
            val barcode = results[i]
            logExtrasForTesting(barcode)
        }
        if (results.isNotEmpty()) {
            this.stop()
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Barcode detection failed $e")
    }

    companion object {
        private const val TAG = "BarcodeProcessor"

        private fun logExtrasForTesting(barcode: Barcode?) {
            if (barcode != null) {
                Log.v(
                    MANUAL_TESTING_LOG,
                    String.format(
                        "Detected barcode's bounding box: %s",
                        barcode.boundingBox!!.flattenToString()
                    )
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    String.format(
                        "Expected corner point size is 4, get %d",
                        barcode.cornerPoints!!.size
                    )
                )
                for (point in barcode.cornerPoints!!) {
                    Log.v(
                        MANUAL_TESTING_LOG,
                        String.format(
                            "Corner point is located at: x = %d, y = %d",
                            point.x,
                            point.y
                        )
                    )
                }
                Log.v(
                    MANUAL_TESTING_LOG,
                    "barcode display value: " + barcode.displayValue
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "barcode raw value: " + barcode.rawValue
                )
            }
        }
    }
}

