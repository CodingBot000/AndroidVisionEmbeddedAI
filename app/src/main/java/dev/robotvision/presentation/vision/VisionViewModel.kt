package dev.robotvision.presentation.vision

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.robotvision.data.modelassets.ModelAssetManager
import dev.robotvision.data.vision.VisionController
import dev.robotvision.debug.DebugScenario
import dev.robotvision.di.IoDispatcher
import dev.robotvision.domain.*
import dev.robotvision.domain.Target
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@HiltViewModel class VisionViewModel @Inject constructor(
    val controller: VisionController, private val speech: SpeechRecognizerEngine, private val audio: AudioInput,
    private val repository: EventRepository, private val preferences: SettingsRepository,
    private val clock: Clock, private val saved: SavedStateHandle, private val assets: ModelAssetManager,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(VisionUiState(search = SearchState(target = Targets.byLabel(saved["target"]))))
    val state = _state.asStateFlow()
    private val _overlay = MutableStateFlow(OverlayState())
    val overlay = _overlay.asStateFlow()
    val metrics = controller.metrics
    val history = repository.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val effects = Channel<VisionEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()
    private val intents = Channel<VisionIntent>(Channel.UNLIMITED)
    private val parser = ParseVoiceCommand()
    private val stabilization = StabilizeDetection()
    private val cooldown = EventCooldown()
    private var timeout: Job? = null
    private var speechJob: Job? = null
    private var simulationJob: Job? = null
    private var loadingJob: Job? = null
    private var foreground = true
    private var generation = 0
    init {
        viewModelScope.launch { intents.consumeAsFlow().collect { intent ->
            try { handle(intent) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = "작업 실패: ${e.message}"
                _state.update { it.copy(message = message) }
                effects.trySend(VisionEffect.Snackbar(message))
            }
        } }
        viewModelScope.launch { controller.state.collect { engine ->
            _state.update { it.copy(engine = engine) }
            engine.error?.let { message ->
                _overlay.value = OverlayState()
                stopSearch()
                save("MODEL_ERROR", detail = message)
            }
        } }
        viewModelScope.launch { controller.frames.collect { frame -> if (!_state.value.simulation && foreground) onFrame(frame) } }
        viewModelScope.launch { preferences.settings.collect { settings ->
            if (_state.value.settings.confidence != settings.confidence) stabilization.reset()
            _state.update { it.copy(settings = settings) }
        } }
        viewModelScope.launch {
            while (isActive) {
                delay(200)
                val frame = _overlay.value.frame
                if (frame != null && clock.now() - frame.timestamp > 800) _overlay.value = OverlayState()
            }
        }
        viewModelScope.launch(io) {
            runCatching { assets.description() to assets.hashSummary() }
                .onSuccess { (description, hashes) -> _state.update { it.copy(modelDescription = description, hashes = hashes) } }
                .onFailure { e -> _state.update { it.copy(modelDescription = "Model manifest unavailable: ${e.message}") } }
        }
        loadEngine()
    }
    fun dispatch(intent: VisionIntent) { intents.trySend(intent) }
    private fun loadEngine() {
        if (loadingJob?.isActive == true || _state.value.simulation || !foreground) return
        loadingJob = viewModelScope.launch { controller.load() }
    }
    private suspend fun handle(intent: VisionIntent) {
        when (intent) {
            is VisionIntent.SelectTarget -> Targets.byLabel(intent.label)?.let(::startSearch)
            VisionIntent.StopSearch -> stopSearch()
            VisionIntent.RetrySearch -> _state.value.search.target?.let(::startSearch)
            VisionIntent.ClearTarget -> { stopSearch(); saved["target"] = null; _state.update { it.copy(search = SearchState()) } }
            VisionIntent.StartListening -> startListening()
            VisionIntent.StopListening -> {
                if (_state.value.listening == ListeningStatus.PREPARING) cancelListening()
                else if (_state.value.listening == ListeningStatus.RECORDING) {
                    _state.update { it.copy(listening = ListeningStatus.TRANSCRIBING) }; audio.stop()
                }
            }
            VisionIntent.CancelListening -> cancelListening()
            VisionIntent.ReloadEngine -> { controller.unload(); loadEngine() }
            VisionIntent.Background -> {
                foreground = false; generation++; cancelListening(); stopSearch(); _overlay.value = OverlayState()
                simulationJob?.cancel(); _state.update { it.copy(cameraStatus = "IDLE") }
                // Unload runs after any in-flight native inference on its owning thread.
                controller.unload()
            }
            VisionIntent.Foreground -> { foreground = true; loadEngine(); if (_state.value.simulation) startSimulation() }
            is VisionIntent.Permissions -> {
                _state.update { it.copy(cameraPermission = intent.camera, microphonePermission = intent.microphone) }
                if (!intent.microphone) cancelListening()
                if (!intent.camera && !_state.value.simulation) { stopSearch(); _overlay.value = OverlayState() }
            }
            is VisionIntent.CameraStatus -> _state.update { it.copy(cameraStatus = intent.value) }
            is VisionIntent.SetConfidence -> preferences.setConfidence(intent.value)
            is VisionIntent.SetHaptic -> preferences.setHaptic(intent.value)
            VisionIntent.ClearHistory -> repository.clear()
            VisionIntent.ToggleSimulation -> if (DebugScenario.available) {
                cancelListening(); stopSearch(); simulationJob?.cancel(); _overlay.value = OverlayState()
                val enabled = !_state.value.simulation
                _state.update { it.copy(simulation = enabled, transcript = "", message = null) }
                if (enabled) { controller.unload(); startSimulation() } else loadEngine()
            }
        }
    }
    private fun startSearch(target: Target) {
        generation++; timeout?.cancel(); stabilization.reset(); saved["target"] = target.label
        _state.update { it.copy(search = VisionReducer.reduce(it.search, SearchAction.Start(target, clock.now())), message = null) }
        val searchGeneration = generation
        timeout = viewModelScope.launch {
            delay(20_000)
            if (generation == searchGeneration && _state.value.search.status == SearchStatus.SEARCHING) {
                _state.update { it.copy(search = VisionReducer.reduce(it.search, SearchAction.Timeout)) }
                save("NOT_FOUND", target.label, detail = "20초 안에 안정화된 결과 없음")
            }
        }
    }
    private fun stopSearch() {
        generation++; timeout?.cancel(); stabilization.reset()
        _state.update { it.copy(search = VisionReducer.reduce(it.search, SearchAction.Stop)) }
    }
    private fun onFrame(frame: DetectionFrame) {
        _overlay.value = OverlayState(frame.copy(detections = frame.detections.filter { it.score >= _state.value.settings.confidence }))
        val state = _state.value
        if (state.search.status != SearchStatus.SEARCHING || frame.timestamp < state.search.startedAt) return
        val target = state.search.target ?: return
        val hit = stabilization.accept(frame, target, state.settings.confidence) ?: return
        _state.update { it.copy(search = VisionReducer.reduce(it.search, SearchAction.Found)) }
        timeout?.cancel()
        if (cooldown.allow("${target.label}:FOUND:${frame.simulated}", clock.now())) {
            save("FOUND", target.label, hit.score, frame.backend, frame.inferenceMs, simulated = frame.simulated)
            effects.trySend(VisionEffect.TargetFound)
        }
    }
    private fun cancelListening() {
        audio.cancel(); speechJob?.cancel(); speechJob = null
        _state.update { it.copy(listening = ListeningStatus.IDLE) }
    }
    private fun startListening() {
        if (_state.value.simulation) {
            _state.update { it.copy(transcript = DebugScenario.transcript) }
            applyCommand(parser.parse(DebugScenario.transcript)); return
        }
        if (!_state.value.microphonePermission || speechJob?.isActive == true) return
        _state.update { it.copy(listening = ListeningStatus.PREPARING, message = null) }
        speechJob = viewModelScope.launch {
            var samples: FloatArray? = null
            try {
                speech.load(); _state.update { it.copy(speechReady = true) }
                samples = audio.capture { _state.update { it.copy(listening = ListeningStatus.RECORDING) } }
                ensureActive()
                _state.update { it.copy(listening = ListeningStatus.TRANSCRIBING) }
                val start = System.nanoTime()
                val text = speech.transcribe(samples)
                ensureActive()
                controller.speechMeasured((System.nanoTime() - start) / 1e6)
                _state.update { it.copy(transcript = text, listening = ListeningStatus.IDLE) }
                if (text.isBlank()) error("음성 명령을 인식하지 못했습니다. 대상을 직접 선택할 수 있습니다.")
                applyCommand(parser.parse(text))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { speechError(e.message ?: "음성 처리 오류") }
            catch (e: LinkageError) { speechError("sherpa-onnx JNI 로딩 실패: ${e.message}") }
            finally { audio.cancel(); samples?.fill(0f); _state.update { it.copy(listening = ListeningStatus.IDLE) } }
        }
    }
    private fun speechError(message: String) {
        _state.update { it.copy(message = message, listening = ListeningStatus.IDLE) }
        effects.trySend(VisionEffect.Snackbar(message))
    }
    private fun applyCommand(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.Find -> startSearch(command.target)
            VoiceCommand.Stop -> stopSearch()
            VoiceCommand.Retry -> _state.value.search.target?.let(::startSearch)
            VoiceCommand.Clear -> { stopSearch(); _state.update { it.copy(search = SearchState()) }; saved["target"] = null }
            is VoiceCommand.Unsupported -> {
                val message = "지원하지 않는 대상입니다. 컵·물병·사람·의자·노트북·키보드·책·휴대폰을 선택하세요."
                _state.update { it.copy(message = message) }; save("COMMAND_UNSUPPORTED", detail = "지원 대상 외 명령")
            }
            is VoiceCommand.Unknown -> _state.update { it.copy(message = "명령을 이해하지 못했습니다. ‘컵 찾아줘’처럼 말해 주세요.") }
        }
    }
    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive) { delay(400); DebugScenario.frame(clock.now())?.let(::onFrame) }
        }
    }
    private fun save(type: String, label: String? = null, score: Float? = null, backend: String = _state.value.engine.backend,
                     latency: Double? = null, detail: String = "", simulated: Boolean = _state.value.simulation) {
        viewModelScope.launch {
            runCatching { repository.save(Event(time = clock.wallTime(), type = type, label = label, score = score,
                backend = backend, latencyMs = latency, detail = detail, simulated = simulated)) }
                .onFailure { error -> _state.update { it.copy(message = "이벤트 저장 실패: ${error.message}") } }
        }
    }
    override fun onCleared() {
        audio.cancel()
        CoroutineScope(SupervisorJob() + io).launch {
            runCatching { controller.close() }; runCatching { speech.close() }
        }
        intents.close(); effects.close()
    }
}
