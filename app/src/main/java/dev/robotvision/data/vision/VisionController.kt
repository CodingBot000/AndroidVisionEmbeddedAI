package dev.robotvision.data.vision

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.robotvision.data.modelassets.ModelAssetManager
import dev.robotvision.domain.*
import dev.robotvision.domain.Target
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

data class EngineState(val loading: Boolean = false, val ready: Boolean = false, val backend: String = "--", val reason: String? = null, val error: String? = null)
class VisionController @Inject constructor(
    @param:ApplicationContext private val context: Context, private val assets: ModelAssetManager, private val clock: Clock,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "RobotVision-Inference") }.asCoroutineDispatcher()
    private var detector: VisionDetector? = null
    private val _state = MutableStateFlow(EngineState())
    val state = _state.asStateFlow()
    val performance = PerformanceMetrics()
    private val _metrics = MutableStateFlow(Metrics())
    val metrics = _metrics.asStateFlow()
    private val _frames = MutableSharedFlow<DetectionFrame>()
    val frames = _frames.asSharedFlow()
    suspend fun load() = withContext(dispatcher) {
        if (detector != null) return@withContext
        _state.value = EngineState(loading = true)
        val start = System.nanoTime()
        try {
            val file = assets.requireFile("efficientdet_lite0.tflite")
            val labels = assets.requireFile("labels.txt").readLines()
            var fallback: String? = null
            detector = try { LiteRtObjectDetector(file, labels) } catch (e: Exception) {
                fallback = "LiteRT CPU initialization/warm-up failed: ${e.javaClass.simpleName}: ${e.message}"
                MediaPipeObjectDetector(context, file)
            } catch (e: LinkageError) {
                fallback = "LiteRT JNI unavailable: ${e.message}"
                MediaPipeObjectDetector(context, file)
            }
            performance.loaded((System.nanoTime()-start)/1e6); _metrics.value = performance.value
            _state.value = EngineState(ready = true, backend = detector!!.backend, reason = fallback)
        } catch (e: Exception) { _state.value = EngineState(error = "MODEL_UNAVAILABLE: ${e.message}") }
        catch (e: LinkageError) {
            android.util.Log.e("RobotVision", "Vision native initialization failed", e)
            _state.value = EngineState(error = "Vision native runtime unavailable: ${e.javaClass.simpleName}: ${e.message ?: e.cause?.toString() ?: "see Logcat"}")
        }
    }
    suspend fun process(bitmap: Bitmap, capturedAt: Long, startNanos: Long) = withContext(dispatcher) {
        val engine = detector ?: return@withContext
        try {
            val start = System.nanoTime()
            val detections = engine.detect(bitmap)
            val end = System.nanoTime()
            val frame = DetectionFrame(capturedAt, bitmap.width, bitmap.height, detections, engine.backend,
                (end-start)/1e6, (end-startNanos)/1e6)
            _metrics.value = performance.record(frame)
            _frames.emit(frame)
        } catch (e: Exception) { _state.value = _state.value.copy(ready = false, error = "Vision inference failed: ${e.message}") }
    }
    fun speechMeasured(ms: Double) { performance.speech(ms); _metrics.value = performance.value }
    fun skipped() { performance.skipped(); _metrics.value = performance.value }
    suspend fun unload() = withContext(dispatcher) {
        try { detector?.close() } finally { detector = null; _state.value = EngineState() }
    }
    suspend fun close() { try { unload() } finally { dispatcher.close() } }
}
