package io.github.alessiocpt.powerremapper.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/**
 * Recognises camera apps: those that answer the standard "open the camera" or "take a picture"
 * intents. While one is in front it holds the camera, and Android refuses another app's torch
 * request, so the Flashlight action cannot work in a camera app's Profile.
 */
object CameraApps {

    private val CAMERA_ACTIONS = listOf(
        MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
        MediaStore.ACTION_IMAGE_CAPTURE
    )

    fun isCameraApp(context: Context, packageName: String): Boolean =
        CAMERA_ACTIONS.any { action ->
            context.packageManager.queryIntentActivities(
                Intent(action).setPackage(packageName),
                PackageManager.ResolveInfoFlags.of(0)
            ).isNotEmpty()
        }
}
