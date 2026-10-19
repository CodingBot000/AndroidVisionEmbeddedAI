package dev.robotvision.data.vision

import android.graphics.Bitmap
import dev.robotvision.domain.Detection

/** Invoked exclusively on VisionController's dedicated worker, including construction and close. */
interface VisionDetector : AutoCloseable {
    val backend: String
    fun detect(bitmap: Bitmap): List<Detection>
}
