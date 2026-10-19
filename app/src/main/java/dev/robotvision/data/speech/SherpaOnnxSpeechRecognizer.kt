package dev.robotvision.data.speech

import com.k2fsa.sherpa.onnx.*
import dev.robotvision.data.modelassets.ModelAssetManager
import dev.robotvision.domain.SpeechRecognizerEngine
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class SherpaOnnxSpeechRecognizer @Inject constructor(private val assets: ModelAssetManager) : SpeechRecognizerEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "RobotVision-Speech") }.asCoroutineDispatcher()
    private var recognizer: OfflineRecognizer? = null
    override suspend fun load() = withContext(dispatcher) { loadConfined() }
    private fun loadConfined() {
        if (recognizer != null) return
        val encoder = assets.requireFile("encoder_model.ort")
        val decoder = assets.requireFile("decoder_model_merged.ort")
        val tokens = assets.requireFile("tokens.txt")
        recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000),
            modelConfig = OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(encoder = encoder.absolutePath, mergedDecoder = decoder.absolutePath),
                tokens = tokens.absolutePath, numThreads = 2, provider = "cpu",
            ), decodingMethod = "greedy_search",
        ))
    }
    override suspend fun transcribe(samples: FloatArray): String = withContext(dispatcher) {
        require(samples.size >= 3200) { "음성이 너무 짧습니다. 0.2초 이상 말해 주세요." }
        require(samples.any { kotlin.math.abs(it) > .003f }) { "음성이 감지되지 않았습니다." }
        loadConfined()
        val engine = checkNotNull(recognizer)
        val stream = engine.createStream()
        try { stream.acceptWaveform(samples, 16000); engine.decode(stream); engine.getResult(stream).text.trim() }
        finally { stream.release() }
    }
    override suspend fun close() {
        try { withContext(dispatcher) { try { recognizer?.release() } finally { recognizer = null } } }
        finally { dispatcher.close() }
    }
}
