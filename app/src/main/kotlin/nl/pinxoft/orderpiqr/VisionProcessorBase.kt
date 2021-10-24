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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build.VERSION_CODES
import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.*
import com.google.android.odml.image.MlImage
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import java.lang.Math.max
import java.lang.Math.min
import java.nio.ByteBuffer
import java.util.*

/**
 * Abstract base class for ML Kit frame processors. Subclasses need to implement {@link
 * #onSuccess(T, FrameMetadata, GraphicOverlay)} to define what they want to with the detection
 * results and {@link #detectInImage(VisionImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
abstract class VisionProcessorBase<T>(context: Context) : VisionImageProcessor {

    companion object {
        const val MANUAL_TESTING_LOG = "LogTagForTest"
        private const val TAG = "VisionProcessorBase"
    }

    private var activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val fpsTimer = Timer()
    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)
    private val scanningIntervalMs = 1000L;

    // Whether this processor is already shut down
    private var isShutdown = false

    // Used to calculate latency, running in the same thread, no sync needed.
    private var numRuns = 0
    private var totalFrameMs = 0L
    private var maxFrameMs = 0L
    private var minFrameMs = Long.MAX_VALUE
    private var totalDetectorMs = 0L
    private var maxDetectorMs = 0L
    private var minDetectorMs = Long.MAX_VALUE

    // Frame count that have been processed so far in an interval to calculate FPS.
    private var frameProcessedInInterval = 0
    private var framesPerInterval = 0

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private var latestImage: ByteBuffer? = null

    @GuardedBy("this")
    private var latestImageMetaData: FrameMetadata? = null

    // To keep the images and metadata in process.
    @GuardedBy("this")
    private var processingImage: ByteBuffer? = null

    @GuardedBy("this")
    private var processingMetaData: FrameMetadata? = null

    init {
        fpsTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    framesPerInterval = frameProcessedInInterval
                    frameProcessedInInterval = 0
                }
            },
            0,
            this.scanningIntervalMs
        )
    }

    // -----------------Code for processing single still image----------------------------------------
    override fun processBitmap(bitmap: Bitmap?) {
        val frameStartMs = SystemClock.elapsedRealtime()

        requestDetectInImage(
            InputImage.fromBitmap(bitmap!!, 0),
            frameStartMs
        )
    }

    // -----------------Code for processing frame from Camera1 API-----------------------
    @Synchronized
    override fun processByteBuffer(
        data: ByteBuffer?,
        frameMetadata: FrameMetadata?,
    ) {
        latestImage = data
        latestImageMetaData = frameMetadata
        if (processingImage == null && processingMetaData == null) {
            processLatestImage()
        }
    }

    @Synchronized
    private fun processLatestImage() {
        processingImage = latestImage
        processingMetaData = latestImageMetaData
        latestImage = null
        latestImageMetaData = null
        if (processingImage != null && processingMetaData != null && !isShutdown) {
            processImage(processingImage!!, processingMetaData!!)
        }
    }

    private fun processImage(
        data: ByteBuffer,
        frameMetadata: FrameMetadata,
    ) {
        val frameStartMs = SystemClock.elapsedRealtime()

        requestDetectInImage(
            InputImage.fromByteBuffer(
                data,
                frameMetadata.width,
                frameMetadata.height,
                frameMetadata.rotation,
                InputImage.IMAGE_FORMAT_NV21
            ),
            frameStartMs
        )
            .addOnSuccessListener(executor) { processLatestImage() }
    }

    // -----------------Code for processing live preview frame from CameraX API-----------------------
    @RequiresApi(VERSION_CODES.KITKAT)
    @ExperimentalGetImage
    override fun processImageProxy(image: ImageProxy) {
        val frameStartMs = SystemClock.elapsedRealtime()
        if (isShutdown) {
            return
        }

        requestDetectInImage(
            InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees),
            frameStartMs
        )
            // When the image is from CameraX analysis use case, must call image.close() on received
            // images when finished using them. Otherwise, new images may not be received or the camera
            // may stall.
            .addOnCompleteListener { image.close() }
    }

    // -----------------Common processing logic-------------------------------------------------------
    private fun requestDetectInImage(
        image: InputImage,
        frameStartMs: Long
    ): Task<T> {
        return setUpListener(
            detectInImage(image),
            frameStartMs
        )
    }

    private fun requestDetectInImage(
        image: MlImage,
        shouldShowFps: Boolean,
        frameStartMs: Long
    ): Task<T> {
        return setUpListener(
            detectInImage(image),
            frameStartMs
        )
    }

    private fun setUpListener(
        task: Task<T>,
        frameStartMs: Long
    ): Task<T> {
        val detectorStartMs = SystemClock.elapsedRealtime()
        return task
            .addOnSuccessListener(
                executor,
                OnSuccessListener { results: T ->
                    val endMs = SystemClock.elapsedRealtime()
                    val currentFrameLatencyMs = endMs - frameStartMs
                    val currentDetectorLatencyMs = endMs - detectorStartMs
                    if (numRuns >= 500) {
                        resetLatencyStats()
                    }
                    numRuns++
                    frameProcessedInInterval++
                    totalFrameMs += currentFrameLatencyMs
                    maxFrameMs = max(currentFrameLatencyMs, maxFrameMs)
                    minFrameMs = min(currentFrameLatencyMs, minFrameMs)
                    totalDetectorMs += currentDetectorLatencyMs
                    maxDetectorMs = max(currentDetectorLatencyMs, maxDetectorMs)
                    minDetectorMs = min(currentDetectorLatencyMs, minDetectorMs)

                    // Only log inference info once per second. When frameProcessedInInterval is
                    // equal to 1, it means this is the first frame processed during the current interval.
                    if (frameProcessedInInterval == 1) {
                        Log.d(TAG, "Num of Runs: $numRuns")
                        Log.d(
                            TAG,
                            "Frame latency: max=" +
                                    maxFrameMs +
                                    ", min=" +
                                    minFrameMs +
                                    ", avg=" +
                                    totalFrameMs / numRuns
                        )
                        Log.d(
                            TAG,
                            "Detector latency: max=" +
                                    maxDetectorMs +
                                    ", min=" +
                                    minDetectorMs +
                                    ", avg=" +
                                    totalDetectorMs / numRuns
                        )
                        val mi = ActivityManager.MemoryInfo()
                        activityManager.getMemoryInfo(mi)
                        val availableMegs: Long = mi.availMem / 0x100000L
                        Log.d(TAG, "Memory available in system: $availableMegs MB")
                    }
                    this@VisionProcessorBase.onSuccess(results)
                }
            )
            .addOnFailureListener(
                executor,
                OnFailureListener { e: Exception ->
                    val error = "Failed to process. Error: " + e.localizedMessage
//                    Toast.makeText(
//                        context,
//                        """
//          $error
//          Cause: ${e.cause}
//          """.trimIndent(),
//                        Toast.LENGTH_SHORT
//                    )
//                        .show()
                    Log.d(TAG, error)
                    e.printStackTrace()
                    this@VisionProcessorBase.onFailure(e)
                }
            )
    }

    override fun stop() {
        executor.shutdown()
        isShutdown = true
        resetLatencyStats()
        fpsTimer.cancel()
    }

    private fun resetLatencyStats() {
        numRuns = 0
        totalFrameMs = 0
        maxFrameMs = 0
        minFrameMs = Long.MAX_VALUE
        totalDetectorMs = 0
        maxDetectorMs = 0
        minDetectorMs = Long.MAX_VALUE
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>

    protected open fun detectInImage(image: MlImage): Task<T> {
        return Tasks.forException(
            MlKitException(
                "MlImage is currently not demonstrated for this feature",
                MlKitException.INVALID_ARGUMENT
            )
        )
    }

    protected abstract fun onSuccess(results: T)

    protected abstract fun onFailure(e: Exception)

}
