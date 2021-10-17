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
import android.os.Build.VERSION_CODES
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.common.base.Preconditions
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase.DetectorMode
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import nl.pinxoft.orderpiqr.CameraSource.SizePair

/**
 * Utility class to retrieve shared preferences.
 */
object PreferenceUtils {
    fun saveString(context: Context, @StringRes prefKeyId: Int, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(prefKeyId), value)
            .apply()
    }

    fun getCameraPreviewSizePair(context: Context, cameraId: Int): SizePair? {
        Preconditions.checkArgument(
            cameraId == CameraSource.CAMERA_FACING_BACK
                    || cameraId == CameraSource.CAMERA_FACING_FRONT
        )
        val previewSizePrefKey: String
        val pictureSizePrefKey: String
        if (cameraId == CameraSource.CAMERA_FACING_BACK) {
            previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size)
        } else {
            previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size)
            pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size)
        }
        return try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            SizePair(
                com.google.android.gms.common.images.Size.parseSize(
                    sharedPreferences.getString(previewSizePrefKey, "352x288")!!
                ),
                com.google.android.gms.common.images.Size.parseSize(
                    sharedPreferences.getString(pictureSizePrefKey, "352x288")!!
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    @RequiresApi(VERSION_CODES.LOLLIPOP)
    fun getCameraXTargetResolution(context: Context, lensfacing: Int): Size? {
        Preconditions.checkArgument(
            lensfacing == CameraSelector.LENS_FACING_BACK
                    || lensfacing == CameraSelector.LENS_FACING_FRONT
        )
        val prefKey =
            if (lensfacing == CameraSelector.LENS_FACING_BACK) context.getString(R.string.pref_key_camerax_rear_camera_target_resolution) else context.getString(
                R.string.pref_key_camerax_front_camera_target_resolution
            )
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            Size.parseSize(sharedPreferences.getString(prefKey, null))
        } catch (e: Exception) {
            null
        }
    }

    fun shouldHideDetectionInfo(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_info_hide)
        return sharedPreferences?.getBoolean(prefKey, false) ?: false
    }

    fun getObjectDetectorOptionsForStillImage(context: Context): ObjectDetectorOptions {
        return getObjectDetectorOptions(
            context,
            R.string.pref_key_still_image_object_detector_enable_multiple_objects,
            R.string.pref_key_still_image_object_detector_enable_classification,
            ObjectDetectorOptions.SINGLE_IMAGE_MODE
        )
    }

    fun getObjectDetectorOptionsForLivePreview(context: Context): ObjectDetectorOptions {
        return getObjectDetectorOptions(
            context,
            R.string.pref_key_live_preview_object_detector_enable_multiple_objects,
            R.string.pref_key_live_preview_object_detector_enable_classification,
            ObjectDetectorOptions.STREAM_MODE
        )
    }

    private fun getObjectDetectorOptions(
        context: Context,
        @StringRes prefKeyForMultipleObjects: Int,
        @StringRes prefKeyForClassification: Int,
        @DetectorMode mode: Int
    ): ObjectDetectorOptions {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enableMultipleObjects =
            sharedPreferences.getBoolean(context.getString(prefKeyForMultipleObjects), false)
        val enableClassification =
            sharedPreferences.getBoolean(context.getString(prefKeyForClassification), true)
        val builder = ObjectDetectorOptions.Builder().setDetectorMode(mode)
        if (enableMultipleObjects) {
            builder.enableMultipleObjects()
        }
        if (enableClassification) {
            builder.enableClassification()
        }
        return builder.build()
    }

//    /**
//     * Mode type preference is backed by [android.preference.ListPreference] which only support
//     * storing its entry value as string type, so we need to retrieve as string and then convert to
//     * integer.
//     */
//    private fun getModeTypePreferenceValue(
//        context: Context, @StringRes prefKeyResId: Int, defaultValue: Int
//    ): Int {
//        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
//        val prefKey = context.getString(prefKeyResId)
//        return sharedPreferences.getString(prefKey, defaultValue.toString())!!.toInt()
//    }

    fun isCameraLiveViewportEnabled(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_camera_live_viewport)
        return sharedPreferences?.getBoolean(prefKey, false) ?: false
    }
}