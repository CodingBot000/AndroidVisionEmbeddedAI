package dev.robotvision.data.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.robotvision.data.vision.VisionController
import dev.robotvision.domain.withOwnedFrame
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/** Owns each ImageProxy synchronously on one analyzer executor; every path closes it once. */
class CameraSession(private val context: Context, private val controller: VisionController) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "RobotVision-Camera") }
    private val closed = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    fun bind(owner: LifecycleOwner, view: PreviewView, onStatus: (String) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed.get()) return@addListener
            try {
                val cameraProvider = future.get(); provider = cameraProvider
                val displayRotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
                val previewUseCase = Preview.Builder().setTargetRotation(displayRotation).build()
                val analyzerUseCase = ImageAnalysis.Builder()
                    .setTargetRotation(displayRotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                preview = previewUseCase; analysis = analyzerUseCase
                previewUseCase.setSurfaceProvider(view.surfaceProvider)
                analyzerUseCase.setAnalyzer(executor) { image -> withOwnedFrame(image::close) {
                    var upright: Bitmap? = null
                    var cropped: Bitmap? = null
                    var original: Bitmap? = null
                    try {
                        if (!closed.get() && controller.state.value.ready) {
                            val captured = android.os.SystemClock.elapsedRealtime()
                            val start = System.nanoTime()
                            original = image.toBitmap()
                            val crop = image.cropRect
                            cropped = Bitmap.createBitmap(original!!, crop.left, crop.top, crop.width(), crop.height())
                            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                            upright = Bitmap.createBitmap(cropped!!, 0, 0, cropped!!.width, cropped!!.height, matrix, true)
                            runBlocking { controller.process(upright!!, captured, start) }
                        } else controller.skipped()
                    } catch (e: Exception) {
                        ContextCompat.getMainExecutor(context).execute { onStatus("카메라 분석 오류: ${e.message}") }
                    } finally {
                        listOfNotNull(upright, cropped, original).distinct().forEach { it.recycle() }
                    }
                } }
                val group = UseCaseGroup.Builder().addUseCase(previewUseCase).addUseCase(analyzerUseCase)
                view.viewPort?.let(group::setViewPort)
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                onStatus("ACTIVE")
            } catch (e: Exception) { onStatus("카메라를 열 수 없습니다: ${e.message}") }
        }, ContextCompat.getMainExecutor(context))
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        analysis?.clearAnalyzer()
        val useCases = listOfNotNull(preview, analysis).toTypedArray()
        if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        executor.shutdown() // An in-flight frame finishes and closes before executor terminates.
    }
}
