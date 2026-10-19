package dev.robotvision.data.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import dev.robotvision.domain.Box
import dev.robotvision.domain.Detection
import java.io.File

class MediaPipeObjectDetector(context: Context, file: File) : VisionDetector {
    override val backend = "MediaPipe Tasks 0.10.32 / CPU"
    private val detector = ObjectDetector.createFromOptions(context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(file.absolutePath).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.IMAGE).setScoreThreshold(.3f).setMaxResults(25).build())
    override fun detect(bitmap: Bitmap): List<Detection> {
        val image = BitmapImageBuilder(bitmap).build()
        try {
            return detector.detect(image).detections().mapNotNull { d ->
                val c = d.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val box = d.boundingBox()
                Detection(c.categoryName(), c.score(), Box(
                    (box.left / bitmap.width).coerceIn(0f,1f), (box.top / bitmap.height).coerceIn(0f,1f),
                    (box.right / bitmap.width).coerceIn(0f,1f), (box.bottom / bitmap.height).coerceIn(0f,1f)))
            }
        } finally { image.close() }
    }
    override fun close() = detector.close()
}
